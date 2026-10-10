"""Vectores del formato Android y pruebas negativas. No sustituyen una prueba Android↔PC."""
import base64
import gzip
import json
import struct
import unittest
from io import BytesIO
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from spvi_web.sync.protocol import (Channel,ProtocolError,session_keys,new_key,employee_key,public_spki,
    qr_encode,qr_decode,link_mac,verify_mac,read_frame,read_greeting,greeting,MAX_FRAME)


class ProtocolTest(unittest.TestCase):
    def test_qr_field_names_and_url_encoding(self):
        token=bytes(range(16))
        code=qr_encode('negocio','Café',['192.168.1.10'],12345,1,'Ana',token,1900000000000)
        fields,decoded=qr_decode(code)
        self.assertTrue(code.startswith('SPVI-VINC1:'));self.assertNotIn('=',code)
        self.assertEqual(token,decoded);self.assertEqual('Café',fields['nn'])
        self.assertEqual({'n','nn','h','p','e','en','t','x'},set(fields))

    def test_ecdh_and_authenticated_link(self):
        a,b=new_key(),new_key();token=bytes(range(16))
        self.assertEqual(employee_key(a,public_spki(b),token),employee_key(b,public_spki(a),token))
        c=link_mac(token,public_spki(a));s=link_mac(token,public_spki(a),public_spki(b))
        self.assertNotEqual(c,s)
        verify_mac(c,c)
        with self.assertRaises(ProtocolError): verify_mac(c,s)

    def test_both_directions_and_fresh_session(self):
        keys=session_keys(bytes(32),bytes(16),bytes([1])*16)
        self.assertNotEqual(*keys)
        self.assertNotEqual(keys,session_keys(bytes(32),bytes([2])*16,bytes([1])*16))
        principal,secondary=Channel(keys,True),Channel(keys,False)
        self.assertEqual({'t':'sync','id':1},principal.receive(BytesIO(secondary.encode({'t':'sync','id':1}))))
        self.assertEqual({'t':'sync_ok','id':1},secondary.receive(BytesIO(principal.encode({'t':'sync_ok','id':1}))))

    def test_independent_android_aad_frame_vector(self):
        key=bytes(range(32));iv=bytes(range(12));payload={'t':'sync','id':7}
        # ByteBuffer Android: 8 B "SPVI-S1\\0" + etiqueta 1 + long BE 0.
        aad=bytes.fromhex('535056492d533100010000000000000000')
        cipher=AESGCM(key).encrypt(iv,gzip.compress(json.dumps(payload).encode()),aad)
        encoded=struct.pack('>i',len(iv+cipher))+iv+cipher
        self.assertEqual(payload,Channel((key,bytes(32)),True).receive(BytesIO(encoded)))

    def test_replay_poison_session(self):
        keys=session_keys(bytes(32),bytes(16),bytes(16))
        sender,receiver=Channel(keys,False),Channel(keys,True)
        packet=sender.encode({'t':'sync'})
        receiver.receive(BytesIO(packet))
        with self.assertRaises(ProtocolError): receiver.receive(BytesIO(packet))
        with self.assertRaises(ProtocolError): receiver.receive(BytesIO(sender.encode({'t':'sync'})))

    def test_tampered_cipher(self):
        keys=session_keys(bytes(32),bytes(16),bytes(16))
        packet=Channel(keys,False).encode({'t':'sync'})
        altered=packet[:-1]+bytes([packet[-1]^1])
        with self.assertRaises(ProtocolError): Channel(keys,True).receive(BytesIO(altered))

    def test_bounds_and_truncated_input(self):
        for data in [struct.pack('>i',-1),struct.pack('>i',MAX_FRAME+1),b'\0\0',struct.pack('>i',20)+b'ab']:
            with self.assertRaises(ProtocolError): read_frame(BytesIO(data))
        with self.assertRaises(ProtocolError): read_greeting(BytesIO(struct.pack('>i',65537)))
        self.assertEqual({'t':'hola'},read_greeting(BytesIO(greeting({'t':'hola'}))))

    def test_bad_qr(self):
        for code in ['','http://192.168.1.1','SPVI-VINC1:***','SPVI-VINC1:'+base64.urlsafe_b64encode(b'[]').decode()]:
            with self.assertRaises(ProtocolError): qr_decode(code)
