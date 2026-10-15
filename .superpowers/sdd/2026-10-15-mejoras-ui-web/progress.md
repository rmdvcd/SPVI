# SDD ledger — plan: docs/superpowers/plans/2026-10-15-mejoras-ui-web.md

- Ruling (setup): trabajo en `main` sin worktree — el `.venv` de desktop y la caché Gradle no existen en un worktree y reinstalarlos rompe los tests; commits locales, sin push. Coste si mal: ensuciar main (mitigado: commits por tarea, push solo con permiso).
- Ruling (setup): scripts `task-start`/`task-done` son sh (no corren en Windows) — se emulan con líneas `Task N: complete` aquí.
- Pre-flight: sin interfaces compartidas entre tareas (cada una toca archivos distintos; T2 centraliza `bannerLicencia` y elimina sus llamadas por vista dentro de la misma tarea).

Task 1: complete (commit 49b0bdb, tests: unittest 131 OK + captura servicios sin solape).
Task 2: complete (commit 09ce705, tests: unittest 131 OK + 10 capturas con banner único).
Task 3: complete (commit 33d518c, tests: unittest 131 OK + captura registros en dos filas).
Task 4: complete (commit ea942e2, tests: unittest 131 OK + captura sin costos, 0 spans).
Tasks 5-7: complete (commit bcc36f0, tests: unittest 131 OK + capturas ajustes/tiempo/inventario).
Task 8: complete (commit bcc36f0 incluye HISTORIAL; suite 131 OK + 10 capturas revisadas).
