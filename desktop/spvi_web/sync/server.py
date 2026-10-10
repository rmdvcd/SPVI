"""Servidor principal TCP v1, independiente del servidor web de administración."""
import base64
import ipaddress
import json
import os
import socket
import socketserver
import threading
from .protocol import (Channel,ProtocolError,read_greeting,greeting,new_key,public_spki,employee_key,
                       link_mac,verify_mac,session_keys,rejection_mac,qr_encode)
from ..business import meta, objects
from .. import dto


def enc(data): return base64.b64encode(data).decode('ascii')


def dec(value):
    if not isinstance(value,str) or len(value)>4096: raise ProtocolError('Formato no válido.')
    return base64.b64decode(value,validate=True)


def local_address(host):
    try: address=ipaddress.ip_address(host)
    except ValueError: raise ValueError('Usa la dirección IP de esta PC en la red local.') from None
    if not address.is_private or address.is_loopback or address.is_unspecified or address.is_multicast or address.version!=4:
        raise ValueError('Elige una dirección IPv4 privada de la LAN, no localhost ni 0.0.0.0.')
    return str(address)


class PrincipalServer(socketserver.ThreadingMixIn,socketserver.TCPServer):
    allow_reuse_address=True
    daemon_threads=True
    block_on_close=False
    request_queue_size=16

    def verify_request(self,request,client_address):
        address=ipaddress.ip_address(client_address[0])
        return address.is_private and not address.is_multicast and not address.is_unspecified

    def __init__(self,address,manager):
        self.manager=manager
        self.capacity=threading.BoundedSemaphore(24)
        super().__init__(address,Handler)

    def process_request(self,request,address):
        if not self.capacity.acquire(blocking=False):
            self.shutdown_request(request);return
        try: super().process_request(request,address)
        except Exception:
            self.capacity.release();raise

    def process_request_thread(self,request,address):
        try: super().process_request_thread(request,address)
        finally: self.capacity.release()

    def handle_error(self,request,address):
        # Nunca escribir mensajes, direcciones, claves o datos del negocio en el log.
        return


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        manager=self.server.manager; employee=None
        self.request.settimeout(90);self.request.setsockopt(socket.IPPROTO_TCP,socket.TCP_NODELAY,1)
        try:
            message=read_greeting(self.rfile)
            response,key,employee=manager.hello(message)
            self.wfile.write(greeting(response));self.wfile.flush()
            if key is None: return
            if response['t']=='vincular_ok':
                manager.disconnect(employee);return
            channel=Channel(session_keys(key,dec(message['nonce']),dec(response['nonce'])),principal=True)
            authenticated=False
            while manager.running:
                request=channel.receive(self.rfile)
                if not authenticated:
                    manager.register(employee,self.request);authenticated=True
                with manager.business.connect() as db:
                    e=manager.business.employee(db,employee)
                    if not e['active']:
                        self.wfile.write(channel.encode({'t':'quitada'}));self.wfile.flush();return
                tag=request['t']
                if tag=='sync': result=manager.business.synchronize(employee,request)
                elif tag=='cmd': result=manager.business.command(employee,request)
                elif tag=='ping': result=dict(t='pong',id=dto.number(request.get('id')))
                elif tag=='apk_pedir':
                    result=dict(t='apk_bloque',id=dto.number(request.get('id')),desde=request.get('desde',0),datos='',total=0,error='no_disponible')
                else: raise ProtocolError('Mensaje no válido.')
                self.wfile.write(channel.encode(result));self.wfile.flush()
        except (EOFError,OSError):
            return
        except Exception as error:
            manager.record_error('datos' if isinstance(error,(ValueError,ProtocolError,KeyError,TypeError)) else 'interno')
            # Una transacción fallida nunca se confirma: Android conserva el lote para reintentar.
            return
        finally:
            if employee is not None: manager.unregister(employee,self.request)


class SyncManager:
    def __init__(self,business):
        self.business=business
        self.server=None; self.thread=None; self.sessions={}; self.lock=threading.RLock(); self.running=False
        self.errors=0;self.last_error=None

    def record_error(self,category):
        with self.lock:
            self.errors+=1;self.last_error=dict(category=category,at=dto.now_ms())

    def start(self,host,port=47811):
        host=local_address(host);dto.number(port,1,65535)
        self.business.license.require_active()
        with self.lock:
            if self.running:
                if (host,port)==self.server.server_address: return self.status()
                raise ValueError('Detén la red antes de cambiar la dirección.')
            try: self.server=PrincipalServer((host,port),self)
            except OSError as error: raise ValueError('No se pudo abrir esa dirección o puerto en esta PC.') from error
            self.running=True
            self.thread=threading.Thread(target=self.server.serve_forever,name='SPVI principal LAN',daemon=True)
            self.thread.start()
        return self.status()

    def stop(self):
        with self.lock:
            self.running=False;server=self.server;self.server=None;sessions=list(self.sessions.values());self.sessions.clear()
        for connection in sessions:
            try: connection.shutdown(socket.SHUT_RDWR);connection.close()
            except OSError: pass
        if server: server.shutdown();server.server_close()
        if self.thread: self.thread.join(timeout=3)

    def status(self):
        with self.lock:
            return dict(role='PRINCIPAL',running=self.running,address=self.server.server_address if self.server else None,
                        connected=sorted(self.sessions),errors=self.errors,last_error=self.last_error)

    def register(self,id,connection):
        with self.lock:
            previous=self.sessions.get(id);self.sessions[id]=connection
        if previous is not None and previous is not connection:
            try: previous.shutdown(socket.SHUT_RDWR);previous.close()
            except OSError: pass

    def unregister(self,id,connection):
        with self.lock:
            if self.sessions.get(id) is connection: self.sessions.pop(id,None)

    def disconnect(self,id):
        with self.lock: connection=self.sessions.pop(id,None)
        if connection:
            try: connection.shutdown(socket.SHUT_RDWR);connection.close()
            except OSError: pass

    def qr(self,id):
        self.business.license.require_active()
        with self.lock:
            if not self.running: raise ValueError('Inicia primero la red local.')
            host,port=self.server.server_address
        with self.business.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            e=self.business.employee(db,id)
            if not e['active'] or not self.business.seat_allowed(db,id): raise ValueError('Empleado fuera del límite autorizado.')
            if any(t.get('empleadoId')==id and t.get('cerradoEn') is None for t in objects(db,'turnos')):
                raise ValueError('Cierra y sincroniza el turno antes de volver a vincular.')
            token=os.urandom(16);expires=dto.now_ms()+10*60*1000
            db.execute('UPDATE employees SET token=?,expires=? WHERE id=?',(self.business.box.seal(token),expires,id))
            return qr_encode(meta(db,'business_id'),meta(db,'name'),[host],port,id,e['name'],token,expires)

    def hello(self,message):
        reject=lambda reason:(dict(t='rechazo',motivo=reason),None,None)
        if message.get('v',1)!=1: return reject('version')
        id=message.get('empleado')
        if type(id) is not int or id<=0: return reject('desconocida')
        with self.business.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            if message.get('negocio')!=meta(db,'business_id'): return reject('otro_negocio')
            try: e=self.business.employee(db,id)
            except ValueError: return reject('desconocida')
            if message['t']=='vincular':
                if not e['active'] or not e['token']: return reject('codigo_invalido')
                if not self.business.seat_allowed(db,id): return reject('limite')
                if dto.now_ms()>=(e['expires'] or 0): return reject('codigo_vencido')
                token=self.business.box.open(e['token']);peer=dec(message.get('pub'));mac=dec(message.get('mac'))
                verify_mac(link_mac(token,peer),mac)
                private=new_key();public=public_spki(private);key=employee_key(private,peer,token)
                db.execute('UPDATE employees SET secret=?,token=NULL,expires=NULL,linked=? WHERE id=?',
                           (self.business.box.seal(key),dto.now_ms(),id))
                return dict(t='vincular_ok',pub=enc(public),mac=enc(link_mac(token,peer,public)),nombreNegocio=meta(db,'name')),key,id
            if message['t']!='hola' or e['secret'] is None: return reject('desconocida')
            nonce=dec(message.get('nonce'))
            if len(nonce)!=16: return reject('desconocida')
            key=self.business.box.open(e['secret'])
            if not e['active']:
                return dict(t='rechazo',motivo='quitada',mac=enc(rejection_mac(key,nonce,'quitada'))),None,None
            return dict(t='hola_ok',nonce=enc(os.urandom(16)),comandosUnicos=True),key,id
