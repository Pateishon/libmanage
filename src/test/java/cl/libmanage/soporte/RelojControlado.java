package cl.libmanage.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Reloj que las pruebas pueden adelantar, para probar vencimientos sin esperar. */
public final class RelojControlado extends Clock {
    private volatile Instant ahora;
    private final ZoneId zona;

    public RelojControlado(Instant inicio, ZoneId zona) {
        this.ahora = inicio;
        this.zona = zona;
    }

    public void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId nuevaZona) {
        return new RelojControlado(ahora, nuevaZona);
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
