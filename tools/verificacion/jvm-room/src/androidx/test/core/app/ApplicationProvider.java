package androidx.test.core.app;

import android.content.Context;
import android.content.ContextWrapper;

/** SOLO tools/verificacion: contexto vacío (Room en memoria no lo usa para nada más que guardarlo). */
public final class ApplicationProvider {
    private ApplicationProvider() {}

    @SuppressWarnings("unchecked")
    public static <T extends Context> T getApplicationContext() {
        return (T) new ContextWrapper(null) {
            @Override public Object getSystemService(String name) { return null; } // Room: modo de diario TRUNCATE
            @Override public Context getApplicationContext() { return this; }
            @Override public String getPackageName() { return "cu.spvi.verificacion"; }
        };
    }
}
