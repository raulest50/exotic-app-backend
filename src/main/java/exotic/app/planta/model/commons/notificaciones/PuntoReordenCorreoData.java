package exotic.app.planta.model.commons.notificaciones;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Snapshot de lectura para el correo; no modifica el contrato de la campana. */
public record PuntoReordenCorreoData(
        LocalDate fechaCorte,
        Identidad identidad,
        List<MaterialFila> pendientesOrdenar,
        List<MaterialConOcmFila> pendientesIngresoAlmacen,
        List<MaterialFila> sinPuntoReorden,
        List<SeguimientoFila> seguimiento,
        List<SeguimientoFila> vencidas,
        long paresSinDatosSeguimiento,
        int porcentajeSeguimiento,
        int minimoLeadTimeDias
) {
    public PuntoReordenCorreoData {
        pendientesOrdenar = List.copyOf(pendientesOrdenar);
        pendientesIngresoAlmacen = List.copyOf(pendientesIngresoAlmacen);
        sinPuntoReorden = List.copyOf(sinPuntoReorden);
        seguimiento = List.copyOf(seguimiento);
        vencidas = List.copyOf(vencidas);
    }

    public long totalEnAlerta() {
        return (long) pendientesOrdenar.size() + pendientesIngresoAlmacen.size();
    }

    public record Identidad(
            String nombreComercial, String razonSocial, String telefono, String email
    ) {}

    public record MaterialFila(
            String productoId, String nombre, String tipoMaterialLabel,
            double stockActual, double puntoReorden, String tipoUnidades
    ) {}

    public record OcmFila(int ordenCompraId, LocalDateTime fechaEmision) {}

    public record MaterialConOcmFila(MaterialFila material, List<OcmFila> ocms) {
        public MaterialConOcmFila {
            ocms = List.copyOf(ocms);
        }
    }

    public record SeguimientoFila(
            MaterialFila material,
            int ordenCompraId,
            String proveedor,
            LocalDate fechaEnvio,
            LocalDate fechaLimite,
            int leadTimeDias,
            long diasTranscurridos,
            double cantidadPendiente
    ) {
        public long diasExceso() {
            return Math.max(0L, diasTranscurridos - leadTimeDias);
        }
    }
}
