"""Protección de secretos de esta principal; DPAPI del usuario en Windows."""
import ctypes
import hashlib
import os
from ctypes import wintypes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class SecretBox:
    def __init__(self, secret):
        self.key = hashlib.sha256(('SPVI-desktop-secrets|' + secret).encode()).digest()

    @staticmethod
    def _windows(data, decrypt):
        class Blob(ctypes.Structure):
            _fields_ = [('length', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]
        source = ctypes.create_string_buffer(data)
        incoming = Blob(len(data), ctypes.cast(source, ctypes.POINTER(ctypes.c_ubyte)))
        outgoing = Blob()
        crypt = ctypes.WinDLL('crypt32', use_last_error=True)
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.LocalFree.argtypes = [ctypes.c_void_p]
        kernel.LocalFree.restype = ctypes.c_void_p
        function = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
        function.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                             ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
        function.restype = wintypes.BOOL
        if not function(ctypes.byref(incoming), None, None, None, None, 1, ctypes.byref(outgoing)):
            raise ValueError('No se pueden abrir las claves de esta instalación y usuario de Windows.')
        try:
            return ctypes.string_at(outgoing.data, outgoing.length)
        finally:
            kernel.LocalFree(ctypes.cast(outgoing.data, ctypes.c_void_p))

    def seal(self, data):
        if os.name == 'nt':
            return b'W1' + self._windows(data, False)
        # Modo de desarrollo no Windows. No se considera equivalente a DPAPI.
        nonce = os.urandom(12)
        return b'D1' + nonce + AESGCM(self.key).encrypt(nonce, data, b'SPVI-desktop-v1')

    def open(self, data):
        if data[:2] == b'W1' and os.name == 'nt':
            return self._windows(data[2:], True)
        if data[:2] == b'D1' and os.name != 'nt':
            return AESGCM(self.key).decrypt(data[2:14], data[14:], b'SPVI-desktop-v1')
        raise ValueError('Las claves pertenecen a otra plataforma. Solicita recuperación de licencia.')
