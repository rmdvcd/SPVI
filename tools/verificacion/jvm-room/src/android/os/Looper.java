package android.os;

/**
 * SOLO tools/verificacion (va delante de android-all en el classpath de los tests de Room en la JVM).
 * El Looper real necesita código nativo. Room solo pregunta «¿es este el hilo principal?»
 * (getMainLooper().getThread()): aquí el hilo principal es un hilo que nunca ejecuta nada, así que
 * ningún hilo de prueba es «principal», como en un test instrumentado que no corre en el hilo de UI.
 */
public final class Looper {
    private static final Thread PRINCIPAL = new Thread(() -> { }, "main-ficticio");
    private static final Looper MAIN = new Looper();

    private Looper() { }

    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return null; }
    public static void prepare() { throw new UnsupportedOperationException("Looper no disponible en la JVM"); }
    public static void prepareMainLooper() { throw new UnsupportedOperationException("Looper no disponible en la JVM"); }
    public static void loop() { throw new UnsupportedOperationException("Looper no disponible en la JVM"); }
    public Thread getThread() { return PRINCIPAL; }
    public boolean isCurrentThread() { return Thread.currentThread() == PRINCIPAL; }
    public void quit() { }
    public void quitSafely() { }
}
