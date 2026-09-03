package exotic.app.planta.service.calidad;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.calidad.*;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.*;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.EjecucionWriteRequest;
import exotic.app.planta.model.controles.dto.ControlDTOs.LecturaWriteRequest;
import exotic.app.planta.model.controles.dto.ControlDTOs.MuestraWriteRequest;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.produccion.batchrecord.BatchRecord;
import exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.controles.CaracteristicaPlanControlRepo;
import exotic.app.planta.repo.controles.ControlRequeridoRepo;
import exotic.app.planta.repo.controles.EjecucionControlRepo;
import exotic.app.planta.repo.controles.VersionPlanControlRepo;
import exotic.app.planta.service.controles.ControlExecutionService;
import exotic.app.planta.service.controles.LegacyControlPlanSynchronizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Temporary transactional adapter for clients of the pre-V101 write API.
 * Neither side is committed unless the legacy row and its neutral projection
 * have both passed validation and been flushed successfully.
 */
@Service
@RequiredArgsConstructor
public class LegacyControlCompatibilityService {

    static final String LEGACY_REPETITION_REASON =
            "Registro mediante API legada durante la ventana de compatibilidad.";

    private final CalidadControlProcesoService legacyService;
    private final LegacyControlPlanSynchronizer planSynchronizer;
    private final ControlExecutionService executionService;
    private final ControlProcesoPlantillaRepo legacyPlanRepo;
    private final ControlProcesoEjecucionRepo legacyExecutionRepo;
    private final VersionPlanControlRepo versionRepo;
    private final CaracteristicaPlanControlRepo characteristicRepo;
    private final ControlRequeridoRepo requiredRepo;
    private final EjecucionControlRepo executionRepo;

    @Transactional
    public PlantillaResponse guardarBorrador(User actor, PlantillaRequest request) {
        if (request == null || request.getAreaOperativaId() == null) {
            throw new IllegalArgumentException("Debe seleccionar un area operativa.");
        }
        Integer areaId = request.getAreaOperativaId();
        planSynchronizer.requireLegacyOwnedFamily(areaId);
        // Acquires the existing row before both models replace their children.
        legacyPlanRepo.findByAreaIdAndEstadoForUpdate(
                areaId, EstadoControlProcesoPlantilla.BORRADOR);
        planSynchronizer.prepareLegacyDraftReplacement(areaId);
        PlantillaResponse response = legacyService.guardarBorrador(request);
        planSynchronizer.synchronizeArea(areaId, actor);
        return response;
    }

    @Transactional
    public PlantillaResponse publicarPlantilla(User actor, Long plantillaId) {
        ControlProcesoPlantilla legacy = lockLegacyPlan(plantillaId);
        Integer areaId = legacy.getAreaOperativa().getAreaId();
        planSynchronizer.requireLegacyOwnedFamily(areaId);
        PlantillaResponse response = legacyService.publicarPlantilla(plantillaId);
        planSynchronizer.synchronizeArea(areaId, actor);
        return response;
    }

    @Transactional
    public PlantillaResponse retirarPlantilla(User actor, Long plantillaId) {
        ControlProcesoPlantilla legacy = lockLegacyPlan(plantillaId);
        Integer areaId = legacy.getAreaOperativa().getAreaId();
        PlantillaResponse response = legacyService.retirarPlantilla(plantillaId);
        // Retirement is the only legacy mutation allowed after a native draft is
        // created: it is the coordinated hand-off that permits native publication.
        planSynchronizer.synchronizeRetirement(areaId, actor);
        return response;
    }

    @Transactional
    public EjecucionDetalleResponse guardarEjecucion(User actor, EjecucionRequest request) {
        if (request == null || request.getPlantillaId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una plantilla.");
        }
        ControlProcesoPlantilla legacyPlan = lockLegacyPlan(request.getPlantillaId());
        Integer areaId = legacyPlan.getAreaOperativa().getAreaId();
        if (versionRepo.findByLegacyPlantilla_Id(legacyPlan.getId()).isEmpty()) {
            planSynchronizer.synchronizeArea(areaId, actor);
        }

        EjecucionDetalleResponse legacyResponse = legacyService.guardarEjecucion(actor, request);
        ControlProcesoEjecucion legacyExecution = legacyExecutionRepo.findById(legacyResponse.getId())
                .orElseThrow(() -> new IllegalStateException("No se pudo recuperar la ejecucion legada creada."));
        VersionPlanControl version = versionRepo.findByLegacyPlantilla_Id(legacyPlan.getId())
                .orElseThrow(() -> new IllegalStateException("La plantilla legada no tiene proyeccion neutral."));
        ControlRequerido required = resolveRequired(version, legacyExecution);

        List<EjecucionControl> previous = executionRepo
                .findByControlRequerido_IdOrderByFechaRegistroDescIdDesc(required.getId());
        Long repeatedExecutionId = previous.isEmpty() ? null : previous.getFirst().getId();
        EjecucionWriteRequest neutralRequest = new EjecucionWriteRequest(
                required.getId(), legacyExecution.getObservaciones(), repeatedExecutionId,
                repeatedExecutionId == null ? null : LEGACY_REPETITION_REASON,
                toNeutralSamples(legacyExecution));
        executionService.ejecutarDesdeLegado(
                AmbitoControl.PROCESO, actor, neutralRequest, legacyExecution);

        // The neutral evaluator is authoritative; rebuilding the response also
        // reflects its result if an old client supplied an edge-case value.
        return legacyService.detalleEjecucion(legacyExecution.getId());
    }

    private ControlProcesoPlantilla lockLegacyPlan(Long id) {
        return legacyPlanRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Plantilla no encontrada."));
    }

    private ControlRequerido resolveRequired(
            VersionPlanControl version, ControlProcesoEjecucion legacyExecution) {
        BatchRecordEtapa stage = legacyExecution.getBatchRecordEtapa();
        if (stage != null) {
            return requiredRepo.findByBatchRecordEtapa_IdAndVersionPlan_Id(stage.getId(), version.getId())
                    .orElseGet(() -> requiredRepo.saveAndFlush(
                            newRequired(version, legacyExecution, OrigenControlRequerido.BATCH_RECORD)));
        }
        return requiredRepo.findByLegacyEjecucion_Id(legacyExecution.getId())
                .orElseGet(() -> requiredRepo.saveAndFlush(
                        newRequired(version, legacyExecution, OrigenControlRequerido.LEGACY)));
    }

    private ControlRequerido newRequired(
            VersionPlanControl version,
            ControlProcesoEjecucion legacyExecution,
            OrigenControlRequerido origin) {
        AplicabilidadPlanControl applicability = version.getAplicabilidades().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("La version legada no tiene aplicabilidad neutral."));
        Lote lot = legacyExecution.getLote();
        BatchRecord record = legacyExecution.getBatchRecord();
        BatchRecordEtapa stage = legacyExecution.getBatchRecordEtapa();
        Producto product = record == null ? resolveProduct(lot) : record.getProductoResultado();
        Categoria category = record == null ? resolveCategory(lot) : resolveCategory(record);

        ControlRequerido required = new ControlRequerido();
        required.setVersionPlan(version);
        required.setAplicabilidad(applicability);
        required.setLote(lot);
        required.setBatchRecord(record);
        required.setBatchRecordEtapa(stage);
        required.setOrigen(origin);
        required.setEstado(EstadoControlRequerido.PENDIENTE);
        required.setCreadoEn(AppTime.now());
        required.setPlanCodigoSnapshot(version.getPlan().getCodigo());
        required.setPlanNombreSnapshot(version.getPlan().getNombre());
        required.setAmbitoSnapshot(AmbitoControl.PROCESO);
        required.setVersionNumeroSnapshot(version.getNumero());
        required.setProductoIdSnapshot(product == null ? null : product.getProductoId());
        required.setProductoNombreSnapshot(product == null ? null : product.getNombre());
        required.setCategoriaIdSnapshot(category == null ? null : category.getCategoriaId());
        required.setCategoriaNombreSnapshot(category == null ? null : category.getCategoriaNombre());
        required.setTipoOrdenSnapshot(lot.getOrdenFabricacion() == null
                ? TipoOrdenControl.OP : TipoOrdenControl.OF);
        required.setPuntoAplicacionSnapshot(PuntoAplicacionControl.SALIDA_OPERACION);
        required.setAreaOperativaIdSnapshot(legacyExecution.getPlantilla().getAreaOperativa().getAreaId());
        required.setAreaOperativaNombreSnapshot(legacyExecution.getPlantilla().getAreaOperativa().getNombre());
        required.setMomentoSnapshot(MomentoControl.DURANTE_FABRICACION);
        required.setPuntoExigenciaSnapshot(PuntoExigenciaControl.INFORMATIVO);
        required.setManufacturingVersionIdSnapshot(manufacturingVersionId(record, lot));
        if (origin == OrigenControlRequerido.LEGACY) {
            required.setLegacyEjecucion(legacyExecution);
        }
        freezeStageContext(required, stage);
        return required;
    }

    private void freezeStageContext(ControlRequerido required, BatchRecordEtapa stage) {
        if (stage == null) return;
        if (stage.getSeguimientoOrdenArea() != null
                && stage.getSeguimientoOrdenArea().getRutaProcesoNode() != null) {
            var node = stage.getSeguimientoOrdenArea().getRutaProcesoNode();
            required.setRutaNodoIdSnapshot(node.getId());
            required.setFrontendNodeIdSnapshot(node.getFrontendId());
            required.setNodoNombreSnapshot(node.getLabel());
            if (node.getRutaProcesoCatVersion() != null) {
                required.setRutaVersionIdSnapshot(node.getRutaProcesoCatVersion().getId());
            }
        }
        if (stage.getOrdenFabricacionOperacion() != null) {
            var operation = stage.getOrdenFabricacionOperacion();
            required.setOrdenFabricacionOperacionIdSnapshot(operation.getId());
            required.setFrontendNodeIdSnapshot(operation.getFrontendNodeId());
            required.setNodoNombreSnapshot(operation.getProcesoNombre());
        }
    }

    private List<MuestraWriteRequest> toNeutralSamples(ControlProcesoEjecucion legacyExecution) {
        List<MuestraWriteRequest> result = new ArrayList<>();
        legacyExecution.getMuestras().stream()
                .sorted(Comparator.comparing(ControlProcesoMuestra::getNumeroMuestra)
                        .thenComparing(m -> m.getCaracteristica().getOrden()))
                .forEach(sample -> {
                    CaracteristicaPlanControl neutralCharacteristic = characteristicRepo
                            .findByLegacyCaracteristica_Id(sample.getCaracteristica().getId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Una caracteristica legada no tiene proyeccion neutral."));
                    List<LecturaWriteRequest> readings = sample.getLecturas().stream()
                            .sorted(Comparator.comparing(ControlProcesoLectura::getIndiceUnidad))
                            .map(reading -> new LecturaWriteRequest(
                                    reading.getIndiceUnidad(),
                                    decimal(reading.getValorNumerico(), reading.getId()),
                                    reading.getValorBooleano()))
                            .toList();
                    result.add(new MuestraWriteRequest(
                            neutralCharacteristic.getId(), sample.getNumeroMuestra(), readings));
                });
        return result;
    }

    private BigDecimal decimal(Double value, Long readingId) {
        if (value == null) return null;
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("La lectura legada " + readingId + " no es finita.");
        }
        BigDecimal result = BigDecimal.valueOf(value).stripTrailingZeros();
        if (result.scale() < 0) result = result.setScale(0);
        if (result.scale() > 8 || Math.max(0, result.precision() - result.scale()) > 12) {
            throw new IllegalArgumentException(
                    "La lectura legada " + readingId + " esta fuera de NUMERIC(20,8).");
        }
        return result;
    }

    private Producto resolveProduct(Lote lot) {
        if (lot.getProducto() != null) return lot.getProducto();
        if (lot.getOrdenProduccion() != null) return lot.getOrdenProduccion().getProducto();
        return lot.getOrdenFabricacion() == null ? null : lot.getOrdenFabricacion().getSemiTerminado();
    }

    private Categoria resolveCategory(BatchRecord record) {
        if (record.getProductoResultado() instanceof Terminado finished) return finished.getCategoria();
        if (record.getOrdenFabricacion() != null
                && record.getOrdenFabricacion().getOrdenProduccionOrigen() != null
                && record.getOrdenFabricacion().getOrdenProduccionOrigen().getProducto()
                instanceof Terminado origin) return origin.getCategoria();
        return null;
    }

    private Categoria resolveCategory(Lote lot) {
        Producto product = resolveProduct(lot);
        if (product instanceof Terminado finished) return finished.getCategoria();
        if (lot.getOrdenFabricacion() != null
                && lot.getOrdenFabricacion().getOrdenProduccionOrigen() != null
                && lot.getOrdenFabricacion().getOrdenProduccionOrigen().getProducto()
                instanceof Terminado origin) return origin.getCategoria();
        return null;
    }

    private Long manufacturingVersionId(BatchRecord record, Lote lot) {
        if (record != null && record.getManufacturingVersion() != null) {
            return record.getManufacturingVersion().getId();
        }
        if (lot.getOrdenProduccion() != null && lot.getOrdenProduccion().getManufacturingVersion() != null) {
            return lot.getOrdenProduccion().getManufacturingVersion().getId();
        }
        if (lot.getOrdenFabricacion() != null && lot.getOrdenFabricacion().getManufacturingVersion() != null) {
            return lot.getOrdenFabricacion().getManufacturingVersion().getId();
        }
        return null;
    }
}
