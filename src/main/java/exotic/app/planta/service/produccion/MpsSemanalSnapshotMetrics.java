package exotic.app.planta.service.produccion;

import exotic.app.planta.model.produccion.dto.PropuestaMpsSemanalResponseDTO;

import java.util.Objects;

record MpsSemanalSnapshotMetrics(
        int totalOrdenesEsperadas,
        int totalBloquesNoProgramados,
        int totalLotesNoProgramados,
        double totalUnidadesNoProgramadas
) {

    static MpsSemanalSnapshotMetrics fromSnapshot(PropuestaMpsSemanalResponseDTO snapshot) {
        if (snapshot == null || snapshot.getCalendar() == null) {
            return new MpsSemanalSnapshotMetrics(0, 0, 0, 0.0);
        }

        int totalOrdenesEsperadas = 0;
        if (snapshot.getCalendar().getRows() != null) {
            totalOrdenesEsperadas = snapshot.getCalendar().getRows().stream()
                    .filter(Objects::nonNull)
                    .flatMap(row -> row.getDays() == null ? java.util.stream.Stream.empty() : row.getDays().stream())
                    .filter(Objects::nonNull)
                    .flatMap(day -> day.getBlocks() == null ? java.util.stream.Stream.empty() : day.getBlocks().stream())
                    .filter(Objects::nonNull)
                    .mapToInt(block -> Math.max(block.getLotesAsignados(), 0))
                    .sum();
        }

        int totalBloquesNoProgramados = 0;
        int totalLotesNoProgramados = 0;
        double totalUnidadesNoProgramadas = 0.0;

        if (snapshot.getCalendar().getUnscheduled() != null) {
            totalBloquesNoProgramados = (int) snapshot.getCalendar().getUnscheduled().stream()
                    .filter(Objects::nonNull)
                    .count();
            totalLotesNoProgramados = snapshot.getCalendar().getUnscheduled().stream()
                    .filter(Objects::nonNull)
                    .mapToInt(block -> Math.max(block.getLotesAsignados(), 0))
                    .sum();
            totalUnidadesNoProgramadas = snapshot.getCalendar().getUnscheduled().stream()
                    .filter(Objects::nonNull)
                    .mapToDouble(block -> Math.max(block.getCantidadAsignada(), 0.0))
                    .sum();
        }

        return new MpsSemanalSnapshotMetrics(
                totalOrdenesEsperadas,
                totalBloquesNoProgramados,
                totalLotesNoProgramados,
                totalUnidadesNoProgramadas
        );
    }

    boolean hasExpectedOrders() {
        return totalOrdenesEsperadas > 0;
    }
}
