"""Fotos locales normalizadas: nunca abrir rutas o URL procedentes del catálogo."""
import base64
import warnings
from io import BytesIO
from PIL import Image, ImageOps, UnidentifiedImageError

MAX_FILE=5*1024*1024


def decode(value):
    if not isinstance(value,str) or len(value)>MAX_FILE*4//3+8: raise ValueError('Foto demasiado grande (máximo 5 MB).')
    try:
        raw=base64.b64decode(value,validate=True)
        if len(raw)>MAX_FILE: raise ValueError()
        with warnings.catch_warnings():
            warnings.simplefilter('error',Image.DecompressionBombWarning)
            with Image.open(BytesIO(raw)) as source:
                if source.width*source.height>16000000: raise ValueError()
                image=ImageOps.exif_transpose(source).convert('RGB')
                image.thumbnail((1600,1600))
                output=BytesIO(); image.save(output,'JPEG',quality=85)
                return output.getvalue()
    except (ValueError,UnidentifiedImageError,OSError,Image.DecompressionBombError,Image.DecompressionBombWarning):
        raise ValueError('Foto no válida: usa JPEG, PNG o WebP de hasta 16 megapíxeles.') from None


def document(db):
    return [dict(kind=r['kind'],id=r['id'],jpeg=base64.b64encode(r['content']).decode('ascii')) for r in db.execute('SELECT * FROM photos')]


def validate(rows):
    if not isinstance(rows,list) or len(rows)>10000: raise ValueError('Fotos no válidas.')
    result=[];seen=set();size=0
    for row in rows:
        if not isinstance(row,dict) or row.get('kind') not in ('productos','servicios') or type(row.get('id')) is not int or row['id']<1: raise ValueError('Foto sin artículo válido.')
        key=(row['kind'],row['id'])
        if key in seen: raise ValueError('Foto repetida.')
        seen.add(key); content=decode(row.get('jpeg')); size+=len(content)
        if size>64*1024*1024: raise ValueError('Las fotos exceden 64 MB.')
        result.append((*key,content))
    return result
