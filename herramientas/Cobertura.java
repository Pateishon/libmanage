import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Cobertura de líneas sin dependencias externas: ejecuta las pruebas JUnit en una JVM depurada (JDI),
 * pone un punto de interrupción en cada línea de las clases de LibManage y registra las que se ejecutan.
 * Equivale a la métrica LINE de JaCoCo, que se usa en la integración continua.
 *
 * Uso: scripts/cobertura.sh (compila Precarga y lanza las pruebas a través de ella).
 */
public final class Cobertura {
    private static final String[] PAQUETES = {"cl.libmanage.dominio.*", "cl.libmanage.repositorio.*",
        "cl.libmanage.servicio.*", "cl.libmanage.seguridad.*", "cl.libmanage.web.*"};

    public static void main(String[] args) throws Exception {
        String classpath = args[0];
        StringBuilder clases = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            clases.append(' ').append(args[i]);
        }
        LaunchingConnector conector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> argumentos = conector.defaultArguments();
        argumentos.get("main").setValue("Precarga" + clases);
        argumentos.get("options").setValue("-cp " + classpath);
        VirtualMachine vm = conector.launch(argumentos);
        drenar(vm.process().getInputStream(), System.out);
        drenar(vm.process().getErrorStream(), System.err);

        EventRequestManager peticiones = vm.eventRequestManager();
        for (String paquete : PAQUETES) {
            ClassPrepareRequest preparacion = peticiones.createClassPrepareRequest();
            preparacion.addClassFilter(paquete);
            preparacion.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            preparacion.enable();
        }
        Map<String, Set<Integer>> lineas = new TreeMap<>();
        Map<String, Set<Integer>> cubiertas = new TreeMap<>();
        vm.resume();
        boolean activa = true;
        while (activa) {
            EventSet eventos = vm.eventQueue().remove();
            for (Event evento : eventos) {
                if (evento instanceof ClassPrepareEvent preparada) {
                    registrar(preparada.referenceType(), peticiones, lineas);
                } else if (evento instanceof BreakpointEvent punto) {
                    cubiertas.computeIfAbsent(archivo(punto.location()), k -> new TreeSet<>())
                            .add(punto.location().lineNumber());
                    peticiones.deleteEventRequest(punto.request());
                } else if (evento instanceof VMDeathEvent || evento instanceof VMDisconnectEvent) {
                    activa = false;
                }
            }
            if (activa) {
                eventos.resume();
            }
        }
        reportar(lineas, cubiertas);
    }

    private static void registrar(ReferenceType tipo, EventRequestManager peticiones, Map<String, Set<Integer>> lineas) {
        try {
            for (Location ubicacion : tipo.allLineLocations()) {
                if (ubicacion.lineNumber() < 1) {
                    continue;
                }
                lineas.computeIfAbsent(archivo(ubicacion), k -> new TreeSet<>()).add(ubicacion.lineNumber());
                BreakpointRequest punto = peticiones.createBreakpointRequest(ubicacion);
                punto.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                punto.enable();
            }
        } catch (AbsentInformationException sinDepuracion) {
            // clase compilada sin información de líneas
        }
    }

    private static String archivo(Location ubicacion) {
        try {
            String paquete = ubicacion.declaringType().name();
            paquete = paquete.substring(0, paquete.lastIndexOf('.'));
            return paquete + "/" + ubicacion.sourceName();
        } catch (AbsentInformationException e) {
            return ubicacion.declaringType().name();
        }
    }

    private static void reportar(Map<String, Set<Integer>> lineas, Map<String, Set<Integer>> cubiertas) {
        int total = 0;
        int cubiertasTotal = 0;
        Map<String, int[]> porPaquete = new TreeMap<>();
        System.out.println("\nCOBERTURA DE LÍNEAS POR ARCHIVO");
        for (Map.Entry<String, Set<Integer>> e : lineas.entrySet()) {
            Set<Integer> hechas = new TreeSet<>(cubiertas.getOrDefault(e.getKey(), Set.of()));
            hechas.retainAll(e.getValue());
            Set<Integer> faltantes = new TreeSet<>(e.getValue());
            faltantes.removeAll(hechas);
            total += e.getValue().size();
            cubiertasTotal += hechas.size();
            String paquete = e.getKey().substring(0, e.getKey().indexOf('/'));
            int[] acumulado = porPaquete.computeIfAbsent(paquete, k -> new int[2]);
            acumulado[0] += hechas.size();
            acumulado[1] += e.getValue().size();
            System.out.printf("%-52s %4d/%-4d %6.1f%%  sin cubrir: %s%n", e.getKey(), hechas.size(), e.getValue().size(),
                    100.0 * hechas.size() / e.getValue().size(), faltantes);
        }
        System.out.println("\nPOR PAQUETE");
        porPaquete.forEach((p, v) -> System.out.printf("%-28s %4d/%-4d %6.1f%%%n", p, v[0], v[1], 100.0 * v[0] / v[1]));
        System.out.printf("%nTOTAL %d/%d = %.1f%%%n", cubiertasTotal, total, 100.0 * cubiertasTotal / total);
    }

    private static void drenar(InputStream entrada, OutputStream salida) {
        Thread hilo = new Thread(() -> {
            try {
                entrada.transferTo(salida);
            } catch (Exception ignorado) {
                // fin del proceso depurado
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }
}
