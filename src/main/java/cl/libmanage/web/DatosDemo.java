package cl.libmanage.web;

import cl.libmanage.dominio.Libro;
import cl.libmanage.dominio.Prestamo;
import cl.libmanage.dominio.Rol;
import cl.libmanage.dominio.Usuario;
import cl.libmanage.repositorio.LibroRepositorio;
import cl.libmanage.repositorio.PrestamoRepositorio;
import cl.libmanage.repositorio.UsuarioRepositorio;
import cl.libmanage.seguridad.Contrasenas;
import cl.libmanage.servicio.Isbn;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Datos de demostración reproducibles (semilla fija): catálogo, usuarios y un historial de préstamos
 * devueltos que alimenta las recomendaciones. Todos los nombres y RUT son ficticios.
 */
public final class DatosDemo {
    public static final String EMAIL_ESTUDIANTE = "ana.torres@uautonoma.cl";
    public static final String CLAVE_ESTUDIANTE = "Lectura2026!";
    public static final String EMAIL_DOCENTE = "luis.rojas@uautonoma.cl";
    public static final String CLAVE_DOCENTE = "Docencia2026!";
    public static final String EMAIL_ADMIN = "admin@uautonoma.cl";
    public static final String CLAVE_ADMIN = "Admin2026!";
    public static final String CLAVE_LECTORES = "Lector2026!";

    static final String[] CATEGORIAS = {"Informática", "Matemática", "Historia", "Literatura", "Derecho", "Medicina",
        "Economía"};
    private static final String[][] DESTACADOS = {
        {"Ingeniería de software", "Ian Sommerville", "Informática", "2011"},
        {"Código limpio", "Robert C. Martin", "Informática", "2008"},
        {"Refactorización", "Martin Fowler", "Informática", "2018"},
        {"Patrones de diseño", "Erich Gamma", "Informática", "1994"},
        {"Pruebas de software: un enfoque práctico", "Paul Jorgensen", "Informática", "2013"},
        {"El arte de probar software", "Glenford Myers", "Informática", "2011"},
        {"Bases de datos relacionales", "Carlos Coronel", "Informática", "2019"},
        {"Cálculo de una variable", "James Stewart", "Matemática", "2012"},
        {"Álgebra lineal", "Gilbert Strang", "Matemática", "2016"},
        {"Historia de Chile", "Sergio Villalobos", "Historia", "1974"},
        {"Cien años de soledad", "Gabriel García Márquez", "Literatura", "1967"},
        {"Veinte poemas de amor", "Pablo Neruda", "Literatura", "1924"},
        {"Desolación", "Gabriela Mistral", "Literatura", "1922"},
        {"Derecho constitucional", "Alejandro Silva", "Derecho", "2010"},
        {"Fisiología médica", "Arthur Guyton", "Medicina", "2016"},
        {"Principios de economía", "Gregory Mankiw", "Economía", "2018"},
    };
    private static final String[] PALABRAS = {"introducción", "fundamentos", "teoría", "práctica", "análisis",
        "sistemas", "datos", "redes", "historia", "moderna", "aplicada", "avanzada", "métodos", "estructuras",
        "diseño", "gestión", "política", "sociedad", "algoritmos", "ética"};
    private static final String[] NOMBRES = {"Ana", "Luis", "Marta", "Pedro", "Isabel", "Jorge", "Camila", "Diego",
        "Rosa", "Tomás", "Elena", "Pablo", "Valentina", "Ignacio"};
    private static final String[] APELLIDOS = {"González", "Muñoz", "Rojas", "Díaz", "Pérez", "Soto", "Contreras",
        "Silva", "Araya", "Torres", "Fuentes", "Vargas"};

    private DatosDemo() {
    }

    public static void poblar(LibroRepositorio libros, UsuarioRepositorio usuarios, PrestamoRepositorio prestamos,
                              Contrasenas contrasenas, Clock reloj, int cantidadLibros, int cantidadLectores) {
        Random azar = new Random(42);
        List<Libro> catalogo = new ArrayList<>();
        long correlativo = 100_000_000L;
        for (String[] d : DESTACADOS) {
            catalogo.add(libros.guardar(new Libro(0, Isbn.conVerificador("978" + correlativo++), d[0], d[1], d[2],
                    Integer.parseInt(d[3]), 3, 3)));
        }
        while (catalogo.size() < cantidadLibros) {
            String titulo = capitalizar(PALABRAS[azar.nextInt(PALABRAS.length)]) + " de "
                    + PALABRAS[azar.nextInt(PALABRAS.length)] + " " + PALABRAS[azar.nextInt(PALABRAS.length)];
            String autor = NOMBRES[azar.nextInt(NOMBRES.length)] + " " + APELLIDOS[azar.nextInt(APELLIDOS.length)];
            int ejemplares = 1 + azar.nextInt(4);
            catalogo.add(libros.guardar(new Libro(0, Isbn.conVerificador("978" + correlativo++), titulo, autor,
                    CATEGORIAS[azar.nextInt(CATEGORIAS.length)], 1950 + azar.nextInt(76), ejemplares, ejemplares)));
        }

        Usuario ana = usuarios.guardar(new Usuario(0, "Ana Torres", EMAIL_ESTUDIANTE, "12.345.678-5",
                contrasenas.hash(CLAVE_ESTUDIANTE), Rol.ESTUDIANTE, true));
        usuarios.guardar(new Usuario(0, "Luis Rojas", EMAIL_DOCENTE, "9.876.543-3",
                contrasenas.hash(CLAVE_DOCENTE), Rol.DOCENTE, true));
        usuarios.guardar(new Usuario(0, "Administración Biblioteca", EMAIL_ADMIN, "11.111.111-1",
                contrasenas.hash(CLAVE_ADMIN), Rol.ADMIN, true));

        // Lectores sintéticos con historial: leen sobre todo en una o dos categorías.
        String hashLectores = contrasenas.hash(CLAVE_LECTORES);  // mismo hash para no demorar el arranque
        LocalDate hoy = LocalDate.now(reloj);
        for (int i = 1; i <= cantidadLectores; i++) {
            Usuario lector = usuarios.guardar(new Usuario(0, "Lector " + i, "lector" + i + "@uautonoma.cl",
                    (10_000_000 + i) + "-" + (i % 10), hashLectores, Rol.ESTUDIANTE, true));
            String favorita = CATEGORIAS[azar.nextInt(CATEGORIAS.length)];
            int lecturas = 3 + azar.nextInt(6);
            for (int j = 0; j < lecturas; j++) {
                Libro libro = favorita.equals("Informática") && azar.nextBoolean()
                        ? catalogo.get(azar.nextInt(7))  // los clásicos de informática se leen juntos
                        : catalogo.get(azar.nextInt(Math.min(catalogo.size(), 400)));
                if (!libro.categoria().equals(favorita) && azar.nextInt(4) != 0) {
                    continue;
                }
                LocalDate inicio = hoy.minusDays(30 + azar.nextInt(300));
                prestamos.guardar(new Prestamo(0, lector.id(), libro.id(), inicio, inicio.plusDays(7),
                        inicio.plusDays(1 + azar.nextInt(7))));
            }
        }
        // Historial de Ana: dos libros de Informática ya devueltos.
        LocalDate inicio = hoy.minusDays(60);
        prestamos.guardar(new Prestamo(0, ana.id(), catalogo.get(0).id(), inicio, inicio.plusDays(7), inicio.plusDays(5)));
        prestamos.guardar(new Prestamo(0, ana.id(), catalogo.get(1).id(), inicio.plusDays(10), inicio.plusDays(17),
                inicio.plusDays(15)));
    }

    private static String capitalizar(String palabra) {
        return Character.toUpperCase(palabra.charAt(0)) + palabra.substring(1);
    }
}
