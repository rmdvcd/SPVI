package cu.spvi.verificacion.room;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteTransactionListener;
import android.os.CancellationSignal;
import android.util.Pair;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SOLO tools/verificacion. SupportSQLiteDatabase sobre UNA conexión JDBC (xerial sqlite-jdbc).
 *
 * Imita lo que importa de Android: una transacción pertenece al hilo que la abrió (los demás hilos esperan, como con
 * la conexión primaria de SQLiteDatabase); las transacciones anidadas solo confirman si TODOS los niveles marcaron
 * setTransactionSuccessful; los valores enteros salen como long; INSERT devuelve -1 si no insertó nada.
 */
public final class JdbcDatabase implements SupportSQLiteDatabase {
    private final Connection conn;
    private final String path;
    private final ReentrantLock lock = new ReentrantLock(true);
    /** Por nivel de transacción del hilo dueño: ¿marcó éxito? */
    private final Deque<Boolean> niveles = new ArrayDeque<>();
    private final Deque<SQLiteTransactionListener> oyentes = new ArrayDeque<>();
    private boolean fallida;
    private volatile boolean abierta = true;

    JdbcDatabase(String url, String path) {
        this.path = path;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(true);
        } catch (Exception e) {
            throw new SQLiteException("no se pudo abrir " + url + ": " + e);
        }
    }

    // ------------------------------------------------------------------------------------------------ ejecución

    static RuntimeException traducir(java.sql.SQLException e, String sql) {
        String m = String.valueOf(e.getMessage()) + " | SQL: " + sql;
        if (System.getProperty("spvi.debug") != null) System.err.println("[jvm-room] " + m);
        if (m.toLowerCase(Locale.ROOT).contains("constraint")) return new SQLiteConstraintException(m);
        return new SQLiteException(m);
    }

    static void enlazar(PreparedStatement ps, Object[] args) throws java.sql.SQLException {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            int k = i + 1;
            if (a == null) ps.setNull(k, java.sql.Types.NULL);
            else if (a instanceof byte[]) ps.setBytes(k, (byte[]) a);
            else if (a instanceof Float || a instanceof Double) ps.setDouble(k, ((Number) a).doubleValue());
            else if (a instanceof Number) ps.setLong(k, ((Number) a).longValue());
            else if (a instanceof Boolean) ps.setLong(k, ((Boolean) a) ? 1 : 0);
            else ps.setString(k, a.toString());
        }
    }

    /** Ejecuta con el cerrojo (otro hilo con una transacción abierta hace esperar, como en Android). */
    <T> T conCerrojo(Accion<T> a) {
        lock.lock();
        try {
            if (!abierta) throw new IllegalStateException("base de datos cerrada");
            return a.run();
        } catch (java.sql.SQLException e) {
            throw traducir(e, a.toString());
        } finally {
            lock.unlock();
        }
    }

    interface Accion<T> { T run() throws java.sql.SQLException; }

    Cursor consultar(String sql, Object[] args) {
        return conCerrojo(new Accion<Cursor>() {
            @Override public Cursor run() throws java.sql.SQLException {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    enlazar(ps, args);
                    boolean hayFilas = ps.execute();
                    if (!hayFilas) return new MemoriaCursor(new String[0], new ArrayList<>());
                    try (ResultSet rs = ps.getResultSet()) {
                        ResultSetMetaData md = rs.getMetaData();
                        int n = md.getColumnCount();
                        String[] cols = new String[n];
                        for (int i = 0; i < n; i++) cols[i] = md.getColumnLabel(i + 1);
                        List<Object[]> filas = new ArrayList<>();
                        while (rs.next()) {
                            Object[] f = new Object[n];
                            for (int i = 0; i < n; i++) f[i] = normalizar(rs.getObject(i + 1));
                            filas.add(f);
                        }
                        return new MemoriaCursor(cols, filas);
                    }
                }
            }
            @Override public String toString() { return sql; }
        });
    }

    static Object normalizar(Object o) {
        if (o instanceof Integer || o instanceof Short || o instanceof Byte) return ((Number) o).longValue();
        if (o instanceof Float) return ((Float) o).doubleValue();
        return o;
    }

    /** -1 = ejecutar sin más; si no, devuelve filas cambiadas (o rowid con insertar=true). */
    long modificar(String sql, Object[] args, boolean insertar) {
        return conCerrojo(new Accion<Long>() {
            @Override public Long run() throws java.sql.SQLException {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    enlazar(ps, args);
                    ps.execute();
                }
                if (!insertar) {
                    try (java.sql.Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT changes()")) {
                        rs.next();
                        return rs.getLong(1);
                    }
                }
                try (java.sql.Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT changes(), last_insert_rowid()")) {
                    rs.next();
                    return rs.getLong(1) == 0 ? -1L : rs.getLong(2);
                }
            }
            @Override public String toString() { return sql; }
        });
    }

    private void exec(String sql) {
        conCerrojo(new Accion<Void>() {
            @Override public Void run() throws java.sql.SQLException {
                try (java.sql.Statement s = conn.createStatement()) { s.execute(sql); }
                return null;
            }
            @Override public String toString() { return sql; }
        });
    }

    // ------------------------------------------------------------------------------------------------ transacciones

    private void empezar(String sql, SQLiteTransactionListener oyente) {
        lock.lock(); // se mantiene hasta endTransaction del nivel exterior
        if (niveles.isEmpty()) {
            fallida = false;
            try {
                exec(sql);
            } catch (RuntimeException e) {
                lock.unlock();
                throw e;
            }
        }
        niveles.push(Boolean.FALSE);
        oyentes.push(oyente == null ? NADA : oyente);
        if (oyente != null) oyente.onBegin();
    }

    private static final SQLiteTransactionListener NADA = new SQLiteTransactionListener() {
        @Override public void onBegin() { }
        @Override public void onCommit() { }
        @Override public void onRollback() { }
    };

    @Override public void beginTransaction() { empezar("BEGIN EXCLUSIVE", null); }
    @Override public void beginTransactionNonExclusive() { empezar("BEGIN IMMEDIATE", null); }
    @Override public void beginTransactionWithListener(SQLiteTransactionListener l) { empezar("BEGIN EXCLUSIVE", l); }
    @Override public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener l) { empezar("BEGIN IMMEDIATE", l); }

    @Override public void setTransactionSuccessful() {
        if (!lock.isHeldByCurrentThread() || niveles.isEmpty()) throw new IllegalStateException("sin transacción");
        niveles.pop();
        niveles.push(Boolean.TRUE);
    }

    @Override public void endTransaction() {
        if (!lock.isHeldByCurrentThread() || niveles.isEmpty()) throw new IllegalStateException("sin transacción");
        boolean ok = niveles.pop();
        SQLiteTransactionListener l = oyentes.pop();
        if (!ok) fallida = true;
        try {
            if (niveles.isEmpty()) {
                if (fallida) { exec("ROLLBACK"); l.onRollback(); } else { l.onCommit(); exec("COMMIT"); }
            } else if (ok) {
                l.onCommit();
            } else {
                l.onRollback();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override public boolean inTransaction() { return lock.isHeldByCurrentThread() && !niveles.isEmpty(); }
    @Override public boolean isDbLockedByCurrentThread() { return lock.isHeldByCurrentThread(); }
    @Override public boolean yieldIfContendedSafely() { return false; }
    @Override public boolean yieldIfContendedSafely(long sleepAfterYieldDelayMillis) { return false; }

    // ------------------------------------------------------------------------------------------------ API

    @Override public SupportSQLiteStatement compileStatement(String sql) { return new JdbcStatement(this, sql); }

    @Override public int getVersion() {
        try (Cursor c = query("PRAGMA user_version")) { c.moveToFirst(); return c.getInt(0); }
    }
    @Override public void setVersion(int version) { exec("PRAGMA user_version = " + version); }
    @Override public long getMaximumSize() { return Long.MAX_VALUE; }
    @Override public long setMaximumSize(long numBytes) { return numBytes; }
    @Override public long getPageSize() {
        try (Cursor c = query("PRAGMA page_size")) { c.moveToFirst(); return c.getLong(0); }
    }
    @Override public void setPageSize(long numBytes) { exec("PRAGMA page_size = " + numBytes); }

    @Override public Cursor query(String sql) { return consultar(sql, null); }
    @Override public Cursor query(String sql, Object[] bindArgs) { return consultar(sql, bindArgs); }
    @Override public Cursor query(SupportSQLiteQuery q) {
        Enlaces e = new Enlaces();
        q.bindTo(e);
        return consultar(q.getSql(), e.valores(q.getArgCount()));
    }
    @Override public Cursor query(SupportSQLiteQuery q, CancellationSignal signal) { return query(q); }

    @Override public long insert(String table, int conflictAlgorithm, ContentValues values) throws SQLException {
        String[] conf = {"", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE"};
        List<String> cols = new ArrayList<>(values.keySet());
        Object[] args = new Object[cols.size()];
        StringBuilder q = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) { args[i] = values.get(cols.get(i)); q.append(i == 0 ? "?" : ",?"); }
        String sql = "INSERT" + conf[conflictAlgorithm] + " INTO `" + table + "` (`" + String.join("`,`", cols) + "`) VALUES (" + q + ")";
        return modificar(sql, args, true);
    }

    @Override public int delete(String table, String whereClause, Object[] whereArgs) {
        String sql = "DELETE FROM `" + table + "`" + (whereClause == null || whereClause.isEmpty() ? "" : " WHERE " + whereClause);
        return (int) modificar(sql, whereArgs, false);
    }

    @Override public int update(String table, int conflictAlgorithm, ContentValues values, String whereClause, Object[] whereArgs) {
        String[] conf = {"", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE"};
        List<String> cols = new ArrayList<>(values.keySet());
        int nw = whereArgs == null ? 0 : whereArgs.length;
        Object[] args = new Object[cols.size() + nw];
        StringBuilder set = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) { args[i] = values.get(cols.get(i)); set.append(i == 0 ? "" : ",").append('`').append(cols.get(i)).append("`=?"); }
        for (int i = 0; i < nw; i++) args[cols.size() + i] = whereArgs[i];
        String sql = "UPDATE" + conf[conflictAlgorithm] + " `" + table + "` SET " + set + (whereClause == null || whereClause.isEmpty() ? "" : " WHERE " + whereClause);
        return (int) modificar(sql, args, false);
    }

    @Override public void execSQL(String sql) throws SQLException { modificar(sql, null, false); }
    @Override public void execSQL(String sql, Object[] bindArgs) throws SQLException { modificar(sql, bindArgs, false); }

    @Override public boolean isReadOnly() { return false; }
    @Override public boolean isOpen() { return abierta; }
    @Override public boolean needUpgrade(int newVersion) { return newVersion > getVersion(); }
    @Override public String getPath() { return path; }
    @Override public void setLocale(Locale locale) { }
    @Override public void setMaxSqlCacheSize(int cacheSize) { }
    @Override public void setForeignKeyConstraintsEnabled(boolean enabled) { exec("PRAGMA foreign_keys = " + (enabled ? "ON" : "OFF")); }
    @Override public boolean enableWriteAheadLogging() { return false; }
    @Override public void disableWriteAheadLogging() { }
    @Override public boolean isWriteAheadLoggingEnabled() { return false; }
    @Override public List<Pair<String, String>> getAttachedDbs() { return Collections.emptyList(); }
    @Override public boolean isDatabaseIntegrityOk() {
        try (Cursor c = query("PRAGMA integrity_check")) { c.moveToFirst(); return "ok".equalsIgnoreCase(c.getString(0)); }
    }

    @Override public void close() {
        lock.lock();
        try {
            abierta = false;
            conn.close();
        } catch (java.sql.SQLException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /** Recoge los bind* de un SupportSQLiteQuery. */
    static final class Enlaces implements androidx.sqlite.db.SupportSQLiteProgram {
        private final java.util.TreeMap<Integer, Object> v = new java.util.TreeMap<>();
        @Override public void bindNull(int i) { v.put(i, null); }
        @Override public void bindLong(int i, long x) { v.put(i, x); }
        @Override public void bindDouble(int i, double x) { v.put(i, x); }
        @Override public void bindString(int i, String x) { v.put(i, x); }
        @Override public void bindBlob(int i, byte[] x) { v.put(i, x); }
        @Override public void clearBindings() { v.clear(); }
        @Override public void close() { }
        Object[] valores(int n) {
            int max = Math.max(n, v.isEmpty() ? 0 : v.lastKey());
            Object[] a = new Object[max];
            for (java.util.Map.Entry<Integer, Object> e : v.entrySet()) a[e.getKey() - 1] = e.getValue();
            return a;
        }
    }
}
