package cu.spvi.verificacion.room;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.io.File;

/**
 * SOLO tools/verificacion. Abre la base (memoria o archivo en java.io.tmpdir/spvi-jvm-room) y aplica el ciclo de
 * SQLiteOpenHelper: onConfigure → (onCreate | onUpgrade | onDowngrade dentro de una transacción) → onOpen.
 */
public final class JdbcOpenHelper implements SupportSQLiteOpenHelper {
    private final Configuration config;
    private JdbcDatabase db;

    public JdbcOpenHelper(Configuration config) { this.config = config; }

    /** Ruta en disco para una base con nombre (los tests la borran con este mismo método). */
    public static File archivo(String nombre) {
        File f = new File(nombre);
        if (f.isAbsolute()) return f;
        File dir = new File(System.getProperty("java.io.tmpdir"), "spvi-jvm-room");
        dir.mkdirs();
        return new File(dir, nombre);
    }

    @Override public String getDatabaseName() { return config.name; }
    @Override public void setWriteAheadLoggingEnabled(boolean enabled) { }

    @Override public synchronized SupportSQLiteDatabase getWritableDatabase() {
        if (db != null && db.isOpen()) return db;
        String url = config.name == null ? "jdbc:sqlite::memory:" : "jdbc:sqlite:" + archivo(config.name).getAbsolutePath();
        JdbcDatabase d = new JdbcDatabase(url, config.name == null ? ":memory:" : archivo(config.name).getAbsolutePath());
        Callback cb = config.callback;
        cb.onConfigure(d);
        int actual = d.getVersion();
        if (actual != cb.version) {
            d.beginTransaction();
            try {
                if (actual == 0) cb.onCreate(d);
                else if (actual < cb.version) cb.onUpgrade(d, actual, cb.version);
                else cb.onDowngrade(d, actual, cb.version);
                d.setVersion(cb.version);
                d.setTransactionSuccessful();
            } finally {
                d.endTransaction();
            }
        }
        cb.onOpen(d);
        db = d;
        return d;
    }

    @Override public SupportSQLiteDatabase getReadableDatabase() { return getWritableDatabase(); }

    @Override public synchronized void close() {
        if (db != null) db.close();
        db = null;
    }
}
