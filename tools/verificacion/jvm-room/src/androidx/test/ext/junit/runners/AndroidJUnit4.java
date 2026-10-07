package androidx.test.ext.junit.runners;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/** SOLO tools/verificacion: en la JVM, @RunWith(AndroidJUnit4::class) = runner JUnit 4 normal (sin Robolectric). */
public final class AndroidJUnit4 extends BlockJUnit4ClassRunner {
    public AndroidJUnit4(Class<?> klass) throws InitializationError {
        super(klass);
    }
}
