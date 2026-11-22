"""Pruebas de aceptación automatizadas de LibManage (caja negra, desde el navegador).

Cada prueba corresponde a un criterio de aceptación de una historia de usuario (HU) y se ejecuta
contra el sistema completo: interfaz web + API Java + repositorios. Levanta el servidor, abre
Chromium con Playwright, guarda una captura por historia en e2e/capturas y deja un resumen en
e2e/resultados.txt.

Requisitos: Java 21 con las clases compiladas en build/classes y `pip install playwright`.
Uso: python e2e/test_aceptacion.py
"""
import os
import socket
import subprocess
import sys
import time
import unittest
import urllib.request
from pathlib import Path

from playwright.sync_api import expect, sync_playwright

RAIZ = Path(__file__).resolve().parent.parent
CAPTURAS = RAIZ / "e2e" / "capturas"
CLASES = os.environ.get("LIBMANAGE_CLASES", str(RAIZ / "build" / "classes"))
ESTUDIANTE = ("ana.torres@uautonoma.cl", "Lectura2026!")
ADMIN = ("admin@uautonoma.cl", "Admin2026!")
LIMITE_RESPUESTA_MS = 1000  # criterio no funcional: la interfaz responde en menos de 1 s


def puerto_libre():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def isbn13(doce):
    suma = sum(int(d) * (1 if i % 2 == 0 else 3) for i, d in enumerate(doce))
    return doce + str((10 - suma % 10) % 10)


class Aceptacion(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        CAPTURAS.mkdir(parents=True, exist_ok=True)
        cls.puerto = puerto_libre()
        cls.base = f"http://127.0.0.1:{cls.puerto}"
        entorno = dict(os.environ, LIBMANAGE_PUERTO=str(cls.puerto), LIBMANAGE_ITERACIONES="10000",
                       LIBMANAGE_LIBROS="2000", LIBMANAGE_LECTORES="300")
        cls.servidor = subprocess.Popen(["java", "-cp", CLASES, "cl.libmanage.web.Servidor"], env=entorno,
                                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(120):
            try:
                urllib.request.urlopen(cls.base + "/api/salud", timeout=1)
                break
            except OSError:
                time.sleep(0.5)
        else:
            cls.servidor.kill()
            raise RuntimeError("El servidor no inició")
        cls.pw = sync_playwright().start()
        cls.navegador = cls.pw.chromium.launch()

    @classmethod
    def tearDownClass(cls):
        cls.navegador.close()
        cls.pw.stop()
        cls.servidor.terminate()
        cls.servidor.wait(timeout=10)

    def setUp(self):
        self.contexto = self.navegador.new_context(viewport={"width": 1280, "height": 860}, locale="es-CL")
        self.pagina = self.contexto.new_page()
        self.pagina.goto(self.base)

    def tearDown(self):
        self.contexto.close()

    # -- utilidades ---------------------------------------------------------------------------------
    def ingresar(self, credenciales):
        p = self.pagina
        p.fill("#email", credenciales[0])
        p.fill("#contrasena", credenciales[1])
        p.click("#form-login button[type=submit]")
        expect(p.locator("#vista-app")).to_be_visible()
        expect(p.locator("#vista-login")).to_be_hidden()  # el formulario de ingreso no debe quedar a la vista

    def buscar(self, **campos):
        p = self.pagina
        for campo, valor in campos.items():
            if campo == "categoria":
                p.select_option("#categoria", valor)
            elif campo == "disponibles":
                p.set_checked("#disponibles", valor)
            else:
                p.fill(f"#{campo}", str(valor))
        inicio = time.perf_counter()
        with p.expect_response(lambda r: "/api/libros?" in r.url):
            p.click("#form-busqueda button[type=submit]")
        transcurrido = (time.perf_counter() - inicio) * 1000
        self.assertLess(transcurrido, LIMITE_RESPUESTA_MS, "la búsqueda tardó demasiado")
        return p.locator("#resultados li")

    def captura(self, nombre):
        self.pagina.screenshot(path=str(CAPTURAS / f"{nombre}.png"), full_page=True)

    # -- HU-01 Búsqueda avanzada --------------------------------------------------------------------
    def test_hu01_busqueda_avanzada_por_categoria_y_rango_de_anios(self):
        """Como estudiante quiero combinar palabra clave, categoría y años para encontrar un libro."""
        self.ingresar(ESTUDIANTE)
        # El rango debe incluir ambos límites (valor frontera): Sommerville y Myers son de 2011.
        resultados = self.buscar(q="software", categoria="Informática", desde=2008, hasta=2011)
        expect(resultados.first).to_be_visible()
        detalles = resultados.locator(".detalle").all_inner_texts()
        self.assertTrue(detalles)
        for detalle in detalles:
            self.assertIn("Informática", detalle)
            anio = int(detalle.split(" · ")[2])
            self.assertTrue(2008 <= anio <= 2011, detalle)
        expect(self.pagina.locator("#resultados")).to_contain_text("Ingeniería de software")
        expect(self.pagina.locator("#resultados")).to_contain_text("El arte de probar software")
        self.captura("hu01_busqueda_avanzada")

    def test_hu01_busqueda_sin_resultados_informa_cero(self):
        self.ingresar(ESTUDIANTE)
        self.buscar(q="xyzinexistente")
        expect(self.pagina.locator("#total-resultados")).to_have_text("0 resultados")

    # -- HU-02 Préstamo y devolución ----------------------------------------------------------------
    def test_hu02_prestamo_y_devolucion_actualizan_disponibilidad(self):
        """Como estudiante quiero pedir un libro disponible y devolverlo, viendo el estado al día."""
        p = self.pagina
        self.ingresar(ESTUDIANTE)
        libro = self.buscar(q="Cálculo de una variable").first
        expect(libro).to_contain_text("3 de 3 disponibles")
        libro.get_by_role("button", name="Pedir préstamo").click()
        expect(p.locator("#mensaje")).to_contain_text("Préstamo registrado: Cálculo de una variable")
        expect(p.locator("#resultados li").first).to_contain_text("2 de 3 disponibles")
        self.captura("hu02_prestamo_registrado")

        p.click("button[data-vista=prestamos]")
        fila = p.locator("#tabla-prestamos tr", has_text="Cálculo de una variable")
        expect(fila).to_contain_text("ACTIVO")
        fila.get_by_role("button", name="Devolver").click()
        expect(p.locator("#mensaje")).to_contain_text("Devolución registrada")
        expect(p.locator("#tabla-prestamos tr", has_text="Cálculo de una variable").first).to_contain_text("DEVUELTO")
        self.captura("hu02_devolucion")

    def test_hu02_no_permite_pedir_dos_veces_el_mismo_libro(self):
        p = self.pagina
        self.ingresar(ESTUDIANTE)
        libro = self.buscar(q="Álgebra lineal").first
        libro.get_by_role("button", name="Pedir préstamo").click()
        expect(p.locator("#mensaje")).to_contain_text("Préstamo registrado")
        p.locator("#resultados li").first.get_by_role("button", name="Pedir préstamo").click()
        expect(p.locator("#mensaje")).to_have_text("Ya tiene un ejemplar de este libro")
        expect(p.locator("#mensaje")).to_have_class("mensaje error")
        self.captura("hu02_regla_un_ejemplar")
        p.click("button[data-vista=prestamos]")  # deja el estado limpio para otras pruebas
        p.locator("#tabla-prestamos tr", has_text="Álgebra lineal").get_by_role("button", name="Devolver").click()
        expect(p.locator("#mensaje")).to_contain_text("Devolución registrada")

    # -- HU-03 Recomendaciones ----------------------------------------------------------------------
    def test_hu03_recomendaciones_personalizadas_sin_libros_ya_leidos(self):
        """Como estudiante quiero recibir sugerencias según mi historial, sin libros que ya leí."""
        p = self.pagina
        self.ingresar(ESTUDIANTE)
        p.click("button[data-vista=recomendados]")
        lista = p.locator("#lista-recomendados li")
        expect(lista.first).to_be_visible()
        self.assertGreaterEqual(lista.count(), 3)
        titulos = lista.locator("strong").all_inner_texts()
        self.assertNotIn("Ingeniería de software", titulos)  # ya leídos por Ana
        self.assertNotIn("Código limpio", titulos)
        categorias = [d.split(" · ")[1] for d in lista.locator(".detalle").all_inner_texts()]
        self.assertGreaterEqual(categorias.count("Informática"), 1)  # coherentes con su historial
        self.captura("hu03_recomendaciones")

    # -- HU-04 Acceso seguro -------------------------------------------------------------------------
    def test_hu04_login_fallido_muestra_mensaje_generico(self):
        """Como usuario quiero que un error de acceso no revele si mi correo existe."""
        p = self.pagina
        p.fill("#email", ESTUDIANTE[0])
        p.fill("#contrasena", "incorrecta")
        p.click("#form-login button[type=submit]")
        expect(p.locator("#mensaje")).to_have_text("Credenciales inválidas")
        p.fill("#email", "nadie@uautonoma.cl")
        p.click("#form-login button[type=submit]")
        expect(p.locator("#mensaje")).to_have_text("Credenciales inválidas")
        expect(p.locator("#vista-app")).to_be_hidden()
        self.captura("hu04_login_generico")

    def test_hu04_texto_malicioso_se_muestra_como_texto(self):
        """Un título con HTML se muestra literal (sin ejecutar scripts): protección contra XSS."""
        p = self.pagina
        dialogos = []
        p.on("dialog", lambda d: (dialogos.append(d.message), d.dismiss()))
        self.ingresar(ADMIN)
        p.click("button[data-vista=admin]")
        p.fill("#nuevo-isbn", isbn13("978999000001"))
        p.fill("#nuevo-titulo", "<img src=x onerror=alert(1)> Seguridad web")
        p.fill("#nuevo-autor", "Equipo QA")
        p.fill("#nuevo-categoria", "Informática")
        p.fill("#nuevo-anio", "2024")
        p.fill("#nuevo-ejemplares", "2")
        p.click("#form-libro button[type=submit]")
        expect(p.locator("#mensaje")).to_contain_text("Libro agregado")
        p.click("button[data-vista=buscar]")
        self.buscar(q="Seguridad web")
        expect(p.locator("#resultados strong").first).to_have_text("<img src=x onerror=alert(1)> Seguridad web")
        self.assertEqual(dialogos, [])
        self.assertEqual(p.locator("#resultados img").count(), 0)

    # -- HU-05 Gestión de inventario ----------------------------------------------------------------
    def test_hu05_administrador_agrega_libro_y_queda_buscable(self):
        """Como administrador quiero registrar un libro y que los lectores lo encuentren de inmediato."""
        p = self.pagina
        self.ingresar(ADMIN)
        expect(p.locator("#pestana-admin")).to_be_visible()
        p.click("button[data-vista=admin]")
        p.fill("#nuevo-isbn", isbn13("978999000002"))
        p.fill("#nuevo-titulo", "Pruebas de aceptación automatizadas")
        p.fill("#nuevo-autor", "Patricio Farías")
        p.fill("#nuevo-categoria", "Informática")
        p.fill("#nuevo-anio", "2026")
        p.fill("#nuevo-ejemplares", "4")
        p.click("#form-libro button[type=submit]")
        expect(p.locator("#mensaje")).to_contain_text("Libro agregado con ID")
        self.captura("hu05_libro_agregado")
        p.click("button[data-vista=buscar]")
        resultado = self.buscar(q="aceptación automatizadas").first
        expect(resultado).to_contain_text("4 de 4 disponibles")

    def test_hu05_estudiante_no_ve_ni_usa_administracion(self):
        p = self.pagina
        self.ingresar(ESTUDIANTE)
        expect(p.locator("#pestana-admin")).to_be_hidden()
        respuesta = p.evaluate("""async () => {
            const r = await fetch('/api/admin/libros/1/ejemplares', {method: 'PUT', body: '{"ejemplares": 9}'});
            return r.status; }""")
        self.assertEqual(respuesta, 401)  # sin token del administrador la API lo rechaza

    # -- HU-06 Uso en dispositivos móviles -----------------------------------------------------------
    def test_hu06_interfaz_usable_en_movil(self):
        """Como estudiante quiero buscar desde el teléfono sin desplazamiento horizontal."""
        contexto = self.navegador.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
        p = contexto.new_page()
        p.goto(self.base)
        p.fill("#email", ESTUDIANTE[0])
        p.fill("#contrasena", ESTUDIANTE[1])
        p.click("#form-login button[type=submit]")
        expect(p.locator("#resultados li").first).to_be_visible()
        ancho = p.evaluate("document.documentElement.scrollWidth")
        self.assertLessEqual(ancho, 390)
        p.screenshot(path=str(CAPTURAS / "hu06_movil.png"))
        contexto.close()


if __name__ == "__main__":
    resultado = unittest.main(exit=False, verbosity=2).result
    total = resultado.testsRun
    fallas = len(resultado.failures) + len(resultado.errors)
    (RAIZ / "e2e" / "resultados.txt").write_text(
        f"Pruebas de aceptación: {total} ejecutadas, {total - fallas} aprobadas, {fallas} fallidas\n", encoding="utf-8")
    sys.exit(1 if fallas else 0)
