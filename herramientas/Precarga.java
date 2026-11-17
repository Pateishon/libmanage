import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Carga todas las clases de producción antes de ejecutar JUnit, para que la medición de cobertura
 * cuente también las clases que ninguna prueba usa (y no las omita del total).
 */
public final class Precarga {
    public static void main(String[] args) throws Exception {
        Path raiz = Path.of(System.getProperty("cobertura.clases", "build/classes"));
        List<String> clases;
        try (Stream<Path> archivos = Files.walk(raiz)) {
            clases = archivos.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> raiz.relativize(p).toString().replace('/', '.').replaceAll("\\.class$", ""))
                    .toList();
        }
        for (String clase : clases) {
            Class.forName(clase, true, Precarga.class.getClassLoader());
        }
        org.junit.runner.JUnitCore.main(args);
    }
}
