package cl.libmanage.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bloquea una cuenta tras demasiados intentos fallidos dentro de una ventana de tiempo. */
public final class LimitadorIntentos {
    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;
    private final Map<String, Deque<Instant>> fallos = new ConcurrentHashMap<>();

    public LimitadorIntentos(int maximo, Duration ventana, Clock reloj) {
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    public boolean bloqueado(String clave) {
        Deque<Instant> registro = fallos.get(clave);
        if (registro == null) {
            return false;
        }
        synchronized (registro) {
            depurar(registro);
            return registro.size() >= maximo;
        }
    }

    public void registrarFallo(String clave) {
        Deque<Instant> registro = fallos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (registro) {
            depurar(registro);
            registro.addLast(reloj.instant());
        }
    }

    public void limpiar(String clave) {
        fallos.remove(clave);
    }

    private void depurar(Deque<Instant> registro) {
        Instant limite = reloj.instant().minus(ventana);
        while (!registro.isEmpty() && registro.peekFirst().isBefore(limite)) {
            registro.pollFirst();
        }
    }
}
