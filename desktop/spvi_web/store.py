"""Libro local en centavos. Cada operación de caja se confirma en una transacción."""
import sqlite3
from contextlib import contextmanager
from decimal import Decimal, InvalidOperation


SCHEMA = """
PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS items (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('producto','servicio')),
 price INTEGER NOT NULL CHECK(price>=0), cost INTEGER NOT NULL CHECK(cost>=0),
 stock INTEGER NOT NULL CHECK(stock>=0), minimum INTEGER NOT NULL CHECK(minimum>=0));
CREATE TABLE IF NOT EXISTS shifts (
 id INTEGER PRIMARY KEY, opened TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
 closed TEXT, fund INTEGER NOT NULL CHECK(fund>=0), counted INTEGER, expected INTEGER);
CREATE UNIQUE INDEX IF NOT EXISTS one_open_shift ON shifts((1)) WHERE closed IS NULL;
CREATE TABLE IF NOT EXISTS sales (
 id INTEGER PRIMARY KEY, shift_id INTEGER NOT NULL REFERENCES shifts(id),
 item_id INTEGER NOT NULL REFERENCES items(id), name TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity>0),
 price INTEGER NOT NULL, cost INTEGER NOT NULL, method TEXT NOT NULL CHECK(method IN ('efectivo','transferencia')),
 created TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')));
CREATE TABLE IF NOT EXISTS movements (
 id INTEGER PRIMARY KEY, shift_id INTEGER NOT NULL REFERENCES shifts(id), amount INTEGER NOT NULL,
 reason TEXT NOT NULL, created TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')));
PRAGMA user_version=1;
"""


def money(value):
    try:
        n = Decimal(str(value))
        if not n.is_finite() or n < 0 or n > 999999999 or n * 100 != (n * 100).to_integral_value():
            raise ValueError()
        return int(n * 100)
    except (InvalidOperation, ValueError, TypeError):
        raise ValueError('Importe no válido (máximo dos decimales).') from None


def integer(value, minimum=0):
    if isinstance(value, bool) or len(str(value)) > 9 or not str(value).isascii() or not str(value).isdigit():
        raise ValueError('Cantidad no válida.')
    n = int(value)
    if not minimum <= n <= 100000000:
        raise ValueError('Cantidad fuera de rango.')
    return n


def text(value, limit=120):
    if not isinstance(value, str) or not value.strip() or len(value.strip()) > limit:
        raise ValueError('Completa el texto requerido.')
    return value.strip()


class Store:
    def __init__(self, path):
        self.path = str(path)
        with self.connect() as db:
            version = db.execute('PRAGMA user_version').fetchone()[0]
            if version not in (0, 1):
                raise RuntimeError('Versión de datos no compatible. No se modificó el archivo.')
            db.executescript(SCHEMA)

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA foreign_keys=ON')
        try:
            with db:
                yield db
        finally:
            db.close()

    @staticmethod
    def opened(db):
        shift = db.execute('SELECT * FROM shifts WHERE closed IS NULL').fetchone()
        if shift is None:
            raise ValueError('Abre un turno para continuar.')
        return shift

    @staticmethod
    def expected(db, shift):
        cash = db.execute("SELECT COALESCE(SUM(price*quantity),0) FROM sales WHERE shift_id=? AND method='efectivo'", (shift['id'],)).fetchone()[0]
        moves = db.execute('SELECT COALESCE(SUM(amount),0) FROM movements WHERE shift_id=?', (shift['id'],)).fetchone()[0]
        return shift['fund'] + cash + moves

    def operate(self, action, data):
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            self.apply(db, action, data)

    def apply(self, db, action, data):
        if action == 'item':
            kind = data.get('kind')
            if kind not in ('producto', 'servicio'):
                raise ValueError('Tipo no válido.')
            db.execute('INSERT INTO items(name,kind,price,cost,stock,minimum) VALUES(?,?,?,?,?,?)', (
                text(data.get('name')), kind, money(data.get('price')), money(data.get('cost')),
                integer(data.get('stock', 0)) if kind == 'producto' else 0, integer(data.get('minimum', 0))))
        elif action == 'open':
            if db.execute('SELECT 1 FROM shifts WHERE closed IS NULL').fetchone():
                raise ValueError('Ya hay un turno abierto.')
            db.execute('INSERT INTO shifts(fund) VALUES(?)', (money(data.get('fund')),))
        elif action == 'sale':
            shift = self.opened(db)
            item = db.execute('SELECT * FROM items WHERE id=?', (integer(data.get('item_id'), 1),)).fetchone()
            if item is None:
                raise ValueError('Artículo no encontrado.')
            quantity = integer(data.get('quantity'), 1)
            method = data.get('method')
            if method not in ('efectivo', 'transferencia'):
                raise ValueError('Forma de pago no válida.')
            if item['kind'] == 'producto':
                if quantity > item['stock']:
                    raise ValueError('Existencias insuficientes.')
                db.execute('UPDATE items SET stock=stock-? WHERE id=?', (quantity, item['id']))
            db.execute('INSERT INTO sales(shift_id,item_id,name,quantity,price,cost,method) VALUES(?,?,?,?,?,?,?)',
                       (shift['id'], item['id'], item['name'], quantity, item['price'], item['cost'], method))
        elif action == 'movement':
            shift = self.opened(db)
            amount = money(data.get('amount'))
            direction = data.get('direction')
            if amount == 0 or direction not in ('entrada', 'salida'):
                raise ValueError('Movimiento no válido.')
            if direction == 'salida':
                amount = -amount
                if self.expected(db, shift) + amount < 0:
                    raise ValueError('Efectivo insuficiente.')
            db.execute('INSERT INTO movements(shift_id,amount,reason) VALUES(?,?,?)', (shift['id'], amount, text(data.get('reason'))))
        elif action == 'close':
            shift = self.opened(db)
            db.execute("UPDATE shifts SET closed=strftime('%Y-%m-%dT%H:%M:%SZ','now'),counted=?,expected=? WHERE id=?",
                       (money(data.get('counted')), self.expected(db, shift), shift['id']))
        else:
            raise ValueError('Operación no válida.')

    def dashboard(self, days=30):
        if days not in (7, 30, 90):
            raise ValueError('Período no válido.')
        period = (f'-{days} days',)
        with self.connect() as db:
            db.execute('BEGIN')
            shift = db.execute('SELECT * FROM shifts WHERE closed IS NULL').fetchone()
            result = dict(db.execute("""SELECT COUNT(*) AS operations, COALESCE(SUM(price*quantity),0) AS revenue,
                COALESCE(SUM((price-cost)*quantity),0) AS margin,
                COALESCE(SUM(CASE WHEN method='efectivo' THEN price*quantity ELSE 0 END),0) AS cash,
                COALESCE(SUM(CASE WHEN method='transferencia' THEN price*quantity ELSE 0 END),0) AS transfers
                FROM sales WHERE julianday(created)>=julianday('now',?)""", period).fetchone())
            previous = db.execute('''SELECT COALESCE(SUM(price*quantity),0) FROM sales
                WHERE julianday(created)>=julianday('now',?) AND julianday(created)<julianday('now',?)''',
                (f'-{days * 2} days', f'-{days} days')).fetchone()[0]
            result['previous_revenue'] = previous
            result['revenue_change_percent'] = round((result['revenue'] - previous) * 100 / previous, 1) if previous else None
            result['daily'] = [dict(row) for row in db.execute('''SELECT substr(created,1,10) AS day,
                SUM(price*quantity) AS revenue, SUM((price-cost)*quantity) AS margin FROM sales
                WHERE julianday(created)>=julianday('now',?) GROUP BY day ORDER BY day''', period)]
            result['below_cost'] = [dict(row) for row in db.execute('SELECT id,name,price,cost FROM items WHERE price<cost ORDER BY name')]
            result['shift'] = dict(shift) if shift else None
            result['expected'] = self.expected(db, shift) if shift else None
            result['inventory_cost'] = db.execute("SELECT COALESCE(SUM(stock*cost),0) FROM items WHERE kind='producto'").fetchone()[0]
            result['items'] = [dict(r) for r in db.execute('SELECT * FROM items ORDER BY name')]
            result['low_stock'] = [r for r in result['items'] if r['kind'] == 'producto' and r['stock'] <= r['minimum']]
            result['recent'] = [dict(r) for r in db.execute('SELECT * FROM sales ORDER BY id DESC LIMIT 20')]
            result['top'] = [dict(r) for r in db.execute("SELECT name,SUM(price*quantity) AS total FROM sales WHERE julianday(created)>=julianday('now',?) GROUP BY item_id,name ORDER BY total DESC LIMIT 5", period)]
            result['shifts'] = [dict(r) for r in db.execute('SELECT * FROM shifts ORDER BY id DESC LIMIT 10')]
            return result
