# LibManage

Sistema de gestión de bibliotecas (backend Java 21, interfaz web, MySQL) con búsqueda avanzada,
recomendaciones, inventario y préstamos. Proyecto de pruebas de software — semana 10, Ingeniería de
Software, Universidad Autónoma de Chile.

## Ejecutar
```bash
mkdir -p build/classes && javac -d build/classes $(find src/main/java -name "*.java")
cp -r src/main/resources/* build/classes/
LIBMANAGE_ITERACIONES=10000 java -cp build/classes cl.libmanage.web.Servidor   # http://127.0.0.1:8080
```
Usuarios de prueba: `ana.torres@uautonoma.cl` / `Lectura2026!` (estudiante), `admin@uautonoma.cl` /
`Admin2026!` (administrador). Para usar MySQL: `LIBMANAGE_DB_URL`, `LIBMANAGE_DB_USUARIO`, `LIBMANAGE_DB_CLAVE`.

## Pruebas
| Qué | Comando |
|---|---|
| JUnit (unitarias, API, seguridad, integración) | `scripts/pruebas.sh` o `mvn verify` |
| Cobertura de líneas sin Maven | `scripts/cobertura.sh` |
| Aceptación en navegador | `pip install playwright && python -m playwright install chromium && python e2e/test_aceptacion.py` |
| Carga | `carga/ejecutar.sh build/classes v1.1 carga/resultados/carga.csv 15 10 50 100 200 400` |

Las pruebas con MySQL se ejecutan si existe `LIBMANAGE_TEST_DB_URL` (en GitHub Actions siempre).
Plan, estrategia y resultados: [`docs/PLAN_DE_PRUEBAS.md`](docs/PLAN_DE_PRUEBAS.md).
Aceptación con usuarios: [`docs/GUIA_UAT.md`](docs/GUIA_UAT.md).
