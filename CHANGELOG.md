# Cambios

## [1.1.0] — 2026-11-22
### Pruebas
- Batería JUnit de 93 pruebas: unitarias (caja blanca y negra), API, seguridad, integración y sistema.
- Pruebas de integración con MySQL 8.4 real en CI y cobertura con JaCoCo (umbral 85 % de líneas).
- Prueba de carga (10–400 usuarios concurrentes) con perfiles de Java Flight Recorder.
- 10 pruebas de aceptación en navegador (Playwright) y guía de aceptación con usuarios (SUS).
### Corregido
- F1–F4 (búsqueda, recomendaciones, inventario, devoluciones), C1 (carrera en préstamos simultáneos).
- P1–P5 (servidor de un hilo, búsqueda lineal, recomendaciones recalculadas, Nagle, ordenamiento completo).
- S1–S9 (contraseñas, IDOR, fuerza bruta, filtración de errores, datos personales en claro, autorización
  por rol, cabeceras, inyección SQL, enumeración de usuarios).
- U1 (formulario de ingreso visible tras iniciar sesión).

Detalle de cada defecto y la prueba que lo detectó: `docs/PLAN_DE_PRUEBAS.md`.

## [1.0.0] — 2026-11-16
- Versión inicial: búsqueda, recomendaciones, inventario, préstamos, API REST e interfaz web.
