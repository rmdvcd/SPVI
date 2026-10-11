"""Inicio con el sistema: valor «SPVI» en HKCU\\...\\Run (Windows) o un .desktop de XDG (desarrollo en Linux).

No requiere permisos de administrador ni modifica nada fuera del perfil del usuario.
"""
import os
import sys
from pathlib import Path

NAME = 'SPVI'
RUN_KEY = r'Software\Microsoft\Windows\CurrentVersion\Run'


def command():
    """Orden que se registra: el .exe empaquetado o el intérprete con este lanzador, con ``--autostart``."""
    if getattr(sys, 'frozen', False):
        return '"%s" --autostart' % sys.executable
    launcher = Path(__file__).resolve().parent.parent / 'launcher.py'
    return '"%s" "%s" --autostart' % (sys.executable, launcher)


def _xdg():
    return Path(os.environ.get('XDG_CONFIG_HOME', Path.home() / '.config')) / 'autostart' / 'spvi.desktop'


def supported(): return os.name == 'nt' or sys.platform.startswith('linux')


def enabled():
    if os.name == 'nt':
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as key:
                winreg.QueryValueEx(key, NAME)
                return True
        except OSError:
            return False
    return _xdg().is_file()


def apply(on):
    """Activa o desactiva el inicio con el sistema. Lanza ``ValueError`` si no se puede."""
    try:
        if os.name == 'nt':
            import winreg
            with winreg.CreateKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as key:
                if on: winreg.SetValueEx(key, NAME, 0, winreg.REG_SZ, command())
                else:
                    try: winreg.DeleteValue(key, NAME)
                    except FileNotFoundError: pass
        elif sys.platform.startswith('linux'):
            path = _xdg()
            if on:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('[Desktop Entry]\nType=Application\nName=SPVI\nExec=%s\nX-GNOME-Autostart-enabled=true\n'
                                % command(), encoding='utf-8')
            elif path.exists(): path.unlink()
        else:
            raise ValueError('El inicio automático no está disponible en este sistema.')
    except OSError as error:
        raise ValueError('No se pudo cambiar el inicio automático.') from error
