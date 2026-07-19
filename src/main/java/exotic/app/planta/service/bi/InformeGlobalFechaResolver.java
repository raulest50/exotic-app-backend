package exotic.app.planta.service.bi;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class InformeGlobalFechaResolver {

    public static final int MAX_DIAS_RANGO = 31;

    private InformeGlobalFechaResolver() {
    }

    public static Rango resolve(LocalDate fecha, LocalDate fechaDesde, LocalDate fechaHasta) {
        boolean fechaUnica = fecha != null;
        boolean inicio = fechaDesde != null;
        boolean fin = fechaHasta != null;
        if (fechaUnica && (inicio || fin)) {
            throw new IllegalArgumentException("Use fecha o fechaDesde/fechaHasta, no ambas opciones.");
        }
        if (fechaUnica) {
            return new Rango(fecha, fecha);
        }
        if (!inicio || !fin) {
            throw new IllegalArgumentException("Debe enviar fecha o el rango completo fechaDesde/fechaHasta.");
        }
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta.");
        }
        if (ChronoUnit.DAYS.between(fechaDesde, fechaHasta) + 1 > MAX_DIAS_RANGO) {
            throw new IllegalArgumentException("El rango maximo permitido para este informe es de 31 dias.");
        }
        return new Rango(fechaDesde, fechaHasta);
    }

    public record Rango(LocalDate fechaDesde, LocalDate fechaHasta) {
    }
}
