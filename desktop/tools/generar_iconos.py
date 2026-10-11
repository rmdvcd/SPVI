"""Genera ``spvi_web/static/icons.svg`` (sprite) a partir de ``SpviIcons.kt`` de la app móvil.

Uso: ``python tools/generar_iconos.py`` desde ``desktop/``. Cada nombre de ``SpviIcons`` (Inicio, Venta, Ajustes…)
queda como ``<symbol id="Inicio">``: la web usa exactamente los mismos glifos que el móvil. Se añaden unos pocos
iconos Material «Filled» (Apache 2.0, © Google) propios del escritorio: menú, tema, apagado y cronómetro.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'designsystem/src/main/kotlin/cu/spvi/designsystem/icon/SpviIcons.kt'
TARGET = Path(__file__).resolve().parents[1] / 'spvi_web/static/icons.svg'
EXTRA = {
    'Menu': 'M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z',
    'TemaOscuro': 'M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z',
    'Apagar': 'M13 3h-2v10h2V3zm4.83 2.17-1.42 1.42C17.99 7.86 19 9.81 19 12c0 3.87-3.13 7-7 7s-7-3.13-7-7c0-2.19 1.01-4.14 2.58-5.42L6.17 5.17C4.23 6.82 3 9.26 3 12c0 4.97 4.03 9 9 9s9-4.03 9-9c0-2.74-1.23-5.18-3.17-6.83z',
    'Cronometro': 'M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61 1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42A8.962 8.962 0 0 0 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z',
    'AtrasPagina': 'M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z',
    'Ver': 'M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z',
    'Informacion': 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z',
}


def build():
    """Contenido del sprite y número de iconos (sin escribir nada)."""
    text = SOURCE.read_text(encoding='utf-8')
    paths = {}
    for match in re.finditer(r'icono\("(\w+)",((?:\s*"[^"]*",?)+)', text):
        paths[match.group(1)] = re.findall(r'"([^"]*)"', match.group(2))
    whatsapp = ''.join(re.findall(r'"([^"]*)"', text.split('WHATSAPP_PATH =', 1)[1].split('\n}', 1)[0]))
    aliases = {}
    for name, target in re.findall(r'val (\w+): ImageVector get\(\) = (?:MaterialSolido\.)?(\w+)', text):
        aliases[name] = target
    symbols = {}
    for name, target in aliases.items():
        while target in aliases and target not in paths: target = aliases[target]
        if target in paths: symbols[name] = paths[target]
    symbols['WhatsApp'] = [whatsapp]
    symbols.update({k: [v] for k, v in EXTRA.items()})
    body = ''.join('<symbol id="%s" viewBox="0 0 24 24">%s</symbol>' % (name, ''.join('<path d="%s"/>' % d for d in ds))
                   for name, ds in sorted(symbols.items()))
    return '<svg xmlns="http://www.w3.org/2000/svg">' + body + '</svg>\n', len(symbols)


def main():
    content, count = build()
    TARGET.write_text(content, encoding='utf-8')
    print('%d iconos → %s' % (count, TARGET))


if __name__ == '__main__':
    main()
