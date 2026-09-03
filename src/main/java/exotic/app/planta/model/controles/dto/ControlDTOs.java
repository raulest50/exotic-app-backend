package exotic.app.planta.model.controles.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonAlias;
import exotic.app.planta.model.controles.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public final class ControlDTOs {
    private ControlDTOs() {}

    public record CatalogoWriteRequest(
            @NotBlank @Size(max = 40) String codigo,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(max = 80) String dimension,
            @NotBlank @Size(max = 30) String simbolo) {}

    public record EstadoCatalogoRequest(@NotNull Boolean activo) {}

    public record CatalogoResponse(
            Long id, String codigo, String nombre, String dimension,
            String simbolo, boolean activo, boolean usado) {}

    public record PlanWriteRequest(
            @NotBlank @Size(max = 60) String codigo,
            @NotBlank @Size(max = 160) String nombre,
            @NotBlank @Size(max = 120) String proposito,
            @Size(max = 500) String motivoCambio,
            @NotEmpty List<@Valid AplicabilidadWriteRequest> aplicabilidades,
            @NotEmpty List<@Valid CaracteristicaWriteRequest> caracteristicas) {}

    public record AplicabilidadWriteRequest(
            String productoId,
            Integer categoriaId,
            @NotNull TipoOrdenControl tipoOrden,
            @NotNull PuntoAplicacionControl puntoAplicacion,
            Integer areaOperativaId,
            Integer procesoId,
            @NotNull MomentoControl momento,
            @NotNull PuntoExigenciaControl puntoExigencia,
            List<String> productosExcluidosIds) {}

    public record CaracteristicaWriteRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotNull TipoCaracteristicaControl tipo,
            @NotNull Long magnitudId,
            Long unidadId,
            @NotNull @Positive Integer orden,
            @NotNull @Positive Integer cantidadMuestras,
            @NotNull @Positive Integer unidadesPorMuestra,
            @NotNull @Min(0) @Max(8) Integer escalaVisible,
            BigDecimal objetivo,
            BigDecimal limiteInferior,
            BigDecimal limiteSuperior,
            Boolean valorBooleanoEsperado) {}

    public record AplicabilidadResponse(
            Long id, String productoId, Integer categoriaId,
            TipoOrdenControl tipoOrden, PuntoAplicacionControl puntoAplicacion,
            Integer areaOperativaId, String areaOperativaNombre,
            Integer procesoId, String procesoNombre,
            MomentoControl momento, PuntoExigenciaControl puntoExigencia,
            List<String> productosExcluidosIds, boolean legadoGlobal) {}

    public record CaracteristicaResponse(
            Long id, String nombre, TipoCaracteristicaControl tipo,
            Integer orden, Integer cantidadMuestras, Integer unidadesPorMuestra,
            Integer escalaVisible,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal objetivo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limiteInferior,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limiteSuperior,
            Boolean valorBooleanoEsperado,
            CatalogoResponse magnitud, CatalogoResponse unidad,
            boolean requiereDepuracion) {}

    public record VersionResponse(
            Long id, Integer numero, EstadoVersionPlanControl estado,
            String proposito, String motivoCambio,
            String responsableEjecucion, String responsableRevision,
            String responsableDisposicion, LocalDateTime creadaEn,
            LocalDateTime publicadaEn, LocalDateTime retiradaEn,
            List<AplicabilidadResponse> aplicabilidades,
            List<CaracteristicaResponse> caracteristicas) {}

    public record PlanResponse(
            Long id, String codigo, String nombre, AmbitoControl ambito,
            LocalDateTime creadoEn, List<VersionResponse> versiones) {}

    public record PendienteResponse(
            Long controlRequeridoId, EstadoControlRequerido estado,
            Long planId, String planCodigo, String planNombre,
            Long versionId, Integer versionNumero, String proposito, AmbitoControl ambito,
            Long loteId, String lote, String productoId, String productoNombre,
            TipoOrdenControl tipoOrden, Integer ordenProduccionId, Long ordenFabricacionId,
            LocalDate fechaVencimientoLote,
            Long batchRecordId, String batchRecordCodigo,
            Long batchRecordEtapaId, String etapaNombre,
            Integer areaOperativaId, String areaOperativaNombre,
             Integer procesoId, String procesoNombre,
             MomentoControl momento, PuntoExigenciaControl puntoExigencia,
             boolean requiereRepeticion, boolean requiereRevalidacion,
             boolean agregadoExcepcionalmente, String motivoAdicion,
             String agregadoPor, Long revisionAdicionId, Long firmaAdicionId,
             Long ultimaEjecucionId, LocalDateTime ultimaEjecucionFecha,
             List<CaracteristicaResponse> caracteristicas) {}

    public record IndependienteWriteRequest(@NotNull Long loteId) {}

    public record LoteControlResponse(
            Long id, String lote, String productoId, String productoNombre,
            TipoOrdenControl tipoOrden, Long batchRecordId, String batchRecordCodigo) {}

    public record AdicionExcepcionalWriteRequest(
            @NotNull Long batchRecordId,
            @NotNull Long planId,
            Long batchRecordEtapaId,
            @NotBlank @Size(max = 500) String motivo) {}

    public record OpcionAdicionExcepcionalResponse(
            Long planId, String planCodigo, String planNombre,
            Long versionId, Integer versionNumero, String proposito,
            PuntoAplicacionControl puntoAplicacion, MomentoControl momento,
            PuntoExigenciaControl puntoExigencia) {}

    public record EtapaAdicionExcepcionalResponse(
            Long id, Integer secuencia, String nombre,
            Integer areaId, String areaNombre) {}

    public record LecturaWriteRequest(
            @NotNull @Positive Integer indiceUnidad,
            BigDecimal valorNumerico,
            Boolean valorBooleano) {}

    public record MuestraWriteRequest(
            @NotNull Long caracteristicaId,
            @NotNull @Positive Integer numeroMuestra,
            @NotEmpty List<@Valid LecturaWriteRequest> lecturas) {}

    public record EjecucionWriteRequest(
            @NotNull Long controlRequeridoId,
            @Size(max = 5000) String observaciones,
            Long repeticionDeId,
            @Size(max = 500) String motivoRepeticion,
            @NotEmpty List<@Valid MuestraWriteRequest> muestras) {}

    public record LecturaResponse(
            Long id, Integer indiceUnidad,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorNumerico,
            Boolean valorBooleano, boolean conforme) {}

    public record MuestraResponse(
            Long id, Long caracteristicaId, String caracteristicaNombre,
            TipoCaracteristicaControl tipo, String unidadSimbolo,
            Integer escalaVisible,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal objetivo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limiteInferior,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limiteSuperior,
            Boolean valorBooleanoEsperado,
            Integer numeroMuestra, List<LecturaResponse> lecturas) {}

    public record EjecucionResumenResponse(
            Long id, Long controlRequeridoId, Long repeticionDeId,
            AmbitoControl ambito, Long planId, String planCodigo, String planNombre,
            Integer versionNumero, Long loteId, String lote, String productoId, String productoNombre,
            TipoOrdenControl tipoOrden, Integer ordenProduccionId, Long ordenFabricacionId,
            Long batchRecordId, String batchRecordCodigo,
            Long batchRecordEtapaId, String etapaNombre,
            Integer areaOperativaId, String areaOperativaNombre,
             Integer procesoId, String procesoNombre,
             boolean agregadoExcepcionalmente, String motivoAdicion,
             String agregadoPor, Long revisionAdicionId, Long firmaAdicionId,
             String usuarioUsername, String usuarioNombreCompleto,
            LocalDateTime fechaRegistro, ResultadoEjecucionControl resultado,
            String observaciones, String motivoRepeticion, Long desviacionId) {}

    public record EjecucionDetalleResponse(
            EjecucionResumenResponse resumen,
            List<MuestraResponse> muestras,
            Long desviacionId) {}

    public record RevalidacionWriteRequest(
            @NotBlank @Size(max = 1000) String justificacion) {}

    public record RevalidacionResponse(
            Long id, Long controlRequeridoId, Long ejecucionRevalidadaId,
            Integer cicloRevisionNumero, String justificacion,
            LocalDateTime confirmadaEn, String confirmadaPor) {}

    public record DesviacionResolveRequest(
            @NotBlank @Size(max = 10000) String investigacion,
            @NotBlank @Size(max = 10000) String resolucion,
            @NotNull DisposicionDesviacionControl disposicion) {}

    public record DesviacionCloseRequest(
            @NotNull DisposicionDesviacionControl disposicion,
            @JsonAlias("resolucion")
            @NotBlank @Size(max = 10000) String justificacionDisposicion) {}

    public record DesviacionResponse(
            Long id, Long controlRequeridoId, Long ejecucionOrigenId,
            AmbitoControl ambito, EstadoDesviacionControl estado,
            String planCodigo, String planNombre, Long loteId, String lote,
            String productoId, TipoOrdenControl tipoOrden,
            DisposicionDesviacionControl disposicion,
            String investigacion, String resolucion, String justificacionDisposicion,
            LocalDateTime abiertaEn, String abiertaPor,
            LocalDateTime resueltaEn, String resueltaPor,
            LocalDateTime cerradaEn, String cerradaPor) {}
}
