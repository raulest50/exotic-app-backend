package exotic.app.planta.service.produccion;

import java.time.LocalDate;

final class MpsSemanalFechaPlanificadaCalculator {

    private MpsSemanalFechaPlanificadaCalculator() {
    }

    static FechasPlanificadas desdeFechaEntrega(LocalDate fechaEntrega, int tiempoDiasFabricacion) {
        if (fechaEntrega == null) {
            throw new IllegalArgumentException("fechaEntrega es obligatoria.");
        }
        int diasFabricacion = Math.max(tiempoDiasFabricacion, 0);
        return new FechasPlanificadas(
                fechaEntrega.minusDays(diasFabricacion),
                fechaEntrega
        );
    }

    record FechasPlanificadas(LocalDate fechaLanzamiento, LocalDate fechaFinalPlanificada) {
    }
}
