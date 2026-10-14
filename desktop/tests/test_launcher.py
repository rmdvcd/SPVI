import tempfile
import threading
import time
import unittest
from pathlib import Path
import launcher


class ExtractionReadyTest(unittest.TestCase):
    def test_listo_cuando_existen(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            (base / '_sqlite3.pyd').touch(); (base / 'sqlite3.dll').touch()
            self.assertTrue(launcher.extraction_ready(base, timeout=1))

    def test_falla_si_nunca_aparecen(self):
        with tempfile.TemporaryDirectory() as folder:
            start = time.monotonic()
            self.assertFalse(launcher.extraction_ready(Path(folder), timeout=0.3))
            self.assertLess(time.monotonic() - start, 5)

    def test_espera_al_que_falta(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            (base / 'sqlite3.dll').touch()

            def later():
                time.sleep(0.4)
                (base / '_sqlite3.pyd').touch()
            worker = threading.Thread(target=later); worker.start()
            try:
                self.assertTrue(launcher.extraction_ready(base, timeout=5))
            finally:
                worker.join()


if __name__ == '__main__':
    unittest.main()
