# SDD ledger — plan: docs/superpowers/plans/2026-10-15-mejoras-ui-movil.md

- Ruling (setup): idem plan web — trabajo en `main`, commits locales sin push.
- Pre-flight: sin interfaces compartidas entre tareas (T1 InicioScreen, T2 tres pantallas+VMs, T3 AjustesScreen, T4 solo verificación).
- Ruling (T1): un segundo editor añadió `resumenVentas/Ganancia/Inventario/Metodos/Top/TopPersonas` en `InicioLogic.kt` (sin commit) con la misma API del plan; no duplico, solo cableo UI y corro mi test contra su implementación. Coste si mal: si cambian su firma, ajusto el cableado.

Task 1: complete (commit 9fac8d5; cableado subtitle en SpviAccordionCard/CardGrafico/tops + test resumenesDeLosAcordeones en verde).
