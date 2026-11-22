# Plan y resultados de pruebas — LibManage

Proyecto de la semana 10 de Ingeniería de Software (Universidad Autónoma de Chile).
Autor: Patricio Farías Inzunza.

## 1. Objetivo y alcance

Verificar y validar LibManage, sistema de gestión de bibliotecas con backend Java, interfaz web y base
de datos MySQL, en sus cuatro funciones críticas: **búsqueda avanzada**, **recomendaciones**,
**inventario** y **préstamos**. También se prueban tres atributos de calidad: **rendimiento** con alta
concurrencia, **seguridad** de los datos personales y **disponibilidad**.

| Nivel | Técnica | Herramienta | Dónde |
|---|---|---|---|
| Unitario | Caja blanca (caminos básicos, condiciones) y caja negra (particiones, valores límite) | JUnit 4 | `src/test/java/.../unitarias` |
| API | Caja negra: particiones, valores límite, tabla de decisión | JUnit + `java.net.http` | `.../api/ApiCajaNegraTest` |
| Seguridad | OWASP Top 10 (A01, A02, A03, A05, A07) | JUnit | `.../api/SeguridadApiTest`, `SeguridadTest` |
| Integración | Front–API–repositorio y API–MySQL | JUnit + MySQL 8.4 (CI) | `.../integracion` |
| Sistema | Flujos completos y concurrencia (50–80 hilos) | JUnit | `IntegracionSistemaTest`, `PrestamoServiceTest` |
| Rendimiento | Carga con 10 a 400 usuarios concurrentes + perfil JFR | `carga/PruebaCarga.java`, Java Flight Recorder | `carga/` |
| Aceptación | Historias de usuario en navegador real | Playwright (Chromium) | `e2e/test_aceptacion.py` |
| Aceptación con usuarios | Tareas guiadas + cuestionario SUS | Guía | `docs/GUIA_UAT.md` |

Criterios de salida: 100 % de pruebas aprobadas; cobertura de líneas ≥ 85 % (verificada por JaCoCo en CI);
latencia p95 ≤ 500 ms con 200 usuarios concurrentes; 0 vulnerabilidades abiertas de las categorías probadas.

## 2. Estrategia: probar primero la versión inicial (v1.0)

La batería completa se ejecutó primero sobre la versión inicial **v1.0.0** para medir su capacidad de
detectar defectos. Después se corrigió cada defecto en una rama propia y se volvió a medir (v1.1.0).

| Ejecución | Pruebas JUnit | Aceptación (navegador) | p95 con 200 usuarios |
|---|---|---|---|
| v1.0.0 | 93 ejecutadas, **23 fallidas** | 10 ejecutadas, **2 fallidas** (8 tras agregar la verificación U1) | 17 126 ms |
| v1.1.0 | 93 ejecutadas, **0 fallidas** | 10 ejecutadas, **0 fallidas** | **272 ms** |

## 3. Defectos encontrados y prueba que los detecta

| ID | Defecto en v1.0 | Prueba que lo detectó | Corrección |
|---|---|---|---|
| F1 | El rango de años excluía el año final (`>=` en vez de `>`) | `rangoDeAniosIncluyeAmbosLimites`, `busquedaAvanzadaCombinaFiltros`, E2E HU-01 | Comparación inclusiva |
| F2 | Recomendaba libros que el usuario ya leyó | `nuncaRecomiendaLibrosYaLeidos` | Excluir el historial propio |
| F3 | Se podía dejar menos ejemplares que los prestados | `noPermiteDejarMenosEjemplaresQueLosPrestados` | Validación en inventario |
| F4 | Devolución doble y devolución de préstamos ajenos | `d2…`, `d3…`, `noSePuedenVerNiDevolverPrestamosAjenos` | Verificar dueño y estado |
| C1 | Condición de carrera: 80 solicitudes simultáneas prestaban más ejemplares de los existentes | `ochentaSolicitudesSimultaneas…`, `solicitudesSimultaneasDelMismoUsuario…` | Descuento atómico + bloqueo por usuario |
| P1 | Servidor HTTP con un solo hilo | Prueba de carga + perfil JFR (99 % de CPU en `HTTP-Dispatcher`) | Hilos virtuales |
| P2 | Búsqueda lineal que normalizaba los 20 000 títulos en cada consulta | Perfil JFR (87,6 % en `Texto.normalizar`) | Índice invertido |
| P3 | Recomendaciones recalculadas desde cero en cada solicitud | `unPrestamoNuevoActualizaLasRecomendaciones` + perfil | Coocurrencias incrementales con caché |
| P4 | ~40 ms extra por respuesta (Nagle + ACK retardado) | Prueba de carga de v1.1 (p50 constante de 44 ms) | `TCP_NODELAY` |
| P5 | Ordenamiento completo de todas las coincidencias | Perfil JFR de v1.1 | Montículo acotado (top-k) |
| S1 | Contraseñas con SHA-256 sin sal | `contrasenasSeGuardanConSal…`, `produccionUsaAlMenos600MilIteraciones` | PBKDF2-SHA256, 600 000 iteraciones |
| S2 | IDOR: `?usuario=` permitía ver préstamos ajenos | `noSePuedenVerNiDevolverPrestamosAjenos` | Solo administradores |
| S3 | Sin bloqueo por fuerza bruta | `bloqueoPorFuerzaBruta` | 5 intentos / 15 min |
| S4 | Errores 500 mostraban la traza interna | `erroresInternosNoExponenDetalles` | Mensaje genérico + referencia |
| S5 | RUT y correo guardados y mostrados en claro | `elAlmacenamientoNoContieneDatosPersonalesEnClaro`, `datosPersonalesSeMuestranEnmascarados` | AES-256-GCM + enmascarado |
| S6 | Un estudiante podía usar funciones de administrador | `estudianteNoAccedeAFuncionesDeAdministrador` | Control por rol |
| S7 | Sin cabeceras de seguridad | `cabecerasDeSeguridadEnTodasLasRespuestas` | CSP, nosniff, DENY, no-store |
| S8 | Consulta por ISBN concatenando texto (inyección SQL) | `inyeccionSqlNoTieneEfecto` (MySQL, CI) | `PreparedStatement` |
| S9 | El login revelaba si el correo existía | `loginNoRevelaSiElEmailExiste`, E2E HU-04 | Mensaje único |
| U1 | El formulario de ingreso seguía visible tras iniciar sesión | E2E (verificación en `ingresar`) | Regla CSS `[hidden]` |

## 4. Caja blanca

**`PrestamoService.prestar`**: grafo con 7 decisiones, complejidad ciclomática V(G) = 8; ocho caminos
básicos C1–C8 (usuario inexistente, bloqueado, con vencidos, en su límite, con el mismo libro, libro
inexistente, sin ejemplares, éxito), una prueba por camino. **`devolver`**: V(G) = 5, caminos D1–D5.
Se añadió la prueba del valor frontera del día límite (`elDiaLimiteAunNoCuentaComoVencido`).

Cobertura de líneas medida localmente con JDI (`scripts/cobertura.sh`): **87,5 %** total; servicio
98,1 %, web 89,3 %, dominio 100 %. El repositorio JDBC solo se ejecuta contra MySQL en CI, donde JaCoCo
exige ≥ 85 % de líneas (`mvn verify`).

## 5. Caja negra

- **Particiones de equivalencia**: ISBN válido / con dígito verificador erróneo / con letras / de largo
  incorrecto; parámetros de búsqueda válidos e inválidos.
- **Valores límite**: tamaño de página 0, 1, 50, 51; años 1449, 1450, 2100, 2101; ejemplares 0, 1, 100,
  101; largo de título 200/201; recomendaciones n = 0, 1, 20, 21.
- **Tabla de decisión** del préstamo (sesión × rol × disponibilidad × límite) en `tablaDeDecisionDelPrestamo`.

## 6. Rendimiento

Escenario: catálogo de 20 000 libros, 2 000 lectores con historial; mezcla 50 % búsqueda avanzada,
15 % detalle, 25 % recomendaciones, 10 % préstamo + devolución; 15 s por nivel tras 2 s de calentamiento;
servidor y generador en la misma máquina (2 vCPU). Resultados: `carga/resultados/carga.csv`, gráfico
`carga/resultados/carga.png`, perfiles `carga/resultados/perfil_v1.0.txt` y `perfil_v1.1.txt`.

| Usuarios | v1.0 sol/s | v1.0 p95 | v1.1 sol/s | v1.1 p95 |
|---|---|---|---|---|
| 10 | 16,3 | 1 193 ms | 933,9 | 21,5 ms |
| 50 | 18,7 | 3 495 ms | 1 208,6 | 71,6 ms |
| 100 | 16,3 | 7 536 ms | 1 242,5 | 141,8 ms |
| 200 | 14,9 | 17 126 ms | 1 310,7 | 272,2 ms |
| 400 | 15,0 | 26 090 ms (7 errores) | 1 330,3 | 544,4 ms |

Se cumple el objetivo hasta 200 usuarios. Con 400, la CPU se satura (el rendimiento se estabiliza en
≈ 1 300 sol/s) y la latencia crece de forma lineal sin errores. Para más usuarios hay que escalar
horizontalmente (varias instancias detrás de un balanceador); la principal fuente de consumo restante es
el cálculo de recomendaciones (35 % de la CPU), candidata a calcularse fuera de línea.

## 7. Seguridad de la base de datos

`src/main/resources/db/seguridad.sql`: usuario `libmanage_app` con solo SELECT, INSERT, UPDATE y DELETE
sobre el esquema de la aplicación (sin DROP, ALTER ni GRANT), `REQUIRE SSL`, restricciones CHECK sobre ejemplares
y disponibles. `JdbcLibroRepositorioIT` comprueba en MySQL real que el usuario no puede borrar tablas,
que la conexión sin TLS se rechaza, que la inyección no tiene efecto y que el ajuste concurrente de stock
es atómico.

## 8. Automatización

`.github/workflows/pruebas.yml` ejecuta en cada *push* y *pull request*: (1) `mvn verify` con un servicio
MySQL 8.4, el esquema y el usuario de mínimo privilegio, y el umbral de cobertura de JaCoCo; (2) las
pruebas de aceptación en Chromium, que publican las capturas como artefacto.
