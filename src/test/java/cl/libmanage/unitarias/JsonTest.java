package cl.libmanage.unitarias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import cl.libmanage.web.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class JsonTest {
    @Test
    public void escribeEscapandoCaracteresPeligrosos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("titulo", "<script>alert(\"x\")</script> & más");
        datos.put("n", 3);
        datos.put("lista", List.of(true, "a"));
        datos.put("nulo", null);
        String json = Json.escribir(datos);
        assertFalse(json.contains("<script>"));
        assertEquals("{\"titulo\":\"\\u003cscript\\u003ealert(\\\"x\\\")\\u003c/script\\u003e \\u0026 más\","
                + "\"n\":3,\"lista\":[true,\"a\"],\"nulo\":null}", json);
    }

    @Test
    public void leeObjetosAnidadosYEscapes() {
        Map<String, Object> m = Json.leerObjeto("{ \"a\": 1, \"b\": \"x\\\"y\\u00e1\", \"c\": [1, 2.5, false, null],"
                + " \"d\": {\"e\": -7} }");
        assertEquals(1L, m.get("a"));
        assertEquals("x\"yá", m.get("b"));
        assertEquals(List.of(1L, 2.5, false), ((List<?>) m.get("c")).subList(0, 3));
        assertEquals(-7L, ((Map<?, ?>) m.get("d")).get("e"));
    }

    @Test
    public void rechazaJsonMalFormado() {
        for (String malo : new String[] {"", "[]", "{", "{\"a\":}", "{\"a\":1} extra", "{\"a\":\"sin cerrar}",
            "{\"a\":tru}", "{\"a\":\"\\u12\"}", "{a:1}"}) {
            try {
                Json.leerObjeto(malo);
                fail("Debió rechazar: " + malo);
            } catch (IllegalArgumentException esperado) {
                // correcto
            }
        }
    }
}
