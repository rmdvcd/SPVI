"""Resultado HTTP y mutación de negocio confirmados en la misma transacción."""
import hashlib
import json
from functools import wraps
from flask import request, jsonify


class RevertirRespuesta(Exception):
    def __init__(self,response): self.response=response


def protect(app,business,view):
    @wraps(view)
    def wrapped(*args,**kwargs):
        key=request.headers.get('Idempotency-Key')
        if not key: return view(*args,**kwargs)
        if not 16<=len(key)<=128 or not key.isascii() or not all(x.isalnum() or x in '-_' for x in key):
            return jsonify(error='Identificador de operación no válido.'),400
        payload=request.get_json(silent=True)
        digest=hashlib.sha256((request.path+'\n'+json.dumps(payload,sort_keys=True,separators=(',',':'))).encode()).hexdigest()
        try:
            with business.request_transaction() as db:
                from .business import meta
                source=request.headers.get('X-SPVI-Business')
                if source is not None and source!=meta(db,'business_id'):
                    return jsonify(error='El negocio se restauró. Actualiza la página antes de operar.'),409
                old=db.execute('SELECT digest,result,status FROM http_requests WHERE key=?',(key,)).fetchone()
                if old:
                    if old['digest']!=digest: return jsonify(error='Identificador reutilizado con otros datos.'),409
                    return app.response_class(old['result'],status=old['status'],mimetype='application/json')
                response=app.make_response(view(*args,**kwargs))
                if response.status_code>=400: raise RevertirRespuesta(response)
                db.execute('INSERT INTO http_requests VALUES(?,?,?,?)',(key,digest,response.get_data(as_text=True),response.status_code))
                return response
        except RevertirRespuesta as error: return error.response
    return wrapped
