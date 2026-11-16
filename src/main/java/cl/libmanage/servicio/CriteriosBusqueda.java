package cl.libmanage.servicio;

import cl.libmanage.dominio.ErrorNegocio;

/** Criterios de la búsqueda avanzada. {@link #validar()} aplica los límites de entrada. */
public record CriteriosBusqueda(String texto, String autor, String categoria, Integer anioDesde, Integer anioHasta,
                                boolean soloDisponibles, Orden orden, int pagina, int tamano) {

    public enum Orden { RELEVANCIA, TITULO, ANIO_DESC }

    public static final int MAX_LARGO_TEXTO = 100;
    public static final int MAX_TAMANO = 50;
    public static final int ANIO_MINIMO = 1450;
    public static final int ANIO_MAXIMO = 2100;

    public static CriteriosBusqueda texto(String texto) {
        return new CriteriosBusqueda(texto, null, null, null, null, false, Orden.RELEVANCIA, 1, 20);
    }

    public CriteriosBusqueda validar() {
        verificarLargo(texto, "texto");
        verificarLargo(autor, "autor");
        verificarLargo(categoria, "categoría");
        if (pagina < 1) {
            throw new ErrorNegocio(400, "La página debe ser 1 o mayor");
        }
        if (tamano < 1 || tamano > MAX_TAMANO) {
            throw new ErrorNegocio(400, "El tamaño de página debe estar entre 1 y " + MAX_TAMANO);
        }
        verificarAnio(anioDesde);
        verificarAnio(anioHasta);
        if (anioDesde != null && anioHasta != null && anioDesde > anioHasta) {
            throw new ErrorNegocio(400, "El año inicial no puede ser mayor que el final");
        }
        return this;
    }

    private static void verificarLargo(String valor, String campo) {
        if (valor != null && valor.length() > MAX_LARGO_TEXTO) {
            throw new ErrorNegocio(400, "El campo " + campo + " admite hasta " + MAX_LARGO_TEXTO + " caracteres");
        }
    }

    private static void verificarAnio(Integer anio) {
        if (anio != null && (anio < ANIO_MINIMO || anio > ANIO_MAXIMO)) {
            throw new ErrorNegocio(400, "El año debe estar entre " + ANIO_MINIMO + " y " + ANIO_MAXIMO);
        }
    }
}
