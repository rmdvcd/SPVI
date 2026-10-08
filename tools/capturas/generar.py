#!/usr/bin/env python3
"""
Maquetas HTML de las pantallas de «Apps vinculadas» (0.19.x) y de los cambios de la 0.20.0 (vendedor, cobro por
empleado, cierre de turno pedido, «Sin existencia»…) y de la 0.21.0 (recorrido inicial, módulos, licencia con
secundarias, solicitud de cierre, teléfono del empleado, miniaturas, menú + vertical) con el estilo del PDF de capturas.

No son capturas del dispositivo: reproducen los textos, iconos (iconos.json, extraídos de SpviIcons.kt), colores
(ColorTokens.kt) y la disposición de VinculacionScreen.kt, BloqueoSecundaria.kt, AjustesScreen.kt y PlanAviso.kt.

Uso:  python3 generar.py SALIDA_DIR   →  SALIDA_DIR/pNN.html (una página A4 apaisada por maqueta)
Luego: node render.js SALIDA_DIR      →  SALIDA_DIR/pNN.pdf
"""
import base64, html, io, json, os, sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, "..", ".."))
ICONOS = json.load(open(os.path.join(AQUI, "iconos.json")))


def datauri(ruta, mime):
    return f"data:{mime};base64," + base64.b64encode(open(ruta, "rb").read()).decode()


LOGO = datauri(os.path.join(RAIZ, "designsystem/src/main/res/drawable-nodpi/spvi_logo.png"), "image/png")
ESTADO = datauri(os.path.join(RAIZ, "app/src/main/res/drawable-xxhdpi/ic_stat_spvi.png"), "image/png")
ORBITRON = datauri(os.path.join(RAIZ, "designsystem/src/main/res/font/orbitron.ttf"), "font/ttf")

TEMAS = {
    "claro": dict(primary="#2B4FA3", onPrimary="#FFFFFF", pc="#DCE4FA", onPc="#0F2660", sc="#FFE0CC", onSc="#3A1A00",
                  bg="#F8F9FA", surface="#FFFFFF", on="#1A1C1E", onVar="#5F6368", cont="#F1F3F5", contHigh="#EBEDF0",
                  outline="#767B82", outVar="#E0E3E7", tc="#CDEBF3", onTc="#0B3440", aBajo="#8A6A00", aCrit="#C62828", aIns="#9A3412", aInsB="#2F6F80",
                  c1="#2B4FA3", c2="#B4541A", c3="#8B5E34", c4="#A23B72", c5="#6B6B00", c6="#80868D", error="#C62828", onError="#FFFFFF", ec="#FDE7E7", onEc="#5F1111",
                  sombra="0 1px 3px rgba(0,0,0,.12), 0 1px 2px rgba(0,0,0,.08)", notif="#ECEEF2"),
    "oscuro": dict(primary="#66D1EF", onPrimary="#00212C", pc="#1F3A4A", onPc="#CDEFFB", sc="#5A2F10", onSc="#FFDCC2",
                   bg="#0F1318", surface="#1A1F26", on="#E6E6E6", onVar="#B3B9C2", cont="#242A33", contHigh="#2A313B",
                   outline="#8C939E", outVar="#363E4A", tc="#12434F", onTc="#CDEBF3", aBajo="#FFD54F", aCrit="#EF9A9A", aIns="#FFB74D", aInsB="#7FC4D4",
                   c1="#66D1EF", c2="#F6A15C", c3="#D4A373", c4="#F48FB1", c5="#C5C56A", c6="#9E9E9E", error="#EF9A9A", onError="#3B0000", ec="#5C1A1A", onEc="#FFDAD6",
                   sombra="none", notif="#2B2B2B"),
}


def icono(nombre, tam=24, color="currentColor"):
    caminos = "".join(f'<path d="{p}"/>' for p in ICONOS[nombre])
    return f'<svg width="{tam}" height="{tam}" viewBox="0 0 24 24" fill="{color}" style="flex:none;display:block">{caminos}</svg>'


e = html.escape

# ───────────────────────── componentes ─────────────────────────

def barra_estado(hora="9:41"):
    return f'''<div class="sb"><span>{hora}</span><span class="sbr">
      <svg width="14" height="12" viewBox="0 0 14 12" fill="currentColor"><rect x="0" y="8" width="2.5" height="4"/><rect x="3.8" y="5.5" width="2.5" height="6.5"/><rect x="7.6" y="3" width="2.5" height="9"/><rect x="11.4" y="0" width="2.5" height="12"/></svg>
      <svg width="9" height="14" viewBox="0 0 9 14" fill="currentColor"><rect x="2.5" y="0" width="4" height="2" rx=".5"/><rect x="0" y="1.5" width="9" height="12.5" rx="1.5"/></svg></span></div>'''


def barra_sup(titulo, atras=True, logo=False, acciones=""):
    izq = f'<span class="tbi">{icono("ArrowBack")}</span>' if atras else ""
    if acciones:
        return f'<div class="tb">{izq}<span class="tbt">{e(titulo)}</span><span style="margin-left:auto;display:flex;gap:8px">{acciones}</span></div>'
    if logo:
        izq = f'<img class="tblogo" src="{LOGO}">'
        return f'<div class="tb">{izq}<span class="tbt marca">{e(titulo)}</span></div>'
    return f'<div class="tb">{izq}<span class="tbt">{e(titulo)}</span></div>'


def fila(titulo, sub=None, ic=None, chevron=True, badge=None):
    lead = f'<span class="li-ic">{icono(ic)}</span>' if ic else ""
    # 0.20.0: subtítulo de 2 líneas (\n), como subtituloEmpleado con subtitleMaxLines = 2.
    s = f'<div class="li-sub{" dos" if sub and chr(10) in sub else ""}">{e(sub).replace(chr(10), "<br>")}</div>' if sub else ""
    tr = ""
    if badge:
        tr += f'<span class="badge">{badge}</span>'
    if chevron:
        tr += f'<span class="li-ic">{icono("KeyboardArrowRight")}</span>'
    return f'<div class="li">{lead}<div class="li-txt"><div class="li-t">{e(titulo)}</div>{s}</div>{tr}</div>'


def banner(tono, texto, detalle=None, accion=None, ic=None):
    ic = ic or {"info": "Key", "aviso": "AccessTime", "critico": "WarningAmber"}[tono]
    d = f'<div class="bn-d">{e(detalle)}</div>' if detalle else ""
    a = f'<span class="bn-a">{icono(accion)}</span>' if accion else ""
    return f'<div class="bn bn-{tono}">{icono(ic)}<div class="bn-txt"><div class="bn-t">{e(texto)}</div>{d}</div>{a}</div>'


def tarjeta(titulo, cuerpo):
    t = f'<div class="cd-t">{e(titulo)}</div>' if titulo else ""
    return f'<div class="cd">{t}{cuerpo}</div>'


def boton(ic, estilo="text", cls=""):
    """Botones de SPVI: solo icono (P24). estilo: filled | text | tonal | error"""
    return f'<span class="bt bt-{estilo} {cls}">{icono(ic)}</span>'


def fila_botones(*bs, extra=""):
    return f'<div class="br" style="{extra}">{"".join(bs)}</div>'


def vacio(titulo, detalle):
    return f'''<div class="es"><img src="{LOGO}" class="es-logo"><div class="es-t">{e(titulo)}</div>
      <div class="es-d">{e(detalle)}</div></div>'''


def check(on):
    return f'<span class="chk {"on" if on else ""}">{icono("Check", 16) if on else ""}</span>'


def radio(on):
    return f'<span class="rad {"on" if on else ""}"><i></i></span>'


def opcion(ctrl, et, det):
    return f'<div class="op">{ctrl}<div><div class="op-t">{e(et)}</div><div class="op-d">{e(det)}</div></div></div>'


def campo(etiqueta, valor):
    return f'''<div class="tf"><span class="tf-l">{e(etiqueta)}</span><span class="tf-v">{e(valor)}</span>
      <span class="tf-x">{icono("Close", 20)}</span></div>'''


def nav(sel=4):
    ics = ["Home", "Inventory2", "ManoRecibe", "History", "Settings"]
    return '<div class="nv">' + "".join(
        f'<span class="nv-i {"sel" if i == sel else ""}">{icono(n)}</span>' for i, n in enumerate(ics)) + "</div>"


def dialogo(titulo, cuerpo, confirmar="Check", estilo="filled"):
    pie = boton("Close", "tonal") + (boton(confirmar, estilo) if confirmar else "")
    return f'''<div class="scrim"></div><div class="dlg"><img src="{LOGO}" class="dlg-logo">
      <div class="dlg-t">{e(titulo)}</div><div class="dlg-c">{cuerpo}</div><div class="dlg-f">{pie}</div></div>'''


def qr_svg(texto, tam=200):
    import qrcode
    q = qrcode.QRCode(border=2, error_correction=qrcode.constants.ERROR_CORRECT_M)
    q.add_data(texto)
    q.make()
    m = q.get_matrix()
    n = len(m)
    r = "".join(f'<rect x="{x}" y="{y}" width="1" height="1"/>' for y, f in enumerate(m) for x, v in enumerate(f) if v)
    return f'<svg width="{tam}" height="{tam}" viewBox="0 0 {n} {n}" shape-rendering="crispEdges" style="background:#fff;border-radius:8px"><g fill="#000">{r}</g></svg>'


# ───────────────────────── pantallas ─────────────────────────
TEXTO_PRINCIPAL = ("Esta es la app principal: guarda todos los datos y la licencia. Las apps de tus empleados se vinculan "
                   "con un código QR y envían sus ventas por la wifi del local.")
BANNER_RED = banner("info", "Red local activa", "Este teléfono: 192.168.1.34", "Wifi")
LUIS_ULTIMA = "Última vez: 02/10/2026 09:12"
EMPLEADOS_019 = fila("Ana", "Conectada ahora", "Devices") + fila("Luis", "Última vez: 01/10/2026 18:40", "Devices")
# 0.20.0 (H5): con turno abierto, la fila dice desde cuándo (o que el cierre está pedido) encima del estado.
EMPLEADOS = (fila("Ana", "Turno abierto desde las 08:15\nConectada ahora", "Devices") +
             fila("Luis", "Turno abierto desde las 08:30\n" + LUIS_ULTIMA, "Devices"))
EMPLEADOS_CIERRE = (fila("Ana", "Turno abierto desde las 08:15\nConectada ahora", "Devices") +
                    fila("Luis", "Cierre pedido · se cerrará al conectar\n" + LUIS_ULTIMA, "Devices"))


def p_ajustes_principal():
    filas = [
        fila("Completar configuración", "Todo listo", "Checklist"),
        fila("Perfil", "Nombre, apellidos y carné", "Person"),
        fila("Licencia", "Estado, solicitud y activación", "Key"),
        '<div class="resalta">' + fila("Apps vinculadas", "Principal · 2 secundarias", "Devices") + "</div>",
        fila("Pago electrónico", "Teléfonos y cuentas para cobrar por transferencia", "CreditCard"),
        fila("Precios", "Ajustes de precio por pago o por importe", "AttachMoney"),
        fila("Avisos de inventario", "Cuándo avisar de stock bajo o crítico", "WarningAmber"),
        fila("Permisos del teléfono", "Cámara: se pide solo al leer el QR de vinculación o al hacer una foto", "PhotoCamera"),
        fila("Respaldo", "Copia cifrada de todo e importar", "Backup"),
        fila("Migrar a otro teléfono", "Pasar datos y licencia, con autorización del desarrollador", "PhonelinkSetup"),
    ]
    return barra_sup("SPVI", logo=True) + '<div class="ct">' + "".join(filas) + "</div>" + nav()


def p_ajustes_secundaria():
    filas = [
        '<div class="resalta">' + fila("Apps vinculadas", "Secundaria de Barbería Mayra", "Devices") + "</div>",
        fila("Permisos del teléfono", "Cámara: se pide solo al leer el QR de vinculación o al hacer una foto", "PhotoCamera"),
        fila("Ayuda", "Manual breve de uso", "Help"),
        fila("Soporte", "Datos de contacto del desarrollador", "SupportAgent"),
    ]
    return barra_sup("SPVI", logo=True) + '<div class="ct">' + "".join(filas) + "</div>" + nav()


def cabecera_principal():
    return f'<p class="intro">{e(TEXTO_PRINCIPAL)}</p><div class="px">{BANNER_RED}</div>'


def p_principal_vacia():
    return (barra_sup("Apps vinculadas") + '<div class="ct">' + cabecera_principal() +
            vacio("Aún no hay apps secundarias",
                  "Agrega la app de cada empleado. Necesitan estar en la misma wifi que este teléfono (o en su zona wifi).") +
            fila_botones(boton("QrCodeScanner")) + "</div>" + '<span class="fab">' + icono("PersonAdd") + "</span>")


def lista_principal(empleados=None, snack=None):
    sn = f'<div class="snack">{e(snack)}</div>' if snack else ""
    return (barra_sup("Apps vinculadas") + '<div class="ct">' + cabecera_principal() +
            '<div class="lt">Secundarias (2 de 5)</div>' + (empleados or EMPLEADOS) + sn +
            fila_botones(boton("QrCodeScanner"), extra="padding:16px 0") + "</div>" +
            '<span class="fab">' + icono("PersonAdd") + "</span>")


PERMISOS = [
    ("Vender productos", "Abrir su turno y vender productos del inventario"),
    ("Vender servicios", "Abrir su turno y vender servicios"),
    ("Editar inventario y servicios", "Crear, cambiar o eliminar productos, insumos y servicios, y sus existencias"),
    ("Cambiar precios", "Ajustes de precio y cambios de precio de venta"),
    ("Exportar y compartir", "Sacar tablas y PDF del inventario, los servicios y sus registros"),
]


def permisos(activos):
    return '<div class="sub-t">Permisos</div>' + "".join(opcion(check(i in activos), *p) for i, p in enumerate(PERMISOS))


def p_agregar():
    cuerpo = campo("Nombre del empleado", "Ana") + permisos({0, 1, 4})  # 0.21.0 (C3): todos menos editar y precios
    return lista_principal() + dialogo("Agregar app de un empleado", cuerpo, "Check")


def p_qr():
    cuerpo = (f'<div class="cen">{qr_svg("SPVI-VINC1:Q2FwdHVyYSBkZSBlamVtcGxvOyBubyBlcyB1biBjb2RpZ28gcmVhbA", 196)}</div>'
              '<div class="cen bm">Vence a las 09:55 · un solo uso</div>'
              '<div class="cen bs">Escanéalo en el teléfono del empleado: Ajustes → Apps vinculadas → Usar esta app como secundaria.</div>')
    return lista_principal() + dialogo("Código para Ana", cuerpo, None)


def combo(etiqueta, valor, ic):
    return f'''<div class="tf"><span class="tf-l">{e(etiqueta)}</span><span class="tf-ic">{icono(ic, 20)}</span>
      <span class="tf-v">{e(valor)}</span><span class="tf-x">{icono("ArrowDropDown")}</span></div>'''


def p_editar():
    # 0.20.0: turno + «Pedir cierre» arriba (H5); cobro por transferencia (H4); Nuevo código y Quitar en una fila.
    turno = (f'<div class="li li-d"><span class="li-ic">{icono("Storefront")}</span><div class="li-txt"><div class="li-t">'
             f'Turno abierto desde las 08:30</div></div>{boton("Logout", "tonal")}</div>')
    cobro = ('<div class="sub-t">Cobro por transferencia</div>' +
             combo("Cobrar en la tarjeta", "Metro · •••• 5678", "CreditCard") +
             campo_fijo("Teléfono del empleado", "5398 7654", "Phone") +
             '<div class="bs">Su app solo recibe esta tarjeta para el QR de cobro, junto con el teléfono que escribió el empleado.</div>')
    cuerpo = turno + permisos({0, 1, 4}) + cobro + fila_botones(boton("Devices"), boton("LinkOff"), extra="gap:32px;padding:0")
    return lista_principal() + dialogo("Luis", cuerpo, "Check")


def p_quitar():
    cuerpo = ('<div class="cen bm">Deja de sincronizar y, cuando se conecte, sus datos se borran. '
              'Las ventas que ya envió se quedan aquí.</div>')
    return lista_principal() + dialogo("¿Quitar la app de Luis?", cuerpo, "LinkOff", "error")


def p_usar_secundaria():
    cuerpo = ('<div class="cen bm">Escanearás el código que te muestre la app principal. Al vincularse, se borran los datos '
              'de este teléfono y se usan los de la principal.</div>')
    return (barra_sup("Apps vinculadas") + '<div class="ct">' + cabecera_principal() +
            vacio("Aún no hay apps secundarias",
                  "Agrega la app de cada empleado. Necesitan estar en la misma wifi que este teléfono (o en su zona wifi).") +
            fila_botones(boton("QrCodeScanner")) + "</div>" + '<span class="fab">' + icono("PersonAdd") + "</span>" +
            dialogo("Usar esta app como secundaria", cuerpo, "QrCodeScanner"))


def secundaria(estado_banner, pendientes_txt):
    modos = (opcion(radio(True), "Automática", "Al momento, mientras haya conexión con la app principal") +
             opcion(radio(False), "Manual", "Solo cuando toques «Sincronizar ahora» (y siempre antes de abrir un turno)"))
    return (barra_sup("Apps vinculadas") + '<div class="ct">' +
            '<div class="px">' + tarjeta("Secundaria de Barbería Mayra",
                                         '<div class="cen bl">Empleado: Ana</div>'
                                         f'<div class="cen bm">{e(pendientes_txt)}</div>') + "</div>" +
            f'<div class="px">{estado_banner}</div>' +
            fila_botones(boton("Sync", "filled")) +
            '<div class="px">' + tarjeta("Sincronización", f'<div class="izq">{modos}</div>') + "</div>" +
            '<div class="px">' + tarjeta("Lo que puedes hacer",
                                         '<div class="izq bm">• Vender productos<br>• Vender servicios</div>') + "</div>" +
            fila_botones(boton("LinkOff"), extra="padding:16px 0") + "</div>")


def p_secundaria():
    return secundaria(banner("info", "Conectada con la app principal"), "Todo enviado · Última vez: 02/10/2026 09:40")


def p_secundaria_sin_red():
    b = banner("aviso", "No se encuentra la app principal. Conecta los dos teléfonos a la misma wifi. Hay 3 envíos pendientes.",
               None, "Wifi")
    return secundaria(b, "3 pendientes de enviar · Última vez: 02/10/2026 09:12")


def p_bloqueo():
    return (barra_sup("SPVI", logo=True) + '<div class="ct centro">' +
            vacio("La licencia de la app principal no está activa",
                  "Esta app trabaja con la licencia de la app principal. Pide al dueño que la active y luego toca "
                  "«Sincronizar ahora» con los dos teléfonos en la misma wifi.") +
            fila_botones(boton("Sync", "filled")) + fila_botones(boton("LinkOff")) + "</div>")


def p_aviso(tema):
    t = TEMAS[tema]
    tarjeta_n = lambda texto: f'''<div class="nt"><span class="nt-ic"><img src="{ESTADO}"></span>
        <div><div class="nt-app">SPVI</div><div class="nt-t">SPVI</div><div class="nt-x">{e(texto)}</div></div></div>'''
    return f'''<div class="lock"><div class="sb sb-lock"><span>9:41 <img src="{ESTADO}" class="sb-ic"></span><span>100 %</span></div>
      <div class="lk-h">9:41</div><div class="lk-d">jueves, 2 de octubre</div>
      {tarjeta_n("Turno abierto desde las 09:30 · 2 apps conectadas")}
      <p class="lk-n">Un solo aviso para el turno y las apps vinculadas. Sin turno abierto, en la principal:
      «2 apps conectadas» o «Esperando a tus apps vinculadas». Sin importes.</p></div>'''


# ───────────────────────── 0.20.0 ─────────────────────────

def p_pedir_cierre():
    cuerpo = ('<div class="cen bm">Su app lo cerrará al conectarse, nunca durante una venta: si está cobrando, al terminar. '
              'Las ventas del turno se quedan registradas.</div>')
    return lista_principal() + dialogo("¿Pedir el cierre del turno de Luis?", cuerpo, "Logout")


def p_lista_cierre():
    return lista_principal(EMPLEADOS_CIERRE, "Cierre pedido: el turno de Luis se cerrará cuando su app se conecte")


def turno_card(abierto, desde="Desde las 08:30"):
    sub = desde if abierto else "Ábrelo para empezar a vender"
    return (f'<div class="px"><div class="cd cd-fila"><span class="li-ic pri">{icono("Storefront")}</span>'
            f'<div class="li-txt"><div class="li-t">{"Turno abierto" if abierto else "Turno cerrado"}</div>'
            f'<div class="li-sub">{sub}</div></div><span class="sw {"on" if abierto else ""}"><i></i></span></div></div>')


def accesos(activos):
    cls = "" if activos else "off"
    return f'<div class="br {cls}"><span class="bt bt-filled">{icono("AddShoppingCart")}</span></div>'


def alerta(etiqueta, n, tono):
    return f'<div class="al al-{tono}"><span class="al-n">{n}</span><span class="al-t">{e(etiqueta)}</span></div>'


def pantalla_inicio(cuerpo):
    return barra_sup("SPVI", logo=True) + f'<div class="ct">{cuerpo}</div>' + nav(0)


def p_inicio_cierre():
    b = banner("aviso", "El encargado pidió cerrar tu turno. Se cierra en cuanto no haya una venta en curso.")
    return pantalla_inicio(f'<div class="px">{b}</div>' + turno_card(True) + accesos(False))


def p_venta_cierre():
    b = banner("aviso", "El encargado pidió cerrar tu turno: se cerrará al terminar esta venta.")
    lineas = (fila("Refresco de lata", "2 × 150.00 CUP", None, False) + fila("Galletas", "1 × 120.00 CUP", None, False))
    return (barra_sup("Venta") + '<div class="ct">' + f'<div class="px">{b}</div>' +
            '<div class="total"><div class="bs">Total</div><div class="tot">420.00 CUP</div></div>' + lineas +
            '<div style="flex:1"></div>' + fila_botones(boton("Close", "tonal"), boton("ArrowForward", "filled"),
                                                         extra="padding:16px 0 28px") + "</div>")


def p_inicio_cerrado():
    b = banner("aviso", "El encargado cerró tu turno desde la app principal.", None, "Close")
    return pantalla_inicio(f'<div class="px">{b}</div>' + turno_card(False) + accesos(True))


def registros_fondo():
    tabs = '<div class="tabs"><span class="sel">Ventas</span><span>Transferencias</span><span>Movimientos</span><span>Turnos</span></div>'
    filas = "".join(fila(f"02/10/2026 {h}", i, None) for h, i in
                    (("09:20", "420.00 CUP · Efectivo"), ("09:05", "1,450.00 CUP · Transferencia"), ("08:47", "300.00 CUP · Efectivo")))
    return barra_sup("Registros", atras=False) + f'<div class="ct">{tabs}{filas}</div>' + nav(3)


def campos(pares):
    return '<div class="kv">' + "".join(f'<div><span>{e(k)}</span><b>{e(v)}</b></div>' for k, v in pares) + "</div>"


def p_ficha_vendedor():
    cuerpo = campos([("Pago", "Efectivo"), ("Vendedor", "Luis"), ("2 × Refresco de lata", "150.00 CUP c/u · 300.00 CUP"),
                     ("1 × Galletas", "120.00 CUP c/u · 120.00 CUP"), ("Unidades", "3"), ("Total", "420.00 CUP"),
                     ("Costo", "260.00 CUP"), ("Ganancia", "160.00 CUP")])
    return registros_fondo() + dialogo("Venta del 02/10/2026 09:20", cuerpo, None)


def chips(opciones, sel):
    return '<div class="chips">' + "".join(
        f'<span class="chip {"on" if o == sel else ""}">{icono("Check", 18) if o == sel else ""}{e(o)}</span>' for o in opciones) + "</div>"


def p_filtro_vendedor():
    cuerpo = (combo("Período", "Este mes", "CalendarMonth") +
              '<div class="dos-col">' + campo("Importe mínimo", "$ 0") + campo("Importe máximo", "$ —") + "</div>" +
              '<div class="sub-t">Vendedor</div>' + chips(["Todos", "Ana Pérez", "Luis", "Marta"], "Luis"))
    return registros_fondo() + dialogo("Filtrar por fecha e importe", cuerpo, "Check")


def p_negativos():
    als = ('<div class="als">' + alerta("Sin existencia", 2, "lleno") + alerta("Stock inventario bajo", 5, "bajo") + alerta("Stock insumo bajo", 3, "insb") + "</div>")
    return pantalla_inicio(turno_card(True, "Desde las 08:15") + accesos(True) + als)


def p_pegar_secundaria():
    cuerpo = (campo("Nº de transacción", "MM10040FEJ987") + campo("Nombre y apellidos", "Carlos Díaz") +
              fila_botones('<span class="btn2">' + icono("ContentPaste", 18) + "Pegar SMS</span>") +
              '<div class="cen bs">Pide al cliente el SMS de su transferencia y escribe el nº de transacción. Si te lo reenvía, pégalo.</div>')
    return (barra_sup("Venta") + '<div class="ct">' +
            '<div class="total"><div class="bs">Total</div><div class="tot">420.00 CUP</div></div>' +
            f'<div class="px form">{cuerpo}</div><div style="flex:1"></div>' +
            fila_botones(boton("ArrowBack", "tonal"), boton("Check", "filled"), extra="padding:16px 0 28px") + "</div>")


def p_migrar():
    paso = tarjeta("2. Copia tus datos",
                   '<div class="cen bm">Crea un respaldo completo con contraseña y envíalo al teléfono nuevo. Allí: Ajustes → Respaldo → Importar.</div>'
                   '<div class="cen bs">Si tienes apps de empleados vinculadas, sincronízalas antes: en el teléfono nuevo habrá que '
                   'vincularlas otra vez y se borran sus datos no enviados.</div>' +
                   f'<span class="btn2">{icono("Backup", 18)}Crear respaldo completo</span>')
    p1 = tarjeta("1. Pide la autorización", '<div class="cen bm">El desarrollador autoriza el cambio de teléfono de la licencia.</div>')
    return barra_sup("Migrar a otro teléfono") + f'<div class="ct" style="padding:8px 16px;gap:12px">{p1}{paso}</div>'



# ───────────────────────── 0.21.0 ─────────────────────────

def campo_fijo(etiqueta, valor, ic):
    """Campo de solo lectura (0.21.0, C2): el teléfono del empleado lo escribe él al vincular."""
    return (f'<div class="tf"><span class="tf-l">{e(etiqueta)}</span><span class="tf-ic">{icono(ic, 20)}</span>'
            f'<span class="tf-v">{e(valor)}</span></div>')


def asistente(paso, titulo, cuerpo, boton_txt="Siguiente"):
    return ('<div class="wz">' + f'<div class="wz-p">{e(paso)}</div><div class="wz-t">{e(titulo)}</div>' + cuerpo +
            '<div style="flex:1"></div>' + f'<span class="wzbtn">{e(boton_txt)}</span>' +
            '<div class="cen bs" style="padding-bottom:20px">Omitir</div></div>')


def p_tour_tipo():
    OPC = lambda on, t, d: f'<div class="opc {"on" if on else ""}">{opcion(radio(on), t, d)}</div>'
    cuerpo = (OPC(True, "Principal (dueño)", "Este teléfono controla el negocio: inventario, precios, empleados y licencia.") +
              OPC(False, "Secundaria (empleado)", "Este teléfono es de un empleado: vende con el inventario y los permisos que le dé el dueño.") +
              '<div class="cen bs">Solo hay un teléfono principal por negocio. Cada empleado usa una app secundaria vinculada a él.</div>')
    return barra_estado_vacio() + asistente("Paso 1 de 6", "Tipo de app", cuerpo)


def barra_estado_vacio():
    return '<div style="height:24px;flex:none"></div>'


def p_tour_objetivo():
    OPC = lambda on, ic, t, d: (f'<div class="opc {"on" if on else ""}"><div class="op">{check(on)}'
                                f'<span class="li-ic">{icono(ic)}</span><div><div class="op-t">{e(t)}</div>'
                                f'<div class="op-d">{e(d)}</div></div></div></div>')
    cuerpo = ('<div class="cen bs">Marca lo que harás con SPVI. Lo que no marques no aparecerá. Puedes cambiarlo en Ajustes → Perfil.</div>' +
              OPC(True, "ShoppingCart", "Ventas", "Vender productos del inventario") +
              OPC(True, "Inventory2", "Inventario", "Productos, insumos y existencias") +
              OPC(False, "ManoRecibe", "Servicios", "Vender servicios (barbería, reparaciones…)") +
              '<div class="cen bs">Vender productos necesita el inventario: se activa también.</div>')
    return barra_estado_vacio() + asistente("Paso 2 de 6", "Tu negocio", cuerpo)


def p_tour_empleados():
    cuerpo = ('<div class="cen bm">Cada empleado usa su propia app secundaria. El precio de la licencia incluye un importe por cada una.</div>' +
              f'<div class="stp">{boton("Remove", "tonal")}<b>2 empleados</b>{boton("Add", "tonal")}</div>' +
              '<div class="cen bs">Puedes cambiarlo al pedir la licencia.</div>')
    return barra_estado_vacio() + asistente("Paso 3 de 6", "Empleados", cuerpo)


def p_tour_secundaria():
    cuerpo = (campo("Tu número de teléfono", "5398 7654") +
              '<div class="bs">8 dígitos. Va en tu QR de cobro: el SMS de la transferencia te llegará a ti.</div>')
    fondo = barra_estado_vacio() + asistente("Paso 1 de 1", "Tipo de app",
                                             '<div class="cen bs">Al continuar escribirás tu número de teléfono y escanearás el código que te muestre el dueño.</div>',
                                             "Vincular con el dueño")
    return fondo + dialogo("Usar esta app como secundaria", cuerpo, "QrCodeScanner")


def p_licencia_secundarias():
    tipos = "".join(opcion(radio(t == "Anual"), t, p) for t, p in
                    (("Mensual", "6,000.00 CUP"), ("Semestral", "30,000.00 CUP"), ("Anual", "50,000.00 CUP"), ("Perpetua", "90,000.00 CUP")))
    sel = ('<div class="sub-t" style="text-align:center">Apps secundarias (empleados)</div>' +
           f'<div class="stp">{boton("Remove", "tonal")}<b>2 apps secundarias</b>{boton("Add", "tonal")}</div>' +
           '<div class="cen bm"><b>Total: 68,000.00 CUP (50,000.00 CUP + 2 × 9,000.00 CUP)</b></div>')
    return (barra_sup("Licencia") + '<div class="ct" style="padding:8px 16px;gap:12px">' +
            tarjeta("Solicitar licencia", f'<div class="izq">{tipos}</div>' + sel) +
            fila_botones(boton("Sms", "filled")) + "</div>")


def p_perfil_modulos():
    OPC = lambda on, ic, t: f'<div class="op">{check(on)}<span class="li-ic">{icono(ic)}</span><div class="op-t">{e(t)}</div></div>'
    mod = tarjeta("Tu negocio", '<div class="cen bs">Lo que no marques desaparece de la barra inferior, de Inicio y de Registros. No se borra nada.</div>'
                  '<div class="izq">' + OPC(True, "ShoppingCart", "Ventas") + OPC(True, "Inventory2", "Inventario") +
                  OPC(False, "ManoRecibe", "Servicios") + "</div>")
    datos = ('<div class="form">' + campo("Nombre", "Mayra") + campo("Apellidos", "Pérez Díaz") + campo("Carné de identidad", "85010112345") + "</div>")
    nv = '<div class="nv">' + "".join(f'<span class="nv-i {"sel" if n == "Settings" else ""}">{icono(n)}</span>'
                                      for n in ("Home", "Inventory2", "History", "Settings")) + "</div>"
    return (barra_sup("Perfil") + '<div class="ct" style="padding:8px 16px;gap:12px">' + mod + datos +
            fila_botones(boton("Check", "filled")) + "</div>" + nv)


def fila_foto(titulo, sub, foto=True):
    th = '<span class="thumb"></span>' if foto else f'<span class="thumb v">{icono("Inventory2", 20)}</span>'
    return (f'<div class="li">{check(False)}{th}<div class="li-txt"><div class="li-t">{e(titulo)}</div>'
            f'<div class="li-sub">{e(sub)}</div></div></div>')


def dona():
    """0.21.1 (H5): paleta de gráficos sin los tonos de las alertas. Leyenda con nombre y porcentaje (el color no es lo único)."""
    import math
    datos = [("Bebidas", 34), ("Dulces", 22), ("Aseo", 18), ("Café", 12), ("Granos", 9), ("Otras", 5)]
    r, c, ang, arcos = 34, 2 * math.pi * 34, 0, ""
    for i, (_, p) in enumerate(datos):
        largo = c * p / 100
        arcos += (f'<circle r="{r}" cx="45" cy="45" fill="none" stroke="var(--c{i + 1})" stroke-width="14" '
                  f'stroke-dasharray="{largo - 2:.1f} {c:.1f}" stroke-dashoffset="{-ang:.1f}" transform="rotate(-90 45 45)"/>')
        ang += largo
    leyenda = "".join(f'<div style="display:flex;align-items:center;gap:6px;font-size:12px"><i style="width:10px;height:10px;'
                      f'border-radius:50%;background:var(--c{i + 1});display:inline-block"></i>{e(n)} {p} %</div>'
                      for i, (n, p) in enumerate(datos))
    return (f'<div style="display:flex;gap:14px;align-items:center"><svg width="90" height="90">{arcos}'
            f'<text x="45" y="49" text-anchor="middle" font-size="12" fill="currentColor">212 u</text></svg>'
            f'<div style="display:grid;grid-template-columns:1fr 1fr;gap:4px 10px">{leyenda}</div></div>')


def p_inicio_solicitud():
    b = banner("aviso", "Un empleado pide cerrar su turno", None, "KeyboardArrowRight")
    als = ('<div class="als">' + alerta("Sin existencia", 1, "lleno") + alerta("Stock inventario crítico", 2, "crit") + alerta("Stock insumo crítico", 1, "ins") + "</div>")
    return pantalla_inicio(f'<div class="px">{b}</div>' + turno_card(True, "Desde las 08:15") + accesos(True) + als +
                           '<div class="px">' + tarjeta("Inventario", '<div class="bs">Unidades por categoría</div>' + dona()) + "</div>")


EMPLEADOS_SOLICITUD = (fila("Ana", "Turno abierto desde las 08:30\\nConectada", "Devices") +
                       fila("Luis", "Pide cerrar su turno · desde las 08:40\\nConectada", "Devices", badge="1"))


def p_aprobar_cierre():
    turno = (f'<div class="li li-d"><span class="li-ic">{icono("Storefront")}</span><div class="li-txt"><div class="li-t">'
             f'Pide cerrar su turno</div><div class="li-sub">Turno abierto desde las 08:40</div></div></div>' +
             fila_botones(boton("Close", "tonal"), boton("Check", "filled"), extra="padding:0"))
    cuerpo = turno + permisos({0, 1, 4})
    return lista_principal(EMPLEADOS_SOLICITUD) + dialogo("Luis", cuerpo, "Check")


def p_solicitar_cierre():
    cuerpo = '<div class="cen bm">El encargado debe aprobarlo. Mientras tanto puedes seguir vendiendo.</div>'
    return (pantalla_inicio(turno_card(True) + accesos(True)) +
            dialogo("¿Solicitar el cierre del turno?", cuerpo, "Logout"))


def p_secundaria_solicitada():
    b = banner("aviso", "Pediste cerrar el turno. Se cerrará cuando el encargado lo apruebe; mientras, puedes seguir vendiendo.")
    als = '<div class="als">' + alerta("Stock inventario bajo", 3, "bajo") + "</div>"
    return pantalla_inicio(f'<div class="px">{b}</div>' + turno_card(True) + accesos(True) + als)


def p_movimientos_hecho():
    tabs = '<div class="tabs"><span>Ventas</span><span>Transferencias</span><span class="sel">Movimientos</span><span>Turnos</span></div>'
    filas = "".join(fila(f"02/10/2026 {h}", i, None) for h, i in
                    (("10:12", "Entrada +24 · Refresco de lata · Mayra"), ("09:20", "Venta −2 · Refresco de lata · Luis"),
                     ("09:05", "Ajuste −1 · Galletas · Ana")))
    return barra_sup("Registros", atras=False) + f'<div class="ct">{tabs}{filas}</div>' + nav(3)



# ── 0.22.0/0.23.0: licencia corta cifrada, QR por WhatsApp y renovar sin perder días ──
# Vector de referencia de tools/licencia/vector_0.23.txt (clave de firma de PRUEBA) y solicitud de docs/GL_SOLICITUD_PRUEBA.txt.
CORTA_EJEMPLO = "SPVI2:A8Ntuq3AH5-S0FVM2Itwox5ElM8JLkdH1v90_SJsF6KzCtzdFYKcGWYWoXp1RC0191zsFibEJjMpJmCWwJWjkLnY7KDTfPDFw4lrwTr5VFo_db5f84NP9QfStCUSFOSb9HjaQkeWuTWUZGftUUhs0d0y5eIav9Jn59WIyb9WHNln6Ui7Y_Ce6wOVPP9Do3JcgQ-0a--x8-TLCh1g0A"
SOLICITUD_EJEMPLO = "SPVIR1:Az6UNtjKR76BXmW_pLZk31frWvkydAObO_CRD3bYJR7d1wpB38I8jrKE8V2j059PgkYj3IBDWMLRvY5vugZij_TMKVaePWypOeiU_uT32J-lnP6MhvhJK9gPz_mmThz6KOaA3Vg2Q_dZROt12OYdXxnH6jyGPAP0164W12pI52XZVnaayGNmhOnd0Tig68rGmwqoWor9JRonzLzeSJdT_KPxB-hoOCQwSzlaMObsIvLM2gP0U01IcRb72lCHDLomaaj5srzAD1GSqLwpSZH11lM3PzYen_4o-mxAsPUzvzkXuqkeQrkS7LDRhpM1sqVWIGXXgSx-y6rJ3h6iGIA0IpoXiFl6a2PnqbsRVYy7Sd96ZMSd1iujOP7zkAoYrnzmUphfIM3uo9v5WqHs715qNwlIevCANNqjLGywCX2uEGcpCU8nJSuuKnIidGFwFsa6oH9ajcWZZRmLROXDmu7ceBE-EH9qBQR_Z6vpRn4aY7ObeOIMne3gdYUupGmw1DOcTyQ-7RL9uHKc4OAQDt7k1G9cV0z_uVxHPzdtTg34cNc"
TEXTO_SOLICITUD = ("Solicitud de licencia SPVI\nNombre: María Pérez González\nCarné de identidad: 85010112345\n"
                   "Teléfono: +5352345678\nTipo: Mensual\nApps secundarias: 2\nPrecio: 8,000.00 CUP\n"
                   "Renueva: d53a020b-4062-4825-8219-024f997173f8\n\n" + SOLICITUD_EJEMPLO)
TEXTO_LICENCIA = ("Licencia SPVI\nTipo: Mensual\nApps secundarias: 2\nEmitida: 03/10/2026\nVence: 02/11/2026\n"
                  "ID: 7c9e6679-7425-40de-944b-e07fc1f90ae7\n\n" + CORTA_EJEMPLO)


def estado_licencia():
    return ('<div class="cd"><span class="li-ic" style="color:var(--primary)">' + icono("Verified", 32) + '</span>'
            '<div class="cd-t">Licencia Mensual</div><div class="cen bm">Vence el 02/11/2026.</div>'
            '<div class="bs">Emitida 03/10/2026 · Tiempo restante 30 días</div></div>')


def p_licencia_activar_qr():
    renovar = (fila_botones(boton("Refresh", "tonal")) +
               '<div class="cen bs" style="margin-top:-8px">Renovar igual · Mensual · 2 apps secundarias · 8,000.00 CUP</div>')
    activar = tarjeta("Activar licencia",
                      '<div class="cen bs">Pega el mensaje que recibiste del desarrollador (completo o solo la línea que empieza por SPVI2:).</div>' +
                      '<div class="izq">' + campo("Mensaje de licencia", "") + "</div>" +
                      fila_botones(boton("ContentPaste", "tonal"), boton("Check", "filled")))
    return (barra_sup("Licencia") + '<div class="ct" style="padding:8px 16px;gap:12px">' + estado_licencia() + renovar +
            '<div class="cen bs">Paso 3 de 3 · Activa la licencia</div>' + activar + "</div>")


def p_licencia_renovar():
    tipos = "".join(opcion(radio(t == "Mensual"), t, pr) for t, pr in
                    (("Mensual", "6,000.00 CUP"), ("Semestral", "30,000.00 CUP"), ("Anual", "50,000.00 CUP"), ("Perpetua", "90,000.00 CUP")))
    sel = ('<div class="sub-t" style="text-align:center">Apps secundarias (empleados)</div>' +
           f'<div class="stp">{boton("Remove", "tonal")}<b>2 apps secundarias</b>{boton("Add", "tonal")}</div>' +
           '<div class="cen bm"><b>Total: 8,000.00 CUP (6,000.00 CUP + 2 × 1,000.00 CUP)</b></div>')
    wa = ('<span class="bt bt-filled" style="background:#1F7A4D"><svg viewBox="0 0 24 24" width="24" height="24" fill="#fff">'
          '<path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm0 18.2c-1.5 0-3-.4-4.2-1.2l-.3-.2-3 .8.8-2.9-.2-.3'
          'A8.2 8.2 0 1 1 12 20.2zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9'
          'c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.8'
          ' 11.9 11.9 0 0 0 4.6 4c1.7.7 2.4.8 3.2.7.5-.1 1.5-.6 1.8-1.2.2-.6.2-1.1.1-1.2-.1-.1-.3-.2-.5-.3z"/></svg></span>')
    return (barra_sup("Licencia") + '<div class="ct" style="padding:8px 16px;gap:12px">' +
            '<div class="cen bs">Paso 2 de 3 · Pide la licencia</div>' +
            tarjeta("Renovar licencia", f'<div class="izq">{tipos}</div>' + sel +
                    '<div class="sub-t" style="text-align:center">Enviar por</div>' + fila_botones(wa, boton("Sms", "tonal")) +
                    '<div class="cen bs">Se abrirá WhatsApp con tus datos y la solicitud cifrada.</div>' +
                    '<div class="cen bs">Si renuevas antes de que venza no pierdes días: la nueva licencia empieza cuando termine la actual.</div>') +
            fila_botones(boton("Send", "filled")) + "</div>")


def chat(titulo, burbujas, pie):
    """Chat genérico (WhatsApp o Mensajes): no es una pantalla de SPVI; solo muestra lo que se envía o recibe."""
    return (f'<div class="tb" style="background:#1F2C34"><span class="tbi" style="color:#fff">{icono("ArrowBack")}</span>'
            f'<span class="tbt" style="color:#fff">{e(titulo)}</span></div>'
            '<div class="ct" style="padding:12px;gap:8px;background:#EFEAE2">' + burbujas +
            f'<div style="flex:1"></div><div class="cen bs" style="color:#3D4349">{e(pie)}</div></div>')


def burbuja(texto, propia, qr=None, info="", sms=False):
    fondo, radio_ = ("#D9FDD3", "16px 16px 4px 16px") if propia else ("#FFFFFF", "16px 16px 16px 4px")
    color_info = "" if sms else ";color:#3D4349"
    color_txt = "var(--on)" if sms else "#111"
    if sms: fondo = "var(--contHigh)"
    lado = "flex-end" if propia else "flex-start"
    img = (f'<div style="background:#fff;border-radius:10px;display:flex;justify-content:center;padding:6px;margin-bottom:6px">'
           f'{qr_svg(qr, 150)}</div>') if qr else ""
    cuerpo = "<br>".join(e(l) if l else "" for l in texto.split("\n"))
    return (f'<div style="align-self:{lado};max-width:92%;background:{fondo};border-radius:{radio_};padding:8px 10px;'
            f'font-size:10.5px;line-height:14px;word-break:break-all;color:{color_txt}">{img}{cuerpo}</div>'
            + (f'<div class="bs" style="align-self:{lado};padding:0 4px{color_info}">{e(info)}</div>' if info else ""))


def p_solicitud_whatsapp():
    return chat("Ronnie · +53 5181 5604", burbuja(TEXTO_SOLICITUD, True, None, f"Solo texto · {len(TEXTO_SOLICITUD)} caracteres"),
                "SPVI abre este chat con el texto listo: solo pulsa Enviar.")


def p_licencia_whatsapp():
    return chat("Ronnie · +53 5181 5604", burbuja(TEXTO_LICENCIA, False, None, "Respuesta de GL: características + código, solo texto"),
                "Mantén pulsado el mensaje → Copiar. En SPVI: Licencia → Pegar → Activar.")


def p_licencia_sms():
    return (barra_sup("Mensajes") + '<div class="ct" style="padding:16px;gap:8px">' +
            burbuja(TEXTO_LICENCIA, False, None, f"+53 5181 5604 · {len(TEXTO_LICENCIA)} caracteres · 3 SMS (solo el código: 216, 2 SMS)", sms=True) +
            '<div style="flex:1"></div><div class="cen bs">Copia todo el texto y toca Pegar en «Activar licencia». Solo cuenta el código SPVI2:.</div></div>')

# ── 0.24.0: la Descripción identifica el artículo ──

def campo_ayuda(etiqueta, valor, ayuda=None, error=None, marcador=None):
    v = f'<span class="tf-v">{e(valor)}</span>' if valor else f'<span class="tf-v" style="color:var(--onVar)">{e(marcador or "")}</span>'
    borde = ' style="border:2px solid var(--error)"' if error else ""
    lab = f' style="color:var(--error)"' if error else ""
    sop = (f'<div class="bs" style="padding:2px 16px 0;color:var(--error)">{e(error)}</div>' if error else
           f'<div class="bs" style="padding:2px 16px 0">{e(ayuda)}</div>' if ayuda else "")
    return f'<div><div class="tf"{borde}><span class="tf-l"{lab}>{e(etiqueta)}</span>{v}</div>{sop}</div>'


def p_descripcion_form():
    cuerpo = (campo_ayuda("Nombre *", "Cerveza Cristal") +
              campo_ayuda("Descripción", "", error="Ya hay otro producto con este nombre. Escribe una descripción que lo diferencie (p. ej. Lata 350 ml Superior).", marcador="Lata 350 ml Superior") +
              campo_ayuda("Categoría *", "Bebidas") + '<div class="dos-col">' + campo("Precio costo", "$ 120") + campo("Precio venta", "$ 180") + "</div>" +
              campo_ayuda("Cantidad *", "24"))
    return (barra_sup("Nuevo producto") + f'<div class="ct"><div class="px form fe" style="padding-top:8px">{cuerpo}</div>'
            '<div style="flex:1"></div>' + fila_botones(boton("Close", "tonal"), boton("Check", "filled"), extra="padding:16px 0 28px") + "</div>")


def p_descripcion_lista():
    filas = (fila_foto("Cerveza Cristal · Lata 350 ml Superior", "Bebidas · 24 u · 180.00 CUP") +
             fila_foto("Cerveza Cristal · Botella 330 ml", "Bebidas · 12 u · 200.00 CUP") +
             fila_foto("Refresco", "Nombre repetido: añade descripción", False) +
             fila_foto("Refresco", "Nombre repetido: añade descripción", False) +
             fila_foto("Galletas · Paquete 200 g", "Dulces · 40 u · 120.00 CUP"))
    return (barra_sup("Inventario", atras=False) + '<div class="ct"><div class="px">' + chips(["Todos", "Bajo", "Nombre repetido"], "Nombre repetido") +
            "</div>" + filas + "</div>" + nav(1))


# ── 0.25.0: arqueo de caja ──

def p_caja_fondo():
    cuerpo = ('<div class="cen bm">Escribe el efectivo con el que empieza la caja (puede ser 0).</div>' +
              campo_ayuda("Fondo de caja (CUP) *", "1500.00", "Propuesto: lo contado al cerrar el turno anterior."))
    return pantalla_inicio(turno_card(False) + accesos(False)) + dialogo("Abrir turno", cuerpo, "Check")


def p_caja_turno():
    tarjeta_t = (f'<div class="px"><div class="cd cd-fila"><span class="li-ic pri">{icono("Storefront")}</span>'
                 '<div class="li-txt"><div class="li-t">Turno abierto</div><div class="li-sub">Desde las 08:30</div></div>'
                 '<span class="sw on"><i></i></span></div></div>')
    acc = (f'<div class="br"><span class="bt bt-filled">{icono("AddShoppingCart")}</span>'
           f'<span class="bt bt-tonal">{icono("Payments")}</span></div>'
           '<div class="cen bs" style="margin-top:-6px">Nueva venta · Entrada / salida de efectivo</div>')
    als = '<div class="als">' + alerta("Stock inventario bajo", 3, "bajo") + alerta("Stock insumo bajo", 1, "insb") + "</div>"
    return pantalla_inicio(tarjeta_t + acc + als)


def tabs_dlg(opciones, sel):
    return '<div class="tabs">' + "".join(f'<span class="{"sel" if o == sel else ""}">{e(o)}</span>' for o in opciones) + "</div>"


def p_caja_movimiento():
    cuerpo = (tabs_dlg(["Entrada", "Salida"], "Salida") + campo_ayuda("Importe (CUP) *", "300.00") +
              campo_ayuda("Motivo *", "Pago al proveedor de pan", "De 3 a 60 caracteres. No se borra: un error se corrige con el movimiento contrario."))
    return p_caja_turno() + dialogo("Entrada / salida de efectivo", cuerpo, "Check")


def p_caja_cierre():
    cuerpo = ('<div class="cen bm">Cuenta el efectivo de la caja. Se guarda un resumen del turno con el arqueo; no podrás vender hasta abrir otro.</div>'
              '<div class="sub-t">Efectivo esperado: 4,700.00 CUP</div>'
              '<div class="bs">Fondo 1,500.00 CUP + efectivo 3,300.00 CUP + entradas 200.00 CUP − salidas 300.00 CUP</div>'
              '<div style="display:flex;gap:8px;align-items:center"><div style="flex:1">' + campo_ayuda("Efectivo contado (CUP) *", "4650.00") + "</div>" +
              boton("Done", "text") + "</div>" +
              '<div class="bm" style="color:var(--error)">Faltante: 50.00 CUP</div>')
    return p_caja_turno() + dialogo("¿Cerrar el turno?", cuerpo, "Logout")


def turno_detalle(sel, cuerpo):
    tabs = '<div class="tabs">' + "".join(f'<span class="{"sel" if t == sel else ""}">{t}</span>' for t in ("Resumen", "Ventas 14", "Inventario 9", "Caja 2")) + "</div>"
    return barra_sup("Turno del 02/10/2026") + f'<div class="ct">{tabs}<div class="px" style="padding-top:8px;display:flex;flex-direction:column;gap:12px">{cuerpo}</div></div>'


def p_caja_detalle():
    arqueo = campos([("Fondo", "1,500.00 CUP"), ("Ventas en efectivo", "3,300.00 CUP"), ("Entradas", "200.00 CUP"), ("Salidas", "300.00 CUP"),
                     ("Esperado", "4,700.00 CUP"), ("Contado", "4,650.00 CUP")]) + \
        '<div class="kv"><div><span>Faltante</span><b style="color:var(--error)">50.00 CUP</b></div></div>'
    movs = (fila("Entrada 200.00 CUP · Cambio del banco", "02/10/2026 10:15 · Ana Pérez", "Payments", False) +
            fila("Salida 300.00 CUP · Pago al proveedor de pan", "02/10/2026 12:40 · Ana Pérez", "Payments", False))
    return turno_detalle("Caja 2", tarjeta("Arqueo de caja", arqueo) + '<div class="sub-t">Entradas y salidas de efectivo</div>' + movs)


def p_caja_empleado():
    cuerpo = ('<div class="cen bm">Cuenta el efectivo de la caja: el encargado verá la diferencia antes de aprobar. Mientras tanto puedes seguir vendiendo.</div>'
              '<div class="sub-t">Efectivo esperado: 2,150.00 CUP</div>'
              '<div class="bs">Fondo 500.00 CUP + efectivo 1,650.00 CUP + entradas 0.00 CUP − salidas 0.00 CUP</div>'
              '<div style="display:flex;gap:8px;align-items:center"><div style="flex:1">' + campo_ayuda("Efectivo contado (CUP) *", "2150.00") + "</div>" +
              boton("Done", "text") + "</div>" + '<div class="bm">Cuadra</div>')
    return pantalla_inicio(turno_card(True) + accesos(True)) + dialogo("¿Solicitar el cierre del turno?", cuerpo, "Logout")


def p_caja_aprobar():
    filas = (fila("Luis pide cerrar su turno", "Esperado 2,150 · Contado 2,100 · Faltante 50 CUP", "Storefront", False) +
             fila_botones(boton("Close", "tonal"), boton("Check", "filled")))
    perm = '<div class="sub-t" style="padding:8px 16px 0">Permisos</div>' + "".join(
        opcion(check(True), t, d) for t, d in (("Vender productos", "Ventas en efectivo y por transferencia"), ("Vender servicios", "Registrar servicios")))
    return barra_sup("Luis") + f'<div class="ct">{filas}<div class="px">{perm}</div></div>'


# ── 0.25.0: anular y modificar ventas ──

FICHA_VENTA = [("Pago", "Efectivo"), ("Vendedor", "Ana Pérez"), ("2 × Refresco de lata", "150.00 CUP c/u · 300.00 CUP"),
               ("1 × Galletas", "120.00 CUP c/u · 120.00 CUP"), ("Unidades", "3"), ("Total", "420.00 CUP")]


def p_venta_acciones():
    cuerpo = campos(FICHA_VENTA) + fila_botones(boton("Edit", "tonal"), boton("Delete", "tonal")) + \
        '<div class="cen bs" style="margin-top:-8px">Modificar venta · Anular venta (solo en la principal y con el turno abierto)</div>'
    return registros_fondo() + dialogo("Venta del 02/10/2026 09:20", cuerpo, "Share", "text")


def p_venta_anular():
    cuerpo = ('<div class="cen bm">La venta queda en Registros como ANULADA, deja de contar en los totales y en la caja, y se devuelven las existencias.</div>' +
              campo_ayuda("Motivo *", "El cliente devolvió los refrescos", "De 3 a 60 caracteres. Queda en Registros con la fecha y quién lo hizo."))
    return registros_fondo() + dialogo("Anular venta", cuerpo, "Delete", "error")


def linea_mod(nombre, precio, n):
    return (f'<div style="display:flex;align-items:center;gap:8px"><div style="flex:1"><div class="bm">{e(nombre)}</div>'
            f'<div class="bs">{e(precio)} c/u</div></div>{boton("Remove", "tonal")}<b style="font-size:16px;min-width:16px;text-align:center">{n}</b>{boton("Add", "tonal")}</div>')


def p_venta_modificar():
    cuerpo = ('<div class="cen bs">Cambia cantidades o quita líneas (con sus precios de entonces). Para añadir artículos o cambiar el método de pago, anula la venta y haz una nueva.</div>' +
              linea_mod("Refresco de lata", "150.00 CUP", 1) + linea_mod("Galletas", "120.00 CUP", 1) +
              '<div class="sub-t">Total: 270.00 CUP</div>' +
              campo_ayuda("Motivo *", "Era un solo refresco", "De 3 a 60 caracteres. Queda en Registros con la fecha y quién lo hizo."))
    return registros_fondo() + dialogo("Modificar venta", cuerpo, "Check")


def p_venta_marcas():
    tabs = '<div class="tabs"><span class="sel">Ventas</span><span>Transferencias</span><span>Movimientos</span><span>Turnos</span></div>'
    filas = (fila("02/10/2026 09:31", "Corrige #12\n270.00 CUP · Efectivo") + fila("02/10/2026 09:20", "ANULADA\n420.00 CUP · Efectivo") +
             fila("02/10/2026 09:05", "1,450.00 CUP · Transferencia") + fila("02/10/2026 08:47", "300.00 CUP · Efectivo"))
    snack = '<div class="snack">Venta corregida: 270.00 CUP.</div>'
    return barra_sup("Registros", atras=False) + f'<div class="ct">{tabs}{filas}</div>{snack}' + nav(3)


# ── 0.25.0: recordatorio de respaldo y actualizaciones ──

def p_aviso_respaldo():
    b = banner("info", "Último respaldo hace 32 días. Toca para hacer otro.", None, "KeyboardArrowRight", "Backup")
    return pantalla_inicio(f'<div class="px">{b}</div>' + turno_card(True) + accesos(True))


def tarjeta_act(texto, notas=None, progreso=None, desde=False, limite="03/11/2026"):
    # 0.26.0 (§6): fecha límite en seminegrita; «Más tarde» (reloj) también en la secundaria.
    n = (f'<div class="bm" style="font-weight:600">Obligatoria desde el {limite}.</div>' if limite else "") + \
        (f'<div class="bs" style="text-align:left">{e(notas)}</div>' if notas else "")
    if progreso is not None:
        p = (f'<div class="bs" style="text-align:left">Descargando la actualización… {progreso} %</div>'
             f'<div style="height:4px;border-radius:2px;background:var(--outVar)"><div style="height:4px;width:{progreso}%;border-radius:2px;background:var(--primary)"></div></div>')
        bts = fila_botones(boton("Close", "tonal"))
    else:
        p = ""
        bts = fila_botones(boton("FileDownload", "filled"), boton("Schedule", "tonal"))
    return (f'<div class="px"><div class="cd" style="background:var(--pc);color:var(--onPc);align-items:stretch">'
            f'<div class="bm">{e(texto)}</div>{n}{p}{bts}</div></div>')


def p_actualizacion():
    t = tarjeta_act("Versión 0.26.1 disponible (18.4 MB).", "Correcciones en el arqueo de caja y en la sincronización con las apps de empleados.")
    return pantalla_inicio(t + turno_card(True) + accesos(True))


def p_actualizacion_descarga():
    t = tarjeta_act("Versión 0.26.1 disponible (18.4 MB).", None, 62)
    return pantalla_inicio(t + turno_card(True) + accesos(True))


def p_actualizacion_secundaria():
    t = tarjeta_act("Versión 0.26.1 disponible desde la app principal (red local, sin gastar datos).", desde=True)
    return pantalla_inicio(t + turno_card(True) + accesos(True))


def p_ajustes_actualizaciones():
    filas = (fila("Respaldo", "Exportar o importar todos los datos", "Backup") + fila("Migrar a otro teléfono", "Licencia y datos al teléfono nuevo", "PhonelinkSetup") +
             fila("Actualizaciones", "Versión 0.26.0", "Sync") + fila("Ayuda", "Manual breve de uso", "Help") +
             fila("Soporte", "Datos de contacto del desarrollador", "SupportAgent"))
    cuerpo = ('<div class="bm">Una vez por semana, al abrir la app, mira en GitHub si hay una versión nueva. No envía ningún dato. '
              'Las versiones nuevas son obligatorias: puedes aplazarlas hasta 30 días.</div>' + fila_botones(boton("Sync", "filled")) +
              '<div class="cen bs" style="margin-top:-8px">Buscar ahora</div>')
    fondo = barra_sup("Ajustes", atras=False) + f'<div class="ct">{filas}</div>' + nav(4)
    s = dialogo("Actualizaciones", cuerpo, None)
    return fondo + s


# ── 0.25.0: recuperar la licencia ──

TEXTO_RECUPERACION = ("Recuperar licencia SPVI\nNombre: María Pérez González\nCarné de identidad: 85010112345\nTeléfono: +5352345678\n"
                      "Licencia anterior: 7c9e6679-7425-40de-944b-e07fc1f90ae7\n\n" + SOLICITUD_EJEMPLO)


def p_licencia_recuperar():
    tipos = "".join(opcion(radio(t == "Anual"), t, pr) for t, pr in
                    (("Mensual", "6,000.00 CUP"), ("Semestral", "30,000.00 CUP"), ("Anual", "50,000.00 CUP"), ("Perpetua", "90,000.00 CUP")))
    rec = ('<div class="cen bs">¿Ya tenías licencia en otro teléfono?</div>' +
           campo_ayuda("ID de la licencia anterior (opcional)", "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                       "Escribe o pega el ID de tu licencia anterior: el desarrollador la pasa a este teléfono gratis, con el mismo vencimiento y secundarias. El teléfono anterior se bloquea, borra sus datos y pide desinstalar SPVI."))
    return (barra_sup("Licencia") + '<div class="ct" style="padding:8px 16px;gap:12px">' +
            banner("critico", "Periodo de prueba terminado", "Activa una licencia para seguir vendiendo") +
            '<div class="cen bs">Paso 2 de 3 · Pide la licencia</div>' +
            tarjeta("Solicitar licencia", f'<div class="izq">{tipos}</div><div class="izq form">{rec}</div>') +
            fila_botones(boton("Send", "filled")) + "</div>")


def p_recuperacion_whatsapp():
    return chat("Ronnie · +53 5181 5604", burbuja(TEXTO_RECUPERACION, True, None, f"Solo texto · {len(TEXTO_RECUPERACION)} caracteres"),
                "GL comprueba el ID y el carné, revoca la anterior y responde con la licencia (gratis, mismo vencimiento).")


def p_compartir_licencia():
    hoja = ('<div class="scrim"></div><div style="position:absolute;left:0;right:0;bottom:0;background:var(--contHigh);border-radius:24px 24px 0 0;'
            'padding:16px 16px 40px;display:flex;flex-direction:column;gap:12px">'
            '<div class="cen bs">Compartir con</div><div style="display:flex;justify-content:space-around">' +
            "".join(f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px"><div style="width:52px;height:52px;border-radius:50%;'
                    f'background:var(--surface);display:flex;align-items:center;justify-content:center">{x}</div><span class="bs">{t}</span></div>'
                    for x, t in ((f'<img src="{LOGO}" style="width:36px;height:36px;border-radius:8px">', "SPVI"), (icono("Sms"), "Mensajes"),
                                 (icono("ContentCopy"), "Copiar"), (icono("Share"), "Más"))) +
            '</div><div class="cen bs">Al elegir SPVI se abre Licencia y se activa sola: «Licencia activada».</div></div>')
    return chat("Ronnie · +53 5181 5604", burbuja(TEXTO_LICENCIA, False, None, "Mantén pulsado → Compartir"), "") + hoja


def p_licencia_transferida():
    cuerpo = (vacio("Licencia transferida", "La licencia de SPVI se recuperó en otro teléfono. Por seguridad, aquí se borraron los datos "
                    "del negocio y la app quedó bloqueada. Puedes desinstalarla.") + fila_botones(boton("Delete", "filled")) +
              '<div class="cen bs" style="margin-top:-8px">Desinstalar SPVI (Android pide confirmarlo)</div>')
    return barra_sup("SPVI", logo=True) + f'<div class="ct" style="justify-content:center">{cuerpo}</div>'



# ── 0.25.1 ──

def hoja_inferior(titulo, cuerpo):
    return ('<div class="scrim"></div><div style="position:absolute;left:0;right:0;bottom:0;background:var(--contHigh);border-radius:24px 24px 0 0;'
            'padding:10px 16px 36px;display:flex;flex-direction:column;gap:8px"><div style="align-self:center;width:32px;height:4px;border-radius:2px;background:var(--outline);opacity:.5"></div>'
            f'<div style="font-size:16px;font-weight:500;padding:4px 0">{e(titulo)}</div>{cuerpo}</div>')


def fila_hoja(titulo, sub, *ics):
    s = f'<div class="li-sub">{e(sub)}</div>' if sub else ""
    tr = "".join(f'<span class="li-ic" style="padding:8px">{icono(i)}</span>' for i in ics)
    return f'<div class="li" style="padding:6px 0"><div class="li-txt"><div class="li-t">{e(titulo)}</div>{s}</div>{tr}</div>'


def p_turno_compartir():
    arqueo = campos([("Fondo", "1,500.00 CUP"), ("Ventas en efectivo", "1,880.00 CUP"), ("Esperado", "3,280.00 CUP"), ("Contado", "3,250.00 CUP")])
    tabs = '<div class="tabs">' + "".join(f'<span class="{"sel" if t == "Resumen" else ""}">{t}</span>' for t in ("Resumen", "Ventas 5", "Inventario 6", "Caja 2")) + "</div>"
    fondo = (barra_sup("Turno del 03/10/2026", acciones=f'<span class="li-ic">{icono("Share")}</span>') +
             f'<div class="ct">{tabs}<div class="px" style="padding-top:8px">{tarjeta("Arqueo de caja", arqueo)}</div></div>')
    cuerpo = ('<div class="bs">Resumen, arqueo de caja, entradas y salidas de efectivo, 5 ventas e inventario.</div>' +
              fila_hoja("PDF", None, "Share", "FileDownload") + fila_hoja("Excel", None, "Share", "FileDownload") +
              '<div class="bs">Compartir: enviar a otra app · Descargar: guardar en el teléfono</div>')
    return fondo + hoja_inferior("Compartir turno", cuerpo)


def linea_venta(nombre, unidad, subtotal, n, aviso=None):
    sub = unidad + (f" · {aviso}" if aviso else "")
    return (f'<div class="cd" style="padding:10px 12px;gap:6px;align-items:stretch"><div style="display:flex;gap:8px"><div style="flex:1"><div class="bm" style="font-weight:500">{e(nombre)}</div>'
            f'<div class="bs" style="text-align:left">{e(sub)}</div></div><b class="bm">{e(subtotal)}</b></div>'
            f'<div style="display:flex;align-items:center;gap:8px">{boton("Remove", "tonal")}<div class="tf" style="width:70px;justify-content:center;padding:0"><span class="tf-v" style="text-align:center;width:100%">{n}</span></div>'
            f'{boton("Add", "tonal")}<span style="flex:1"></span>{boton("Delete", "text")}</div></div>')


def barra_modificar():
    return barra_sup("Modificar venta", acciones=boton("Check", "filled"))


TEXTO_MODIFICAR = ("Cambia cantidades, quita o añade artículos y elige el método de pago. Lo que ya estaba conserva su precio de "
                   "entonces; lo nuevo, el precio actual.")


def p_modificar_pantalla():
    cuerpo = (f'<div class="bs" style="text-align:left">{e(TEXTO_MODIFICAR)}</div>' +
              linea_venta("Refresco de lata", "150.00 CUP c/u", "150.00 CUP", 1) + linea_venta("Galletas", "120.00 CUP c/u", "240.00 CUP", 2) +
              linea_venta("Café · Paquete 250 g", "400.00 CUP c/u", "400.00 CUP", 1) +
              fila_botones(f'<span class="btn2">{icono("AddShoppingCart", 18)}Añadir artículo</span>') +
              '<div class="sub-t">Total estimado: 790.00 CUP</div>' + chips(["Efectivo", "Transferencia"], "Efectivo") +
              campo_ayuda("Motivo *", "Otro paquete de galletas y café"))
    return barra_modificar() + f'<div class="ct"><div class="px form" style="padding-top:4px;gap:8px;display:flex;flex-direction:column">{cuerpo}</div></div>'


def p_modificar_buscar():
    res = (fila("Café · Paquete 250 g", "400.00 CUP", None, False, ) + fila("Café · Lata 500 g", "750.00 CUP", None, False))
    res = res.replace('</div></div></div>', '</div></div></div>')
    filas = "".join(f'<div class="li"><div class="li-txt"><div class="li-t">{e(t)}</div><div class="li-sub">{e(p)}</div></div>'
                    f'<span class="li-ic">{icono("Add")}</span></div>' for t, p in (("Café · Paquete 250 g", "400.00 CUP"), ("Café · Lata 500 g", "750.00 CUP")))
    cuerpo = (linea_venta("Refresco de lata", "150.00 CUP c/u", "150.00 CUP", 1) + linea_venta("Galletas", "120.00 CUP c/u", "240.00 CUP", 2) +
              campo_ayuda("Buscar por nombre o descripción", "café") + filas +
              fila_botones(f'<span class="btn2">{icono("Done", 18)}Terminar de añadir</span>') + '<div class="sub-t">Total estimado: 390.00 CUP</div>')
    return barra_modificar() + f'<div class="ct"><div class="px form" style="padding-top:4px;gap:8px;display:flex;flex-direction:column">{cuerpo}</div></div>'


def p_modificar_transferencia():
    cuerpo = (linea_venta("Refresco de lata", "150.00 CUP c/u", "300.00 CUP", 2) + '<div class="sub-t">Total estimado: 300.00 CUP</div>' +
              chips(["Efectivo", "Transferencia"], "Transferencia") + '<div class="sub-t" style="padding-top:8px">Datos de la transferencia</div>' +
              campo_ayuda("Nº de transacción *", "", error="Obligatorio", marcador="MM10040FEJ987") + campo_ayuda("Nombre y apellidos", "Carlos Díaz") +
              campo_ayuda("Motivo *", "Pagó por transferencia") +
              '<div class="bm" style="color:var(--error)">Completa los datos de la transferencia (el número de transacción es obligatorio).</div>')
    return barra_modificar() + f'<div class="ct"><div class="px form" style="padding-top:4px;gap:8px;display:flex;flex-direction:column">{cuerpo}</div></div>'


def p_licencia_vencida_recuperar():
    rec = ('<div class="cen bs">¿Ya tenías licencia en otro teléfono?</div>' +
           campo_ayuda("ID de la licencia anterior (opcional)", "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                       "Escribe o pega el ID de tu licencia anterior: el desarrollador la pasa a este teléfono gratis, con el mismo vencimiento y secundarias. El teléfono anterior se bloquea, borra sus datos y pide desinstalar SPVI."))
    estado = campos([("Estado", "Vencida"), ("Tipo", "Anual"), ("Venció", "30/09/2026")])
    return (barra_sup("Licencia") + '<div class="ct" style="padding:8px 16px;gap:12px">' +
            banner("critico", "Licencia vencida", "Renueva o recupera la licencia para seguir vendiendo") +
            tarjeta("Licencia de este teléfono", estado) + tarjeta("Solicitar licencia", f'<div class="izq form">{rec}</div>') +
            fila_botones(boton("Send", "filled")) + "</div>")


def p_conteo_dialogo():
    cuerpo = ('<div class="cen bm">El encargado pidió cerrar tu turno. Escribe el efectivo que hay; el turno se cierra al enviarlo.</div>'
              '<div class="sub-t">Efectivo esperado: 2,150.00 CUP</div>'
              '<div style="display:flex;gap:8px;align-items:center"><div style="flex:1">' + campo_ayuda("Efectivo contado (CUP) *", "") + "</div>" +
              boton("Done", "text") + "</div>")
    return pantalla_inicio(turno_card(True) + accesos(False)) + dialogo("Cuenta la caja para cerrar", cuerpo, "Send")


def p_conteo_pospuesto():
    b = banner("aviso", "El encargado pidió cerrar tu turno. Se cierra en cuanto no haya una venta en curso.",
               "Falta contar el efectivo. Toca para contarlo; sin conteo no se cierra.", "Payments")
    return pantalla_inicio(f'<div class="px">{b}</div>' + turno_card(True) + accesos(False) +
                           '<div class="cen bs" style="padding:0 24px">Vuelve a pedirlo al regresar a Inicio o a los 15 minutos</div>')


def p_inventario_exportar():
    fondo = barra_sup("Inventario", atras=False) + '<div class="ct">' + fila_foto("Cerveza Cristal · Lata 350 ml", "Bebidas · 24 u · 180.00 CUP") + \
        fila_foto("Galletas · Paquete 200 g", "Dulces · 40 u · 120.00 CUP") + "</div>" + nav(1)
    filas = "".join(fila_hoja(t, d, "Share") for t, d in (
        ("PDF", "Incluye costos"), ("Excel", "Incluye costos"),
        ("Imagen", "Para clientes (sin costos)"), ("Tarjetas", "Para clientes (sin costos)")))
    return fondo + hoja_inferior("Exportar", filas)



# ── 0.26.0 (P73) ──

def p_respaldo_026():
    tabs = '<div class="tabs"><span class="sel">Exportar</span><span>Importar</span></div>'
    cuerpo = ('<div class="wz-p">Paso 1 de 3</div><div class="sub-t" style="text-align:center">Crea una contraseña</div>'
              '<div class="bm">Guarda una copia cifrada con contraseña. Sin la contraseña nadie puede abrirla, ni siquiera el desarrollador.</div>'
              '<div class="bs">Se guarda todo: productos, insumos, inventario, turnos, ventas, perfil, teléfonos y cuentas, precios y preferencias.</div>' +
              campo_ayuda("Contraseña *", "••••••••••", "Mínimo 8 caracteres") + campo_ayuda("Repite la contraseña *", "••••••••••") +
              fila_botones(boton("ArrowForward", "filled")) +
              '<div class="cen bs">0.26.0: solo el archivo .spvi cifrado (ya no hay «Documento PDF»)</div>')
    return barra_sup("Respaldo") + f'<div class="ct">{tabs}<div class="px form" style="padding-top:8px">{cuerpo}</div></div>'


def p_fondo_pedir():
    cuerpo = '<div class="cen bm">Pide al encargado el fondo de caja. Sin él no se puede abrir el turno.</div>'
    return pantalla_inicio(turno_card(False) + accesos(False)) + dialogo("Abrir turno", cuerpo, "Send")


def p_fondo_pedido():
    cuerpo = '<div class="cen bm">Fondo pedido. Llegará al sincronizar, en cuanto el encargado lo asigne.</div>'
    snack = '<div class="snack">Fondo pedido al encargado.</div>'
    return pantalla_inicio(turno_card(False) + accesos(False)) + snack + dialogo("Abrir turno", cuerpo, None)


def p_fondo_inicio_principal():
    b1 = banner("aviso", "Luis pide abrir turno: asígnale el fondo", None, "KeyboardArrowRight")
    b2 = banner("info", "Actualiza la app de Ana", None, "KeyboardArrowRight")
    return pantalla_inicio(f'<div class="px" style="display:flex;flex-direction:column;gap:8px">{b1}{b2}</div>' +
                           turno_card(True, "Desde las 08:15") + accesos(True))


def p_fondo_ficha():
    fondo = (f'<div class="li li-d"><span class="li-ic">{icono("Payments")}</span><div class="li-txt"><div class="li-t">Fondo del próximo turno</div>'
             f'<div class="li-sub dos">Pide abrir turno · asígnale el fondo</div></div>{boton("Edit", "filled")}</div>')
    cobro = ('<div class="sub-t">Cobro por transferencia</div>' + campo_fijo("Teléfono del empleado", "5398 7654", "Phone"))
    cuerpo = fondo + permisos({0, 1, 4}) + cobro + fila_botones(boton("Devices"), boton("LinkOff"), extra="gap:32px;padding:0")
    emp = (fila("Ana", "Turno abierto desde las 08:30\\nConectada", "Devices") +
           fila("Luis", "Pide abrir turno · asígnale el fondo\\nConectada", "Devices", badge="1"))
    return lista_principal(emp) + dialogo("Luis", cuerpo, "Check")


def p_fondo_asignar():
    cuerpo = ('<div class="cen bm">Efectivo con el que empezará su próximo turno (puede ser 0). Sin él, su app no puede abrir turno.</div>' +
              campo_ayuda("Fondo de caja (CUP) *", "1200.00", "Propuesto: lo contado al cerrar el turno anterior."))
    return lista_principal() + dialogo("Asignar fondo: Luis", cuerpo, "Check")


def p_fondo_asignado():
    cuerpo = ('<div class="cen bm">El encargado asignó el fondo de caja de este turno.</div>' +
              campos([("Fondo de caja (CUP)", "1,200.00 CUP")]))
    return pantalla_inicio(turno_card(False) + accesos(False)) + dialogo("Abrir turno", cuerpo, "Check")


def bloqueo(secundaria):
    det = "La versión 0.26.1 es obligatoria desde el 03/11/2026. Hasta actualizar, SPVI no se puede usar. Tus datos no se tocan."
    extra = ('<div class="bs" style="text-align:center">Pon los dos teléfonos en la misma wifi y toca «Actualizar desde la principal».</div>'
             if secundaria else "")
    bts = (fila_botones(boton("FileDownload", "filled")) + ("" if secundaria else fila_botones(boton("Backup", "tonal"))) +
           fila_botones(boton("Storefront", "tonal")))
    nota = ("Actualizar desde la principal · Solicitar cierre del turno" if secundaria
            else "Actualizar · Exportar respaldo · Cerrar turno")
    return ('<div class="ct" style="justify-content:center;align-items:center;gap:16px;padding:24px;text-align:center">'
            f'<span class="pri">{icono("Lock", 40)}</span><div class="dlg-t">Actualiza SPVI para seguir</div>'
            f'<div class="bm">{e(det)}</div>{extra}{bts}<div class="bs">{e(nota)}</div></div>')


def p_bloqueo_principal():
    return bloqueo(False)


def p_bloqueo_secundaria():
    return bloqueo(True)


def p_inventario_esquina():
    tabs = '<div class="tabs"><span class="sel">Productos</span><span>Insumos</span></div>'
    filas = (fila_foto("Refresco de lata", "24 u · 150.00 CUP") + fila_foto("Galletas", "Sin existencia · 120.00 CUP") +
             fila_foto("Café molido 250 g", "8 u · 450.00 CUP", False) + fila_foto("Jabón de baño", "15 u · 90.00 CUP"))
    fab = f'<span class="fab" style="bottom:96px">{icono("Add")}</span>'
    return barra_sup("Inventario", atras=False) + f'<div class="ct">{tabs}{filas}</div>' + nav(1) + fab


def p_prueba_permiso():
    cuerpo = ('<div class="cen bm">SPVI guarda en Imágenes una imagen pequeña y cifrada con la fecha de inicio de tu periodo de prueba. '
              'Permite el acceso a las fotos para que SPVI la encuentre si reinstalas la app. No se abre ninguna otra foto.</div>')
    return pantalla_inicio(turno_card(False) + accesos(False)) + dialogo("Guardar tu periodo de prueba", cuerpo, "Backup")


def fila_dato(titulo, sub, valor):
    return (f'<div class="li"><div class="li-txt"><div class="li-t" style="font-weight:600">{e(titulo)}</div>'
            f'<div class="li-sub">{e(sub)}</div></div><span style="font-weight:600;font-size:14px">{e(valor)}</span></div>')


def p_seminegrita():
    tabs = '<div class="tabs"><span class="sel">Ventas</span><span>Servicios</span><span>Transf.</span><span>Turnos</span></div>'
    filas = (fila_dato("02/10/2026 09:20", "Efectivo · 3 u · Luis", "420.00 CUP") + fila_dato("02/10/2026 09:41", "Transferencia · 1 u · Ana", "150.00 CUP") +
             fila_dato("02/10/2026 10:05", "Efectivo · 5 u · Luis", "610.00 CUP") + fila_dato("02/10/2026 10:30", "Efectivo · 2 u · Ana", "240.00 CUP"))
    tot = '<div class="total"><div class="bs">Total</div><div class="tot">1,420.00 CUP</div></div>'
    return barra_sup("Registros", atras=False) + f'<div class="ct">{tabs}{tot}{filas}</div>' + nav(3)


PAGINAS = [
    ("Ajustes → «Apps vinculadas», debajo de Licencia (en la principal, con cuántas secundarias)", p_ajustes_principal),
    ("Apps vinculadas en la principal, sin secundarias: red local, «+» para agregar y «usar como secundaria»", p_principal_vacia),
    ("Apps vinculadas con dos empleados: turno abierto desde qué hora (0.20.0) y conectada o la última vez", lista_principal),
    ("+ → Agregar app de un empleado: nombre y permisos (0.21.0: todos marcados menos editar y precios)", p_agregar),
    ("✓ → Código QR de vinculación: vence en 10 minutos y sirve una sola vez", p_qr),
    ("Tocar un empleado: turno con «Pedir cierre», permisos y cobro; 0.21.0: el teléfono lo escribe el empleado", p_editar),
    ("Quitar la app de un empleado: las ventas que ya envió se quedan en la principal", p_quitar),
    ("En el teléfono del empleado: «Usar esta app como secundaria» → escanear el código", p_usar_secundaria),
    ("App secundaria: estado, «Sincronizar ahora», modo automático o manual y lo que puede hacer", p_secundaria),
    ("App secundaria sin conexión: sigue vendiendo y guarda los envíos pendientes", p_secundaria_sin_red),
    ("Ajustes en una secundaria: solo lo que le toca al empleado", p_ajustes_secundaria),
    ("Secundaria con la licencia de la principal sin activar: sincronizar o desvincular", p_bloqueo),
    ("Aviso del turno en la principal con las apps conectadas (un solo aviso)", "aviso"),
    # 0.20.0
    ("0.20.0 · «Pedir cierre» del turno de un empleado: nunca durante una venta", p_pedir_cierre),
    ("0.20.0 · Cierre pedido: se aplica cuando su app se conecte (o al terminar su venta en curso)", p_lista_cierre),
    ("0.20.0 · En la secundaria: aviso del cierre pedido; no se empiezan ventas nuevas", p_inicio_cierre),
    ("0.20.0 · Con una venta a medias, el cierre espera a que se confirme o se cancele", p_venta_cierre),
    ("0.20.0 · Turno cerrado por el encargado: aviso que se puede cerrar", p_inicio_cerrado),
    ("0.20.0 · Registros → ficha de una venta: «Vendedor» (0.26.0: la ficha ya no se comparte como texto)", p_ficha_vendedor),
    ("0.20.0 · Registros → Filtrar: «Vendedor» (solo con 2 o más personas que abrieron turnos)", p_filtro_vendedor),
    ("Inicio: alerta «Sin existencia» (cantidad 0 o menos); 0.21.1: contador relleno (0.21.1); «Stock insumo bajo» en petróleo (0.21.2)", p_negativos),
    ("Venta por transferencia en una secundaria: 0.21.0, el SMS llega al teléfono del empleado (va en su QR)", p_pegar_secundaria),
    ("0.20.0 · Migrar → paso 2 en la principal: sincroniza antes las apps de tus empleados", p_migrar),
    # 0.21.0
    ("0.21.0 · Primeros pasos: «Tipo de app». Solo hay una principal; cada empleado usa una secundaria", p_tour_tipo),
    ("0.21.0 · Primeros pasos: «Tu negocio». Lo que no marques no aparece (Ventas activa Inventario)", p_tour_objetivo),
    ("0.21.0 · Primeros pasos: «Empleados». Cuántas apps secundarias prevés (afecta al precio)", p_tour_empleados),
    ("0.21.0 · Secundaria: el empleado escribe su teléfono y escanea el código del dueño", p_tour_secundaria),
    ("0.21.0 · Licencia: apps secundarias y total (base + importe por cada secundaria)", p_licencia_secundarias),
    ("0.21.0 · Ajustes → Perfil: «Tu negocio» para activar o quitar Ventas, Inventario y Servicios", p_perfil_modulos),
    ("0.21.0 · Inicio de la principal: solicitudes de cierre; «Sin existencia» relleno junto a «Stock crítico» (0.21.1)", p_inicio_solicitud),
    ("0.21.0 · Apps vinculadas → empleado que pide cerrar: Rechazar (✕) o Aprobar (✓)", p_aprobar_cierre),
    ("0.21.0 · Secundaria: el interruptor del turno solicita el cierre; nada se cierra solo", p_solicitar_cierre),
    ("0.21.0 · Inicio de la secundaria: turno, Nueva venta y alertas; cierre pendiente", p_secundaria_solicitada),
    ("0.21.0 · Registros → Movimientos: quién hizo cada uno (también en Excel/PDF)", p_movimientos_hecho),
    # 0.22.0 / 0.23.0
    ("0.23.1 · Licencia → Activar: solo Pegar y Activar (sin QR); «Renovar igual» con el precio", p_licencia_activar_qr),
    ("0.23.1 · Renovar igual por WhatsApp o SMS: solo texto; renovar antes de que venza no pierde días", p_licencia_renovar),
    ("0.23.1 · Solicitud por WhatsApp: datos legibles, renglón en blanco y la solicitud cifrada SPVIR1: (solo texto)", p_solicitud_whatsapp),
    ("0.23.1 · Respuesta de GL por WhatsApp: características, renglón en blanco y la licencia cifrada SPVI2: (solo texto)", p_licencia_whatsapp),
    ("0.23.0 · Licencia por SMS: el mismo texto; solo cuenta el código SPVI2: (también si llega partido)", p_licencia_sms),
    # 0.24.0
    ("0.24.0 · Mismo nombre: la Descripción es obligatoria y debe diferenciarlo (máx. 40 caracteres, una línea)", p_descripcion_form),
    ("0.24.0 · Inventario: «Nombre · Descripción» en todas partes; filtro y aviso «Nombre repetido»", p_descripcion_lista),
    # 0.25.0
    ("0.25.0 · Abrir turno: fondo de caja obligatorio (puede ser 0), propuesto con lo contado en el turno anterior", p_caja_fondo),
    ("0.25.0 · Turno abierto: icono del billete para registrar entradas y salidas de efectivo", p_caja_turno),
    ("0.25.0 · Entrada / salida de efectivo: importe y motivo (no se borra; se corrige con el contrario)", p_caja_movimiento),
    ("0.25.0 · Cerrar turno: esperado = fondo + efectivo + entradas − salidas; contado, «Cuadra» y diferencia en vivo", p_caja_cierre),
    ("0.25.0 · Registros → Turnos → detalle: pestaña «Caja» con el arqueo y los movimientos de efectivo", p_caja_detalle),
    ("0.25.0 · Secundaria: al solicitar el cierre, el empleado cuenta la caja", p_caja_empleado),
    ("0.25.0 · Principal: antes de aprobar ve «Esperado · Contado · Faltante» del empleado", p_caja_aprobar),
    ("0.25.0 · Ficha de una venta de un turno abierto (solo en la principal): Modificar y Anular", p_venta_acciones),
    ("0.25.0 · Anular venta: motivo obligatorio; sale de totales y caja y devuelve las existencias", p_venta_anular),
    ("0.25.0 · Registros: la original queda «ANULADA» y la nueva dice «Corrige #N»", p_venta_marcas),
    ("0.25.0 · Inicio de la principal: recordatorio tras 30 días sin respaldo; al tocarlo abre Respaldo", p_aviso_respaldo),
    ("0.25.0 · Inicio: versión nueva en GitHub (semanal, sin enviar datos); 0.26.0: obligatoria en 30 días, «Más tarde»", p_actualizacion),
    ("0.25.0 · Descargando: se comprueba el SHA-256 antes de instalar; Android pide confirmar", p_actualizacion_descarga),
    ("0.25.0 · Secundaria: la actualización llega desde la app principal por la red local", p_actualizacion_secundaria),
    ("0.25.0 · Ajustes → Actualizaciones: «Buscar ahora» (0.26.0: la consulta semanal ya no se desactiva)", p_ajustes_actualizaciones),
    ("0.25.0 · Licencia en un teléfono nuevo: ID de la licencia anterior ya escrito desde el respaldo", p_licencia_recuperar),
    ("0.25.0 · Solicitud de recuperación por WhatsApp: «Licencia anterior» + código cifrado", p_recuperacion_whatsapp),
    ("0.25.0 · Compartir la respuesta de GL con SPVI: se activa sola", p_compartir_licencia),
    ("0.25.0 · Teléfono antiguo: «Licencia transferida», datos borrados y Desinstalar SPVI", p_licencia_transferida),
    # 0.25.1
    ("0.25.1 · Registros → Turnos → detalle → Compartir: el turno completo en PDF o Excel (enviar o guardar)", p_turno_compartir),
    ("0.25.1 · Modificar venta a pantalla completa: cantidades, quitar, añadir artículos, método de pago y motivo", p_modificar_pantalla),
    ("0.25.1 · Modificar venta → Añadir artículo: búsqueda por nombre o descripción (precio actual)", p_modificar_buscar),
    ("0.25.1 · Modificar venta → Transferencia: datos de la transferencia obligatorios (nº de transacción)", p_modificar_transferencia),
    ("0.25.1 · Licencia vencida: también se puede pedir la recuperación desde otro teléfono", p_licencia_vencida_recuperar),
    ("0.25.1 · Secundaria: conteo de la caja pedido por el encargado; ✕ lo pospone (TalkBack: «Ahora no»)", p_conteo_dialogo),
    ("0.25.1 · Tras «Ahora no»: el aviso ofrece «Contar»; sin conteo el turno no se cierra", p_conteo_pospuesto),
    ("0.25.1 · Inventario → Exportar: cada formato dice si es de uso interno (con costos) o para clientes; 0.26.0: sin Texto", p_inventario_exportar),
    # 0.26.0
    ("0.26.0 · Respaldo: solo el archivo .spvi cifrado con contraseña (se quitó el «Documento PDF»)", p_respaldo_026),
    ("0.26.0 · «+» abajo a la derecha en Inventario, Servicios y Precios", p_inventario_esquina),
    ("0.26.0 · Seminegrita en importes, precios, fechas y totales (también en PDF; negrita en Excel)", p_seminegrita),
    ("0.26.0 · Secundaria sin fondo asignado: «Pedir fondo» (el fondo de cada turno lo pone el encargado)", p_fondo_pedir),
    ("0.26.0 · Secundaria: fondo pedido; llegará al sincronizar cuando el encargado lo asigne", p_fondo_pedido),
    ("0.26.0 · Inicio de la principal: «Luis pide abrir turno» y «Actualiza la app de Ana» (app 0.25.x)", p_fondo_inicio_principal),
    ("0.26.0 · Apps vinculadas → Luis: «Fondo del próximo turno» con Asignar fondo (✎)", p_fondo_ficha),
    ("0.26.0 · Asignar fondo: puede ser 0; propone lo contado al cerrar su último turno", p_fondo_asignar),
    ("0.26.0 · Secundaria: abre el turno con el fondo asignado, sin poder cambiarlo (sirve para un turno)", p_fondo_asignado),
    ("0.26.0 · Plazo de 30 días vencido: bloqueo con Actualizar, Exportar respaldo y Cerrar turno", p_bloqueo_principal),
    ("0.26.0 · Bloqueo en la secundaria: actualizar desde la principal o solicitar el cierre del turno", p_bloqueo_secundaria),
    ("0.26.0 · Recién instalada: pide una vez el acceso a fotos para que la prueba no se reinicie al reinstalar", p_prueba_permiso),
]

CSS = """
@font-face{font-family:Orbitron;src:url(%(orb)s)}
@page{size:842.88pt 595.92pt;margin:0}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:1123.84px;height:794.56px;font-family:Roboto,sans-serif;background:#fff;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.pg{width:1123.84px;height:794.56px;position:relative;overflow:hidden}
.ttl{position:absolute;top:36px;left:0;right:0;text-align:center;font-size:16px;font-weight:500;color:#1A1C1E;padding:0 40px}
.ph{position:absolute;top:62px;display:flex;flex-direction:column;align-items:center}
.ph .lbl{font-size:11px;color:#444;margin-top:8px}
.fr{width:360px;height:800px;zoom:.81;border-radius:22px;overflow:hidden;position:relative;box-shadow:0 2px 10px rgba(0,0,0,.25);
    background:var(--bg);color:var(--on);display:flex;flex-direction:column}
.sb{height:28px;display:flex;justify-content:space-between;align-items:center;padding:0 18px;font-size:13px;font-weight:500;flex:none}
.sbr{display:flex;gap:6px;align-items:center}
.tb{height:64px;display:flex;align-items:center;gap:16px;padding:0 16px;flex:none}
.tbi{display:flex}
.tbt{font-size:20px;font-weight:500}
.tbt.marca{font-family:Orbitron;letter-spacing:2px;font-size:21px}
.tblogo{width:32px;height:32px;border-radius:8px}
.ct{flex:1;overflow:hidden;display:flex;flex-direction:column;gap:8px;position:relative}
.ct.centro{justify-content:center;gap:16px;padding:16px}
.li{display:flex;align-items:center;gap:16px;min-height:56px;padding:8px 16px}
.li-ic{display:flex;color:var(--onVar)}
.li-txt{flex:1;min-width:0}
.li-t{font-size:16px;line-height:22px}
.li-sub{font-size:13px;color:var(--onVar);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:18px}
.ct > .li{margin:-4px 0}
.resalta{outline:2px dashed var(--primary);outline-offset:-3px;border-radius:12px}
.badge{background:var(--error);color:var(--onError);font-size:11px;border-radius:8px;padding:1px 6px}
.intro{font-size:14px;line-height:20px;text-align:center;padding:8px 16px 0}
.px{padding:0 16px}
.bn{display:flex;align-items:center;gap:16px;padding:8px 16px;border-radius:12px;min-height:56px}
.bn-info{background:var(--pc);color:var(--onPc)}
.bn-aviso{background:var(--sc);color:var(--onSc)}
.bn-txt{flex:1}
.bn-t{font-size:14px;font-weight:500;line-height:20px}
.bn-d{font-size:13px;line-height:18px}
.bn-a{width:48px;height:48px;display:flex;align-items:center;justify-content:center;margin-right:-8px}
.lt{font-size:14px;font-weight:500;text-align:center;padding-top:8px}
.cd{background:var(--surface);border-radius:16px;padding:16px;box-shadow:var(--sombra);display:flex;flex-direction:column;gap:8px;align-items:center}
.cd-t{font-size:16px;font-weight:500;text-align:center}
.cen{text-align:center;align-self:stretch;display:flex;justify-content:center}
.cen.bm,.cen.bs,.cen.bl{display:block}
.izq{align-self:stretch}
.bl{font-size:16px}.bm{font-size:14px;line-height:20px}.bs{font-size:13px;line-height:18px;color:var(--onVar)}
.br{display:flex;justify-content:center;gap:16px;padding:8px 0}
.bt{width:48px;height:48px;border-radius:50%%;display:flex;align-items:center;justify-content:center}
.bt-text{color:var(--primary)}
.bt-filled{background:var(--primary);color:var(--onPrimary)}
.bt-tonal{background:var(--tc);color:var(--onTc)}
.bt-error{background:var(--error);color:var(--onError)}
.es{display:flex;flex-direction:column;align-items:center;gap:8px;padding:24px 24px 8px;text-align:center}
.es-logo{width:72px;height:72px;border-radius:18px}
.es-t{font-size:16px;font-weight:500}
.es-d{font-size:14px;line-height:20px;color:var(--onVar)}
.fab{position:absolute;bottom:40px;right:16px;width:56px;height:56px;border-radius:16px;background:var(--primary);
     color:var(--onPrimary);display:flex;align-items:center;justify-content:center;box-shadow:0 3px 6px rgba(0,0,0,.3)}
.nv{height:80px;flex:none;background:var(--cont);display:flex;justify-content:space-around;align-items:center;padding-bottom:12px}
.nv-i{width:64px;height:32px;border-radius:16px;display:flex;align-items:center;justify-content:center;color:var(--onVar)}
.nv-i.sel{background:var(--primary);color:var(--onPrimary)}
.gh{position:absolute;bottom:8px;left:50%%;width:124px;height:4px;margin-left:-62px;border-radius:2px;background:var(--onVar);opacity:.7}
.scrim{position:absolute;inset:0;background:rgba(0,0,0,.4)}
.dlg{position:absolute;left:20px;right:20px;top:50%%;transform:translateY(-50%%);background:var(--contHigh);border-radius:24px;padding:20px 20px 12px;
     display:flex;flex-direction:column;gap:10px;align-items:center;max-height:700px}
.dlg-logo{width:40px;height:40px;border-radius:10px}
.dlg-t{font-size:22px;line-height:28px;text-align:center}
.dlg-c{align-self:stretch;display:flex;flex-direction:column;gap:8px}
.dlg-f{display:flex;gap:16px;justify-content:center;padding-top:4px}
.sub-t{font-size:14px;font-weight:500}
.op{display:flex;gap:8px;align-items:center;padding:3px 0}
.op-t{font-size:16px;line-height:21px}.op-d{font-size:13px;line-height:17px;color:var(--onVar)}
.chk{width:18px;height:18px;border:2px solid var(--onVar);border-radius:3px;flex:none;margin:0 11px;display:flex;align-items:center;justify-content:center}
.chk.on{background:var(--primary);border-color:var(--primary);color:var(--onPrimary)}
.rad{width:20px;height:20px;border:2px solid var(--onVar);border-radius:50%%;flex:none;margin:0 10px;display:flex;align-items:center;justify-content:center}
.rad.on{border-color:var(--primary)}.rad.on i{width:10px;height:10px;border-radius:50%%;background:var(--primary)}
.tf{position:relative;border:1px solid var(--outline);border-radius:12px;height:56px;display:flex;align-items:center;padding:0 12px 0 16px;margin-top:6px}
.tf-l{position:absolute;top:-8px;left:12px;font-size:12px;padding:0 4px;background:var(--contHigh);color:var(--onVar)}
.tf-v{flex:1;font-size:16px}.tf-x{display:flex;color:var(--onVar)}
.lock{position:absolute;inset:0;background:linear-gradient(170deg,#24475f 0%%,#4d6b66 40%%,#8b7650 100%%);color:#fff}
.sb-lock{color:#fff}.sb-ic{width:14px;height:14px;vertical-align:-2px;margin-left:4px}
.lk-h{font-size:44px;font-weight:300;padding:16px 18px 0}
.lk-d{font-size:15px;padding:4px 18px 16px;opacity:.9}
.nt{margin:0 12px;background:var(--notif);color:var(--on);border-radius:24px;padding:16px;display:flex;gap:14px;align-items:center;box-shadow:0 2px 6px rgba(0,0,0,.25)}
.nt-ic{width:40px;height:40px;border-radius:50%%;background:#2B4FA3;display:flex;align-items:center;justify-content:center;flex:none}
.nt-ic img{width:24px;height:24px}
.nt-app{font-size:12px;color:var(--onVar)}.nt-t{font-size:15px;font-weight:600}.nt-x{font-size:14px;color:var(--onVar);line-height:19px}
.li-sub.dos{white-space:normal}
.tf{flex:none}.tf-v{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.dlg-c{min-height:0;overflow:hidden;flex:0 1 auto}.dlg{max-height:744px}.dlg-f,.dlg-logo,.dlg-t{flex:none}
.li-d{padding:4px 0;min-height:48px}
.tf-ic{display:flex;color:var(--onVar);margin-right:12px}
.snack{position:absolute;left:12px;right:12px;bottom:110px;background:var(--on);color:var(--bg);border-radius:8px;padding:14px 16px;font-size:14px;line-height:20px}
.cd-fila{flex-direction:row;align-items:center;gap:16px;padding:12px 16px}
.pri{color:var(--primary)}
.sw{width:52px;height:32px;border-radius:16px;border:2px solid var(--outline);position:relative;flex:none}
.sw i{position:absolute;width:16px;height:16px;border-radius:50%%;background:var(--outline);left:6px;top:6px}
.sw.on{background:var(--primary);border-color:var(--primary)}.sw.on i{width:24px;height:24px;left:22px;top:2px;background:var(--onPrimary)}
.br.off .bt{opacity:.38;filter:grayscale(1)}
.als{display:flex;gap:8px;padding:0 16px}
.al{flex:1;border-radius:16px;padding:12px 16px;display:flex;flex-direction:column;gap:2px;background:var(--surface);box-shadow:var(--sombra)}
.al .al-t{color:var(--onVar)}.al-crit .al-n{color:var(--aCrit)}.al-bajo .al-n{color:var(--aBajo)}
.al-ins .al-n{color:var(--aIns)}.al-insb .al-n{color:var(--aInsB)}
.al-lleno{background:var(--error);color:var(--onError);box-shadow:none}.al-lleno .al-t{color:var(--onError)}
.al-n{font-size:24px;font-weight:600}.al-t{font-size:13px;line-height:17px}
.total{text-align:center;padding:4px 0}.tot{font-size:28px;font-weight:600}
.tabs{display:flex;justify-content:space-around;border-bottom:1px solid var(--outVar);font-size:14px;font-weight:500;color:var(--onVar)}
.tabs span{padding:12px 4px}.tabs .sel{color:var(--primary);border-bottom:3px solid var(--primary)}
.kv{display:flex;flex-direction:column;gap:6px}
.kv div{display:flex;justify-content:space-between;gap:12px;font-size:14px;line-height:19px}
.kv span{color:var(--onVar)}.kv b{font-weight:600;text-align:right}
.chips{display:flex;flex-wrap:wrap;gap:8px}
.chip{display:inline-flex;align-items:center;gap:6px;height:32px;padding:0 12px;border:1px solid var(--outline);border-radius:8px;font-size:14px}
.chip.on{background:var(--sc);color:var(--onSc);border-color:transparent}
.dos-col{display:flex;gap:8px}.dos-col .tf{flex:1}
.btn2{display:inline-flex;align-items:center;gap:8px;height:40px;padding:0 20px;border:1px solid var(--outline);border-radius:20px;color:var(--primary);font-size:14px;font-weight:500}
.form{display:flex;flex-direction:column;gap:8px}.form .tf-l{background:var(--bg)}
.form.fe{align-self:stretch}.form.fe .tf-l{background:var(--surface)}.ancho{align-self:stretch}.bt.off{opacity:.38}
.thumb{width:40px;height:40px;border-radius:8px;flex:none;background:linear-gradient(135deg,var(--pc),var(--sc))}
.thumb.v{background:var(--cont);display:flex;align-items:center;justify-content:center;color:var(--onVar)}
.stp{display:flex;align-items:center;justify-content:center;gap:24px;padding:8px 0}.stp b{font-size:22px;font-weight:500;min-width:120px;text-align:center}
.wz{padding:8px 24px;display:flex;flex-direction:column;gap:12px;flex:1}
.wz-p{font-size:13px;color:var(--onVar);text-align:center}.wz-t{font-size:24px;text-align:center}
.wzbtn{align-self:center;height:40px;padding:0 24px;border-radius:20px;background:var(--primary);color:var(--onPrimary);display:flex;align-items:center;font-size:14px;font-weight:500}
.opc{border:1px solid var(--outVar);border-radius:16px;padding:8px}.opc.on{border-color:var(--primary);background:var(--pc);color:var(--onPc)}
.opc.on .op-d{color:var(--onPc)}.op.off{opacity:.6}
.lk-n{font-size:13px;line-height:19px;text-align:center;padding:16px 24px;opacity:.92}
"""


def telefono(tema, cuerpo, aviso=False):
    t = TEMAS[tema]
    vars_ = ";".join(f"--{k}:{v}" for k, v in t.items())
    sb = "" if aviso else barra_estado()
    return f'<div class="fr" style="{vars_}">{sb}{cuerpo}<div class="gh"></div></div>'


def pagina(n, titulo, fn):
    caras = []
    for tema, etiqueta in (("claro", "Claro"), ("oscuro", "Oscuro")):
        cuerpo = p_aviso(tema) if fn == "aviso" else fn()
        caras.append((telefono(tema, cuerpo, fn == "aviso"), etiqueta))
    ancho = 360 * .81
    x1, x2 = 1123.84 / 2 - ancho - 24, 1123.84 / 2 + 24
    fones = "".join(f'<div class="ph" style="left:{x}px">{h}<div class="lbl">{l}</div></div>'
                    for (h, l), x in zip(caras, (x1, x2)))
    return f'''<!doctype html><html lang="es"><head><meta charset="utf-8"><style>{CSS % {"orb": ORBITRON}}</style></head>
<body><div class="pg"><div class="ttl">{n}. {e(titulo)}</div>{fones}</div></body></html>'''


if __name__ == "__main__":
    salida = sys.argv[1]
    inicio = int(sys.argv[2]) if len(sys.argv) > 2 else 25
    os.makedirs(salida, exist_ok=True)
    for i, (titulo, fn) in enumerate(PAGINAS):
        open(os.path.join(salida, f"p{i + 1:02d}.html"), "w").write(pagina(inicio + i, titulo, fn))
    print(len(PAGINAS), "páginas en", salida)
