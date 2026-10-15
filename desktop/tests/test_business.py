"""Regresiones del principal canónico; ejecutar en la PC, sin red ni GL real."""

import base64
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from spvi_web.business import Business, get, put, objects, set_meta
from spvi_web.licensing import subtokens
from spvi_web.secrets import SecretBox
from spvi_web.store import Store


class LicenciaDePrueba:
    """Doble de `LicenseClient`: licencia de prueba (5 secundarias) y firma de sub-tokens (mitigación 7)."""

    def __init__(self):
        self._clave = ec.generate_private_key(ec.SECP256R1())

    def require_active(self):
        return self.status()

    def status(self):
        return {"clase": "PRUEBA", "bloqueada": False, "secundarias": 5}

    def firma_publica(self):
        return base64.b64encode(
            self._clave.public_key().public_bytes(
                serialization.Encoding.DER,
                serialization.PublicFormat.SubjectPublicKeyInfo,
            )
        ).decode("ascii")

    def emitir_subtoken(self, dispositivo, ranura):
        if not isinstance(ranura, int) or not 1 <= ranura <= 5:
            return None
        return subtokens.emitir(
            self._clave, subtokens.PRUEBA_ID, 5, None, str(dispositivo), ranura
        )


class BusinessTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.business = Business(
            Store(Path(temp.name) / "datos.sqlite3"),
            LicenciaDePrueba(),
            SecretBox("solo-pruebas"),
        )
        self.business.operate(
            "item",
            dict(
                name="Café", kind="producto", price="10", cost="2", stock=20, minimum=1
            ),
        )
        self.business.operate("open", {"fund": "100"})

    def sale(self, quantity=1):
        self.business.operate(
            "cart",
            {"lines": [{"item_id": 1, "quantity": quantity}], "method": "efectivo"},
        )

    def test_informe_de_ventas_y_seleccion_por_ids(self):
        """Registros puede exportar solo lo marcado: ventas por id y turnos por id."""
        self.business.operate(
            "cart", {"lines": [{"item_id": 1, "quantity": 1}], "method": "efectivo"}
        )
        with self.business.connect() as db:
            venta = objects(db, "ventas")[0]["id"]
            turno = objects(db, "turnos")[0]["id"]
        columns, rows, items = self.business.report("ventas")
        self.assertEqual(
            ["Venta", "Fecha UTC", "Detalle", "Cobro", "Total CUP", "Estado"], columns
        )
        self.assertEqual(
            [venta, "Café", "Efectivo", Decimal("10"), "Válida"],
            [rows[0][0], rows[0][2], rows[0][3], rows[0][4], rows[0][5]],
        )
        self.assertEqual([], self.business.report("ventas", ids=[venta + 500])[1])
        self.assertEqual(1, len(self.business.report("ventas", ids=[venta])[1]))
        self.assertEqual([], self.business.report("ventas", query="nada")[1])
        for mal in (["x"], [0], [-1], "no-lista"):
            with self.subTest(ids=mal), self.assertRaises(ValueError):
                self.business.report("ventas", ids=mal)
        self.assertEqual(1, len(self.business.report("turnos", ids=[turno])[1]))
        self.assertEqual(0, len(self.business.report("turnos", ids=[turno + 500])[1]))

    def test_ledger_devuelve_ids_del_filtro_completo(self):
        """«Marcar todo lo filtrado» necesita los identificadores de todas las páginas, no solo la visible."""
        for _ in range(3):
            self.business.operate(
                "cart", {"lines": [{"item_id": 1, "quantity": 1}], "method": "efectivo"}
            )
        pagina = self.business.ledger("ventas", page=1, size=2, ids=True)
        self.assertEqual(3, len(pagina["ids"]))
        self.assertEqual(2, len(pagina["items"]))
        self.assertNotIn("ids", self.business.ledger("ventas", page=1, size=2))
        pass

    def test_tablero_por_periodo(self):
        """Inicio puede filtrar por turno, hoy, N días o todo el historial."""
        self.business.operate(
            "cart", {"lines": [{"item_id": 1, "quantity": 1}], "method": "efectivo"}
        )
        with self.business.connect() as db:
            vieja = objects(db, "ventas")[0]
            vieja["fecha"] -= 40 * 86400000
            put(db, "ventas", vieja)
        self.business.operate(
            "cart", {"lines": [{"item_id": 1, "quantity": 1}], "method": "efectivo"}
        )
        self.assertEqual(2, self.business.dashboard(0)["operations"])
        self.assertEqual(1, self.business.dashboard(1)["operations"])
        self.assertEqual(1, self.business.dashboard(30)["operations"])
        self.assertEqual(1, self.business.dashboard("turno")["operations"])
        self.assertIsNone(self.business.dashboard("turno")["revenue_change_percent"])
        self.business.operate("close", {"counted": "0"})
        # Sin turno abierto, «turno» cuenta desde el inicio del día de hoy.
        self.assertEqual(1, self.business.dashboard("turno")["operations"])
        for value in (2, -1, 15, "x", True):
            with self.subTest(days=value), self.assertRaises(ValueError):
                self.business.dashboard(value)

    def test_carrito_es_una_operacion_y_persiste(self):
        self.business.operate(
            "cart",
            {
                "lines": [{"item_id": 1, "quantity": 2}, {"item_id": 1, "quantity": 3}],
                "method": "efectivo",
            },
        )
        with self.business.connect() as db:
            self.assertEqual(1, len(objects(db, "ventas")))
            self.assertEqual(15, get(db, "productos", 1)["cantidad"])
            self.assertEqual(1, len(objects(db, "movimientos")))
            self.assertEqual(5, objects(db, "ventas")[0]["detalles"][0]["cantidad"])
            self.business.capture_local(db)
            self.assertEqual(1, len(objects(db, "ventas")))
        self.assertEqual(15000, self.business.dashboard()["expected"])

    def test_carrito_insuficiente_revierte_todo(self):
        with self.assertRaises(ValueError):
            self.business.operate(
                "cart",
                {
                    "lines": [
                        {"item_id": 1, "quantity": 2},
                        {"item_id": 1, "quantity": 100},
                    ],
                    "method": "efectivo",
                },
            )
        with self.business.connect() as db:
            self.assertEqual([], objects(db, "ventas"))
            self.assertEqual(20, get(db, "productos", 1)["cantidad"])
            self.assertEqual(0, db.execute("SELECT COUNT(*) FROM sales").fetchone()[0])

    def test_minimo_preajuste_se_calcula_sobre_carrito(self):
        with self.business.connect() as db:
            put(
                db,
                "preajustes",
                dict(
                    id=1,
                    nombre="Descuento",
                    puntosBasicos=-1000,
                    productoIds=[1],
                    activo=True,
                    metodoPago="EFECTIVO",
                    importeMinimoCent=3000,
                ),
            )
        self.business.operate(
            "cart",
            {
                "lines": [{"item_id": 1, "quantity": 2}, {"item_id": 1, "quantity": 1}],
                "method": "efectivo",
            },
        )
        with self.business.connect() as db:
            for detail in objects(db, "ventas")[0]["detalles"]:
                self.assertEqual(1000, detail["precioBaseCent"])
                self.assertEqual(900, detail["precioUnitarioCent"])
        self.assertEqual(12700, self.business.dashboard()["expected"])

    def recipe(self):
        with self.business.connect() as db:
            product = get(db, "productos", 1)
            product.update(categoria="Elaborado", cantidad=0)
            put(db, "productos", product)
            put(
                db,
                "insumos",
                dict(
                    id=1,
                    nombre="Grano",
                    unidad="KILOGRAMO",
                    precioCent=200,
                    cantidadMil=5000,
                    creadoEn=1,
                    actualizadoEn=1,
                ),
            )
            put(
                db,
                "recetas",
                dict(productoId=1, lineas=[dict(insumoId=1, cantidadMil=500)]),
            )
            self.business.project_catalog(db)

    def test_elaborado_consume_receta_sin_stock_propio(self):
        self.recipe()
        self.sale(2)
        with self.business.connect() as db:
            self.assertEqual(0, get(db, "productos", 1)["cantidad"])
            self.assertEqual(4000, get(db, "insumos", 1)["cantidadMil"])
            self.assertEqual(
                100, objects(db, "ventas")[0]["detalles"][0]["costoUnitarioCent"]
            )
            self.assertEqual(
                ["CONSUMO"], [m["tipo"] for m in objects(db, "movimientos")]
            )

    def test_anulacion_usa_consumo_original_no_receta_editada(self):
        self.recipe()
        self.sale(2)
        with self.business.connect() as db:
            put(
                db,
                "recetas",
                dict(productoId=1, lineas=[dict(insumoId=1, cantidadMil=900)]),
            )
        self.business.operate("void", dict(id=1, reason="Error de cantidad"))
        self.business.operate("void", dict(id=1, reason="Reintento"))
        with self.business.connect() as db:
            self.assertEqual(5000, get(db, "insumos", 1)["cantidadMil"])
            self.assertEqual(
                1, sum(m["tipo"] == "ANULACION" for m in objects(db, "movimientos"))
            )
        self.assertEqual(10000, self.business.dashboard()["expected"])

    def test_precio_ajustado_bajo_costo_no_persiste(self):
        with self.business.connect() as db:
            put(
                db,
                "preajustes",
                dict(
                    id=1,
                    nombre="Inválido",
                    puntosBasicos=-9000,
                    productoIds=[1],
                    activo=True,
                ),
            )
        with self.assertRaises(ValueError):
            self.sale()
        with self.business.connect() as db:
            self.assertEqual([], objects(db, "ventas"))

    def test_cuenta_malformada_no_persiste(self):
        with self.assertRaises(ValueError):
            self.business.settings(
                {"perfil": {"tarjetas": [{"id": 1, "numero": "abc"}]}}
            )

    def test_correccion_conserva_original_y_arqueo(self):
        self.sale(2)
        self.business.operate(
            "correct",
            dict(
                id=1,
                reason="Cantidad equivocada",
                lines=[dict(item_id=1, quantity=3)],
                method="efectivo",
            ),
        )
        with self.business.connect() as db:
            self.assertIsNotNone(get(db, "ventas", 1)["anuladaEn"])
            self.assertEqual(1, get(db, "ventas", 2)["corrigeVentaId"])
            self.assertEqual(17, get(db, "productos", 1)["cantidad"])
        self.assertEqual(13000, self.business.dashboard()["expected"])

    def test_correccion_fallida_revierte_anulacion(self):
        self.sale(2)
        with self.assertRaises(ValueError):
            self.business.operate(
                "correct",
                dict(
                    id=1,
                    reason="Error",
                    lines=[dict(item_id=1, quantity=200)],
                    method="efectivo",
                ),
            )
        with self.business.connect() as db:
            self.assertIsNone(get(db, "ventas", 1).get("anuladaEn"))
            self.assertEqual(18, get(db, "productos", 1)["cantidad"])
            self.assertEqual(1, len(objects(db, "ventas")))
        self.assertEqual(12000, self.business.dashboard()["expected"])

    def test_insumo_venta_entera_descuenta_milesimas(self):
        self.recipe()
        with self.business.connect() as db:
            supply = get(db, "insumos", 1)
            supply["precioVentaCent"] = 500
            put(db, "insumos", supply)
            self.business.project_catalog(db)
            local = db.execute(
                "SELECT local_id FROM local_refs WHERE kind='insumos' AND object_id=1"
            ).fetchone()[0]
        self.business.operate(
            "cart", dict(lines=[dict(item_id=local, quantity=2)], method="efectivo")
        )
        with self.business.connect() as db:
            self.assertEqual(3000, get(db, "insumos", 1)["cantidadMil"])
            self.assertEqual("INSUMO", get(db, "ventas", 1)["detalles"][0]["clase"])
            self.business.capture_local(db)
            self.assertEqual([], objects(db, "servicios"))
        self.assertEqual(11000, self.business.dashboard()["expected"])

    def test_transferencia_congela_cliente_y_registra_fijo(self):
        self.business.operate(
            "cart",
            dict(
                lines=[dict(item_id=1, quantity=2)],
                method="transferencia",
                transaction=dict(
                    numero="TX123",
                    clienteNombre="Cliente de prueba",
                    clienteCi="90010112345",
                    clienteTelefono="51234567",
                    clienteFijo=True,
                ),
            ),
        )
        with self.business.connect() as db:
            self.assertEqual(2000, get(db, "ventas", 1)["transaccion"]["importeCent"])
            self.assertEqual("90010112345", objects(db, "clientesFijos")[0]["ci"])
        self.assertEqual(10000, self.business.dashboard()["expected"])

    def test_transferencia_invalida_no_descuenta(self):
        with self.assertRaises(ValueError):
            self.business.operate(
                "cart",
                dict(
                    lines=[dict(item_id=1, quantity=2)],
                    method="transferencia",
                    transaction=dict(
                        numero="TX",
                        clienteNombre="Cliente",
                        clienteCi="mal",
                        clienteTelefono="51234567",
                        clienteFijo=True,
                    ),
                ),
            )
        with self.business.connect() as db:
            self.assertEqual(20, get(db, "productos", 1)["cantidad"])
            self.assertEqual([], objects(db, "ventas"))

    def test_respaldo_android_canonico_y_copia_previa(self):
        self.sale()
        self.business.operate("close", dict(counted=110))
        backup = self.business.export_backup("", android=True)
        self.business.operate(
            "item",
            dict(name="Otro", kind="producto", price=3, cost=1, stock=2, minimum=0),
        )
        self.business.import_backup(
            backup, "", android=True, safety_password="copia-segura-pruebas"
        )
        with self.business.connect() as db:
            self.assertEqual(1, len(objects(db, "productos")))
            self.assertEqual(1, len(objects(db, "ventas")))
        self.assertTrue(
            (Path(self.business.path).parent / "antes_de_restaurar.spvidesk").exists()
        )

    def test_cotizacion_no_modifica_el_negocio(self):
        quote = self.business.quote(
            dict(lines=[dict(item_id=1, quantity=3)], method="transferencia")
        )
        self.assertEqual(3000, quote["total"])
        with self.business.connect() as db:
            self.assertEqual([], objects(db, "ventas"))
            self.assertEqual([], objects(db, "clientesFijos"))
            self.assertEqual(20, get(db, "productos", 1)["cantidad"])
            self.assertEqual(0, db.execute("SELECT COUNT(*) FROM sales").fetchone()[0])

    def test_tops_del_periodo_como_el_movil(self):
        """Inicio trae los 7 tops del móvil con sus reglas (unidades, lento con no vendidos, ganancia > 0, servicios, empleado y cliente)."""
        self.business.operate(
            "item",
            dict(name="Pan", kind="producto", price="5", cost="4", stock=50, minimum=1),
        )
        self.business.operate(
            "item",
            dict(
                name="Leche", kind="producto", price="20", cost="5", stock=30, minimum=1
            ),
        )
        self.business.operate(
            "item", dict(name="Corte", kind="servicio", price="15", cost="0")
        )
        with self.business.connect() as db:
            set_meta(db, "perfil", dict(nombre="Dueño", apellidos="Tienda"))
        self.business.operate(
            "cart", {"lines": [{"item_id": 1, "quantity": 3}], "method": "efectivo"}
        )
        self.business.operate(
            "cart", {"lines": [{"item_id": 2, "quantity": 1}], "method": "efectivo"}
        )
        self.business.operate(
            "cart",
            {
                "lines": [{"item_id": 4, "quantity": 2}],
                "method": "transferencia",
                "transaction": {
                    "numero": "TX1",
                    "clienteNombre": "Ana Pérez",
                    "clienteCi": "90010112345",
                    "clienteTelefono": "51234567",
                },
            },
        )
        tops = self.business.dashboard(days=7)["tops"]
        self.assertEqual(["Café", "Pan"], [x["nombre"] for x in tops["mas"]])
        self.assertEqual(["Leche", "Pan", "Café"], [x["nombre"] for x in tops["lento"]])
        self.assertEqual(["Café", "Pan"], [x["nombre"] for x in tops["rentabilidad"]])
        self.assertEqual(2400, tops["rentabilidad"][0]["ganancia"])
        self.assertEqual(["Corte"], [x["nombre"] for x in tops["serviciosMas"]])
        self.assertEqual(2, tops["serviciosMas"][0]["unidades"])
        self.assertEqual(["Corte"], [x["nombre"] for x in tops["serviciosMenos"]])
        self.assertEqual(
            [("Dueño Tienda", 3)],
            [(x["nombre"], x["operaciones"]) for x in tops["empleados"]],
        )
        self.assertEqual(["Ana Pérez"], [x["nombre"] for x in tops["clientes"]])

    def test_cotizacion_correccion_no_anula_original(self):
        self.sale()
        quote = self.business.quote(
            dict(id=1, lines=[dict(item_id=1, quantity=2)], method="efectivo")
        )
        self.assertEqual(2000, quote["total"])
        with self.business.connect() as db:
            self.assertIsNone(get(db, "ventas", 1).get("anuladaEn"))
            self.assertEqual(1, len(objects(db, "ventas")))
        self.assertEqual(11000, self.business.dashboard()["expected"])
