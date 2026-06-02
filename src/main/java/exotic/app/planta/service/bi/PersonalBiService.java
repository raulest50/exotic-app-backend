package exotic.app.planta.service.bi;

import exotic.app.planta.model.bi.dto.HorasExtraBiEstadoDTO;
import exotic.app.planta.model.bi.dto.HorasExtraBiResumenDTO;
import exotic.app.planta.model.bi.dto.HorasExtraBiSerieDTO;
import exotic.app.planta.model.bi.dto.HorasExtraBiSeriePuntoDTO;
import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import exotic.app.planta.repo.personal.RegistroHoraExtraRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PersonalBiService {

    private static final String[] HEADERS_SERIE = {
            "Bucket",
            "Fecha inicio",
            "Fecha fin",
            "Registros registrada",
            "Horas registrada",
            "Registros aprobada",
            "Horas aprobada",
            "Registros rechazada",
            "Horas rechazada",
            "Registros anulada",
            "Horas anulada"
    };

    private static final String[] HEADERS_DETALLE = {
            "ID registro",
            "Cedula integrante",
            "Nombre integrante",
            "Cargo",
            "Departamento",
            "Fecha",
            "Hora inicio",
            "Hora fin",
            "Minutos",
            "Horas",
            "Estado",
            "Motivo",
            "Observaciones",
            "Registrado por",
            "Fecha registro",
            "Decision por",
            "Fecha decision",
            "Motivo rechazo o anulacion"
    };

    private final RegistroHoraExtraRepo registroHoraExtraRepo;

    public HorasExtraBiResumenDTO resumenHorasExtra(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Long integranteId,
            IntegrantePersonal.Departamento departamento,
            String cargo
    ) {
        List<RegistroHoraExtra> registros = registrosFiltrados(fechaDesde, fechaHasta, integranteId, departamento, cargo);
        Map<RegistroHoraExtra.Estado, EstadoAccumulator> porEstado = initEstados();
        for (RegistroHoraExtra registro : registros) {
            porEstado.get(registro.getEstado()).add(registro);
        }

        int totalMinutos = registros.stream().mapToInt(RegistroHoraExtra::getMinutos).sum();
        return HorasExtraBiResumenDTO.builder()
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .integranteId(integranteId)
                .departamento(departamento)
                .cargo(normalizeOptional(cargo))
                .totalRegistros(registros.size())
                .totalMinutos(totalMinutos)
                .totalHoras(toHoras(totalMinutos))
                .estados(porEstado.entrySet().stream()
                        .map((entry) -> entry.getValue().toEstadoDto(entry.getKey()))
                        .toList())
                .build();
    }

    public HorasExtraBiSerieDTO serieHorasExtra(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            HorasExtraBiGranularidad granularidad,
            Long integranteId,
            IntegrantePersonal.Departamento departamento,
            String cargo
    ) {
        HorasExtraBiGranularidad granularidadFinal = granularidad != null ? granularidad : HorasExtraBiGranularidad.DIA;
        List<RegistroHoraExtra> registros = registrosFiltrados(fechaDesde, fechaHasta, integranteId, departamento, cargo);
        Map<LocalDate, SerieAccumulator> porBucket = new java.util.TreeMap<>();
        for (RegistroHoraExtra registro : registros) {
            LocalDate bucketStart = bucketStart(registro.getFecha(), granularidadFinal);
            porBucket.computeIfAbsent(bucketStart, (start) -> new SerieAccumulator(start, bucketEnd(start, granularidadFinal)))
                    .add(registro);
        }

        return HorasExtraBiSerieDTO.builder()
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .granularidad(granularidadFinal)
                .integranteId(integranteId)
                .departamento(departamento)
                .cargo(normalizeOptional(cargo))
                .puntos(porBucket.values().stream()
                        .sorted(Comparator.comparing(SerieAccumulator::fechaInicio))
                        .map(SerieAccumulator::toDto)
                        .toList())
                .build();
    }

    public byte[] exportarHorasExtraExcel(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            HorasExtraBiGranularidad granularidad,
            Long integranteId,
            IntegrantePersonal.Departamento departamento,
            String cargo
    ) {
        HorasExtraBiGranularidad granularidadFinal = granularidad != null ? granularidad : HorasExtraBiGranularidad.DIA;
        List<RegistroHoraExtra> registros = registrosFiltrados(fechaDesde, fechaHasta, integranteId, departamento, cargo);
        HorasExtraBiResumenDTO resumen = buildResumen(fechaDesde, fechaHasta, integranteId, departamento, cargo, registros);
        HorasExtraBiSerieDTO serie = buildSerie(fechaDesde, fechaHasta, granularidadFinal, integranteId, departamento, cargo, registros);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            escribirHojaResumen(workbook, resumen, granularidadFinal);
            escribirHojaSerie(workbook, serie);
            escribirHojaDetalle(workbook, registros);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generando Excel BI personal horas extra", e);
            throw new RuntimeException("Error generando Excel BI personal horas extra", e);
        }
    }

    private List<RegistroHoraExtra> registrosFiltrados(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Long integranteId,
            IntegrantePersonal.Departamento departamento,
            String cargo
    ) {
        validateRange(fechaDesde, fechaHasta);
        return registroHoraExtraRepo.buscarBiHorasExtra(
                fechaDesde,
                fechaHasta,
                integranteId,
                departamento,
                normalizeOptional(cargo)
        );
    }

    private HorasExtraBiResumenDTO buildResumen(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            Long integranteId,
            IntegrantePersonal.Departamento departamento,
            String cargo,
            List<RegistroHoraExtra> registros
    ) {
        Map<RegistroHoraExtra.Estado, EstadoAccumulator> porEstado = initEstados();
        for (RegistroHoraExtra registro : registros) {
            porEstado.get(registro.getEstado()).add(registro);
        }
        int totalMinutos = registros.stream().mapToInt(RegistroHoraExtra::getMinutos).sum();
        return HorasExtraBiResumenDTO.builder()
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .integranteId(integranteId)
                .departamento(departamento)
                .cargo(normalizeOptional(cargo))
                .totalRegistros(registros.size())
                .totalMinutos(totalMinutos)
                .totalHoras(toHoras(totalMinutos))
                .estados(porEstado.entrySet().stream()
                        .map((entry) -> entry.getValue().toEstadoDto(entry.getKey()))
                        .toList())
                .build();
    }

    private HorasExtraBiSerieDTO buildSerie(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            HorasExtraBiGranularidad granularidad,
            Long integranteId,
            IntegrantePersonal.Departamento departamento,
            String cargo,
            List<RegistroHoraExtra> registros
    ) {
        Map<LocalDate, SerieAccumulator> porBucket = new java.util.TreeMap<>();
        for (RegistroHoraExtra registro : registros) {
            LocalDate bucketStart = bucketStart(registro.getFecha(), granularidad);
            porBucket.computeIfAbsent(bucketStart, (start) -> new SerieAccumulator(start, bucketEnd(start, granularidad)))
                    .add(registro);
        }

        return HorasExtraBiSerieDTO.builder()
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .granularidad(granularidad)
                .integranteId(integranteId)
                .departamento(departamento)
                .cargo(normalizeOptional(cargo))
                .puntos(porBucket.values().stream()
                        .sorted(Comparator.comparing(SerieAccumulator::fechaInicio))
                        .map(SerieAccumulator::toDto)
                        .toList())
                .build();
    }

    private void validateRange(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException("fechaDesde y fechaHasta son obligatorias.");
        }
        if (fechaHasta.isBefore(fechaDesde)) {
            throw new IllegalArgumentException("fechaHasta no puede ser anterior a fechaDesde.");
        }
    }

    private Map<RegistroHoraExtra.Estado, EstadoAccumulator> initEstados() {
        Map<RegistroHoraExtra.Estado, EstadoAccumulator> map = new EnumMap<>(RegistroHoraExtra.Estado.class);
        for (RegistroHoraExtra.Estado estado : RegistroHoraExtra.Estado.values()) {
            map.put(estado, new EstadoAccumulator());
        }
        return map;
    }

    private static LocalDate bucketStart(LocalDate fecha, HorasExtraBiGranularidad granularidad) {
        return switch (granularidad) {
            case DIA -> fecha;
            case SEMANA -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> fecha.withDayOfMonth(1);
        };
    }

    private static LocalDate bucketEnd(LocalDate start, HorasExtraBiGranularidad granularidad) {
        return switch (granularidad) {
            case DIA -> start;
            case SEMANA -> start.plusDays(6);
            case MES -> start.withDayOfMonth(start.lengthOfMonth());
        };
    }

    private static double toHoras(int minutos) {
        return Math.round((minutos / 60.0) * 100.0) / 100.0;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String integranteNombre(RegistroHoraExtra registro) {
        String nombres = registro.getIntegrante().getNombres() == null ? "" : registro.getIntegrante().getNombres();
        String apellidos = registro.getIntegrante().getApellidos() == null ? "" : registro.getIntegrante().getApellidos();
        return (nombres + " " + apellidos).trim();
    }

    private static String nombreUsuario(exotic.app.planta.model.users.User user) {
        if (user == null) {
            return "";
        }
        return user.getNombreCompleto() != null && !user.getNombreCompleto().isBlank()
                ? user.getNombreCompleto()
                : user.getUsername();
    }

    private void escribirHojaResumen(XSSFWorkbook workbook, HorasExtraBiResumenDTO resumen, HorasExtraBiGranularidad granularidad) {
        Sheet sheet = workbook.createSheet("Resumen");
        int rowIdx = 0;
        rowIdx = writeKeyValue(sheet, rowIdx, "Fecha desde", resumen.getFechaDesde().toString());
        rowIdx = writeKeyValue(sheet, rowIdx, "Fecha hasta", resumen.getFechaHasta().toString());
        rowIdx = writeKeyValue(sheet, rowIdx, "Granularidad", granularidad.name());
        rowIdx = writeKeyValue(sheet, rowIdx, "Integrante ID", resumen.getIntegranteId() != null ? String.valueOf(resumen.getIntegranteId()) : "Todos");
        rowIdx = writeKeyValue(sheet, rowIdx, "Departamento", resumen.getDepartamento() != null ? resumen.getDepartamento().name() : "Todos");
        rowIdx = writeKeyValue(sheet, rowIdx, "Cargo", resumen.getCargo() != null ? resumen.getCargo() : "Todos");
        rowIdx = writeKeyValue(sheet, rowIdx, "Registros totales", String.valueOf(resumen.getTotalRegistros()));
        rowIdx = writeKeyValue(sheet, rowIdx, "Minutos totales", String.valueOf(resumen.getTotalMinutos()));
        rowIdx = writeKeyValue(sheet, rowIdx, "Horas totales", String.valueOf(resumen.getTotalHoras()));
        rowIdx++;

        Row header = sheet.createRow(rowIdx++);
        header.createCell(0).setCellValue("Estado");
        header.createCell(1).setCellValue("Registros");
        header.createCell(2).setCellValue("Minutos");
        header.createCell(3).setCellValue("Horas");
        for (HorasExtraBiEstadoDTO estado : resumen.getEstados()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(estado.getEstado().name());
            row.createCell(1).setCellValue(estado.getRegistros());
            row.createCell(2).setCellValue(estado.getMinutos());
            row.createCell(3).setCellValue(estado.getHoras());
        }

        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private int writeKeyValue(Sheet sheet, int rowIdx, String key, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }

    private void escribirHojaSerie(XSSFWorkbook workbook, HorasExtraBiSerieDTO serie) {
        Sheet sheet = workbook.createSheet("Serie temporal");
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS_SERIE.length; i++) {
            header.createCell(i).setCellValue(HEADERS_SERIE[i]);
        }

        int rowIdx = 1;
        for (HorasExtraBiSeriePuntoDTO punto : serie.getPuntos()) {
            Row row = sheet.createRow(rowIdx++);
            int c = 0;
            row.createCell(c++).setCellValue(punto.getBucket());
            row.createCell(c++).setCellValue(punto.getFechaInicio().toString());
            row.createCell(c++).setCellValue(punto.getFechaFin().toString());
            row.createCell(c++).setCellValue(punto.getRegistrosRegistrada());
            row.createCell(c++).setCellValue(punto.getHorasRegistrada());
            row.createCell(c++).setCellValue(punto.getRegistrosAprobada());
            row.createCell(c++).setCellValue(punto.getHorasAprobada());
            row.createCell(c++).setCellValue(punto.getRegistrosRechazada());
            row.createCell(c++).setCellValue(punto.getHorasRechazada());
            row.createCell(c++).setCellValue(punto.getRegistrosAnulada());
            row.createCell(c).setCellValue(punto.getHorasAnulada());
        }

        for (int i = 0; i < HEADERS_SERIE.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void escribirHojaDetalle(XSSFWorkbook workbook, List<RegistroHoraExtra> registros) {
        Sheet sheet = workbook.createSheet("Detalle");
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS_DETALLE.length; i++) {
            header.createCell(i).setCellValue(HEADERS_DETALLE[i]);
        }

        int rowIdx = 1;
        for (RegistroHoraExtra registro : registros) {
            Row row = sheet.createRow(rowIdx++);
            int c = 0;
            row.createCell(c++).setCellValue(registro.getId());
            row.createCell(c++).setCellValue(registro.getIntegrante().getId());
            row.createCell(c++).setCellValue(integranteNombre(registro));
            row.createCell(c++).setCellValue(registro.getIntegrante().getCargo() != null ? registro.getIntegrante().getCargo() : "");
            row.createCell(c++).setCellValue(registro.getIntegrante().getDepartamento() != null ? registro.getIntegrante().getDepartamento().name() : "");
            row.createCell(c++).setCellValue(registro.getFecha().toString());
            row.createCell(c++).setCellValue(registro.getHoraInicio().toString());
            row.createCell(c++).setCellValue(registro.getHoraFin().toString());
            row.createCell(c++).setCellValue(registro.getMinutos());
            row.createCell(c++).setCellValue(toHoras(registro.getMinutos()));
            row.createCell(c++).setCellValue(registro.getEstado().name());
            row.createCell(c++).setCellValue(registro.getMotivo() != null ? registro.getMotivo() : "");
            row.createCell(c++).setCellValue(registro.getObservaciones() != null ? registro.getObservaciones() : "");
            row.createCell(c++).setCellValue(nombreUsuario(registro.getRegistradoPor()));
            row.createCell(c++).setCellValue(registro.getFechaRegistro() != null ? registro.getFechaRegistro().toString() : "");
            row.createCell(c++).setCellValue(nombreUsuario(registro.getAprobadoPor()));
            row.createCell(c++).setCellValue(registro.getFechaDecision() != null ? registro.getFechaDecision().toString() : "");
            row.createCell(c).setCellValue(registro.getMotivoRechazoOAnulacion() != null ? registro.getMotivoRechazoOAnulacion() : "");
        }

        for (int i = 0; i < HEADERS_DETALLE.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static class EstadoAccumulator {
        private long registros;
        private int minutos;

        void add(RegistroHoraExtra registro) {
            registros++;
            minutos += registro.getMinutos();
        }

        HorasExtraBiEstadoDTO toEstadoDto(RegistroHoraExtra.Estado estado) {
            return HorasExtraBiEstadoDTO.builder()
                    .estado(estado)
                    .registros(registros)
                    .minutos(minutos)
                    .horas(toHoras(minutos))
                    .build();
        }
    }

    private static class SerieAccumulator {
        private final LocalDate fechaInicio;
        private final LocalDate fechaFin;
        private final Map<RegistroHoraExtra.Estado, EstadoAccumulator> porEstado =
                new EnumMap<>(RegistroHoraExtra.Estado.class);

        SerieAccumulator(LocalDate fechaInicio, LocalDate fechaFin) {
            this.fechaInicio = fechaInicio;
            this.fechaFin = fechaFin;
            for (RegistroHoraExtra.Estado estado : RegistroHoraExtra.Estado.values()) {
                porEstado.put(estado, new EstadoAccumulator());
            }
        }

        LocalDate fechaInicio() {
            return fechaInicio;
        }

        void add(RegistroHoraExtra registro) {
            porEstado.get(registro.getEstado()).add(registro);
        }

        HorasExtraBiSeriePuntoDTO toDto() {
            EstadoAccumulator registrada = porEstado.get(RegistroHoraExtra.Estado.REGISTRADA);
            EstadoAccumulator aprobada = porEstado.get(RegistroHoraExtra.Estado.APROBADA);
            EstadoAccumulator rechazada = porEstado.get(RegistroHoraExtra.Estado.RECHAZADA);
            EstadoAccumulator anulada = porEstado.get(RegistroHoraExtra.Estado.ANULADA);
            return HorasExtraBiSeriePuntoDTO.builder()
                    .bucket(fechaInicio.toString())
                    .fechaInicio(fechaInicio)
                    .fechaFin(fechaFin)
                    .registrosRegistrada(registrada.registros)
                    .registrosAprobada(aprobada.registros)
                    .registrosRechazada(rechazada.registros)
                    .registrosAnulada(anulada.registros)
                    .minutosRegistrada(registrada.minutos)
                    .minutosAprobada(aprobada.minutos)
                    .minutosRechazada(rechazada.minutos)
                    .minutosAnulada(anulada.minutos)
                    .horasRegistrada(toHoras(registrada.minutos))
                    .horasAprobada(toHoras(aprobada.minutos))
                    .horasRechazada(toHoras(rechazada.minutos))
                    .horasAnulada(toHoras(anulada.minutos))
                    .build();
        }
    }
}
