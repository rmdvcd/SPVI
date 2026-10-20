package cu.spvi.verificacion.room;

import android.database.Cursor;
import android.database.sqlite.SQLiteDoneException;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.util.TreeMap;

/** SOLO tools/verificacion. Sentencia compilada: guarda los bind* y ejecuta al pedirlo (Room la reutiliza). */
final class JdbcStatement implements SupportSQLiteStatement {
    private final JdbcDatabase db;
    private final String sql;
    private final TreeMap<Integer, Object> v = new TreeMap<>();

    JdbcStatement(JdbcDatabase db, String sql) { this.db = db; this.sql = sql; }

    private Object[] args() {
        if (v.isEmpty()) return null;
        Object[] a = new Object[v.lastKey()];
        for (java.util.Map.Entry<Integer, Object> e : v.entrySet()) a[e.getKey() - 1] = e.getValue();
        return a;
    }

    @Override public void bindNull(int i) { v.put(i, null); }
    @Override public void bindLong(int i, long x) { v.put(i, x); }
    @Override public void bindDouble(int i, double x) { v.put(i, x); }
    @Override public void bindString(int i, String x) { v.put(i, x); }
    @Override public void bindBlob(int i, byte[] x) { v.put(i, x); }
    @Override public void clearBindings() { v.clear(); }
    @Override public void close() { }

    @Override public void execute() { db.modificar(sql, args(), false); }
    @Override public int executeUpdateDelete() { return (int) db.modificar(sql, args(), false); }
    @Override public long executeInsert() { return db.modificar(sql, args(), true); }

    @Override public long simpleQueryForLong() {
        try (Cursor c = db.consultar(sql, args())) {
            if (!c.moveToFirst()) throw new SQLiteDoneException();
            return c.getLong(0);
        }
    }

    @Override public String simpleQueryForString() {
        try (Cursor c = db.consultar(sql, args())) {
            if (!c.moveToFirst()) throw new SQLiteDoneException();
            return c.getString(0);
        }
    }
}
