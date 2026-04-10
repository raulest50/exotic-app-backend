package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.eliminaciones.*;
import exotic.app.planta.model.contabilidad.AsientoContable;
import exotic.app.planta.model.compras.ItemOrdenCompra;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque;
import exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.ventas.ItemFacturaVenta;
import exotic.app.planta.model.ventas.ItemOrdenVenta;
import exotic.app.planta.repo.activos.fijos.gestion.DepreciacionActivoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.DocumentoBajaActivoRepo;
import exotic.app.planta.repo.activos.fijos.gestion.IncorporacionActivoHeaderRepo;
import exotic.app.planta.repo.compras.ItemOrdenCompraRepo;
import exotic.app.planta.repo.compras.OrdenCompraRepo;
import exotic.app.planta.repo.contabilidad.AsientoContableRepo;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.InsumoEmpaqueRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionCompletoRepo;
import exotic.app.planta.repo.ventas.FacturaVentaRepo;
import exotic.app.planta.repo.ventas.ItemFacturaVentaRepo;
import exotic.app.planta.repo.ventas.ItemOrdenVentaRepo;
import exotic.app.planta.repo.ventas.OrdenVentaRepo;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EliminacionesForzadasService {

    private static final List<TransaccionAlmacen.TipoEntidadCausante> TIPOS_TRANSACCION_OP_PURGA = List.of(
            TransaccionAlmacen.TipoEntidadCausante.OD,
            TransaccionAlmacen.TipoEntidadCausante.OD_RA,
            TransaccionAlmacen.TipoEntidadCausante.RA,
            TransaccionAlmacen.TipoEntidadCausante.OP
    );

    private static final String PURGA_TERMINADOS_OPERATION = "La purga total de terminados";
    private static final String ELIMINACION_TERMINADO_OPERATION = "La eliminación forzada de un terminado";

    private final OrdenCompraRepo ordenCompraRepo;
    private final ItemOrdenCompraRepo itemOrdenCompraRepo;
    private final LoteRepo loteRepo;
    private final TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ProductoRepo productoRepo;
    private final TerminadoRepo terminadoRepo;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final InsumoRepo insumoRepo;
    private final InsumoEmpaqueRepo insumoEmpaqueRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final ItemOrdenVentaRepo itemOrdenVentaRepo;
    private final ItemFacturaVentaRepo itemFacturaVentaRepo;
    private final OrdenVentaRepo ordenVentaRepo;
    private final FacturaVentaRepo facturaVentaRepo;
    private final ProcesoProduccionCompletoRepo procesoProduccionCompletoRepo;
    private final AsientoContableRepo asientoContableRepo;
    private final IncorporacionActivoHeaderRepo incorporacionActivoHeaderRepo;
    private final DepreciacionActivoRepo depreciacionActivoRepo;
    private final DocumentoBajaActivoRepo documentoBajaActivoRepo;
    private final DangerousOperationGuard dangerousOperationGuard;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    @Autowired
    public EliminacionesForzadasService(
            OrdenCompraRepo ordenCompraRepo,
            ItemOrdenCompraRepo itemOrdenCompraRepo,
            LoteRepo loteRepo,
            TransaccionAlmacenHeaderRepo transaccionAlmacenHeaderRepo,
            OrdenProduccionRepo ordenProduccionRepo,
            ProductoRepo productoRepo,
            TerminadoRepo terminadoRepo,
            SemiTerminadoRepo semiTerminadoRepo,
            InsumoRepo insumoRepo,
            InsumoEmpaqueRepo insumoEmpaqueRepo,
            TransaccionAlmacenRepo transaccionAlmacenRepo,
            ManufacturingVersionRepo manufacturingVersionRepo,
            ItemOrdenVentaRepo itemOrdenVentaRepo,
            ItemFacturaVentaRepo itemFacturaVentaRepo,
            OrdenVentaRepo ordenVentaRepo,
            FacturaVentaRepo facturaVentaRepo,
            ProcesoProduccionCompletoRepo procesoProduccionCompletoRepo,
            AsientoContableRepo asientoContableRepo,
            IncorporacionActivoHeaderRepo incorporacionActivoHeaderRepo,
            DepreciacionActivoRepo depreciacionActivoRepo,
            DocumentoBajaActivoRepo documentoBajaActivoRepo,
            DangerousOperationGuard dangerousOperationGuard,
            TransactionTemplate transactionTemplate,
            EntityManager entityManager
    ) {
        this.ordenCompraRepo = ordenCompraRepo;
        this.itemOrdenCompraRepo = itemOrdenCompraRepo;
        this.loteRepo = loteRepo;
        this.transaccionAlmacenHeaderRepo = transaccionAlmacenHeaderRepo;
        this.ordenProduccionRepo = ordenProduccionRepo;
        this.productoRepo = productoRepo;
        this.terminadoRepo = terminadoRepo;
        this.semiTerminadoRepo = semiTerminadoRepo;
        this.insumoRepo = insumoRepo;
        this.insumoEmpaqueRepo = insumoEmpaqueRepo;
        this.transaccionAlmacenRepo = transaccionAlmacenRepo;
        this.manufacturingVersionRepo = manufacturingVersionRepo;
        this.itemOrdenVentaRepo = itemOrdenVentaRepo;
        this.itemFacturaVentaRepo = itemFacturaVentaRepo;
        this.ordenVentaRepo = ordenVentaRepo;
        this.facturaVentaRepo = facturaVentaRepo;
        this.procesoProduccionCompletoRepo = procesoProduccionCompletoRepo;
        this.asientoContableRepo = asientoContableRepo;
        this.incorporacionActivoHeaderRepo = incorporacionActivoHeaderRepo;
        this.depreciacionActivoRepo = depreciacionActivoRepo;
        this.documentoBajaActivoRepo = documentoBajaActivoRepo;
        this.dangerousOperationGuard = dangerousOperationGuard;
        this.transactionTemplate = transactionTemplate;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public EstudiarEliminacionOCMResponseDTO estudiarEliminacionOrdenCompra(int ordenCompraId) {
        if (!ordenCompraRepo.existsById(ordenCompraId)) {
            throw new RuntimeException("OrdenCompraMateriales not found with id: " + ordenCompraId);
        }

        List<ItemOrdenCompraResumenDTO> items = itemOrdenCompraRepo
                .findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId)
                .stream()
                .map(this::toItemResumen)
                .collect(Collectors.toList());

        List<LoteResumenDTO> lotes = loteRepo
                .findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId)
                .stream()
                .map(this::toLoteResumen)
                .collect(Collectors.toList());

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        ordenCompraId
                );

        List<TransaccionAlmacenResumenDTO> transaccionesDto = new ArrayList<>();
        Set<AsientoContableResumenDTO> asientosSet = new LinkedHashSet<>();

        for (TransaccionAlmacen ta : transacciones) {
            TransaccionAlmacenResumenDTO dto = toTransaccionResumen(ta);
            transaccionesDto.add(dto);
            if (ta.getAsientoContable() != null) {
                asientosSet.add(toAsientoResumen(ta.getAsientoContable()));
            }
        }

        EstudiarEliminacionOCMResponseDTO response = new EstudiarEliminacionOCMResponseDTO();
        response.setOrdenCompraId(ordenCompraId);
        response.setEliminable(transacciones.isEmpty());
        response.setItemsOrdenCompra(items);
        response.setLotes(lotes);
        response.setTransaccionesAlmacen(transaccionesDto);
        response.setAsientosContables(new ArrayList<>(asientosSet));
        return response;
    }

    @Transactional
    public void ejecutarEliminacionOrdenCompra(int ordenCompraId) {
        if (!ordenCompraRepo.existsById(ordenCompraId)) {
            throw new RuntimeException("OrdenCompraMateriales not found with id: " + ordenCompraId);
        }

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OCM,
                        ordenCompraId
                );
        if (!transacciones.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede eliminar la orden de compra: tiene " + transacciones.size() + " transacción(es) de almacén asociada(s).");
        }

        List<Lote> lotes = loteRepo.findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId);
        for (Lote lote : lotes) {
            lote.setOrdenCompraMateriales(null);
            loteRepo.save(lote);
        }

        List<ItemOrdenCompra> items = itemOrdenCompraRepo.findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId);
        itemOrdenCompraRepo.deleteAll(items);

        ordenCompraRepo.deleteById(ordenCompraId);
        log.info("Eliminación forzada ejecutada para OCM id: {}", ordenCompraId);
    }

    @Transactional(readOnly = true)
    public EstudiarEliminacionOPResponseDTO estudiarEliminacionOrdenProduccion(int ordenProduccionId) {
        if (!ordenProduccionRepo.existsById(ordenProduccionId)) {
            throw new RuntimeException("OrdenProduccion not found with id: " + ordenProduccionId);
        }

        List<LoteResumenDTO> lotes = loteRepo.findByOrdenProduccion_OrdenId(ordenProduccionId)
                .stream()
                .map(this::toLoteResumen)
                .collect(Collectors.toList());

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OP,
                        ordenProduccionId
                );

        List<TransaccionAlmacenResumenDTO> transaccionesDto = new ArrayList<>();
        Set<AsientoContableResumenDTO> asientosSet = new LinkedHashSet<>();
        for (TransaccionAlmacen ta : transacciones) {
            TransaccionAlmacenResumenDTO dto = toTransaccionResumen(ta);
            transaccionesDto.add(dto);
            if (ta.getAsientoContable() != null) {
                asientosSet.add(toAsientoResumen(ta.getAsientoContable()));
            }
        }

        EstudiarEliminacionOPResponseDTO response = new EstudiarEliminacionOPResponseDTO();
        response.setOrdenProduccionId(ordenProduccionId);
        response.setEliminable(transacciones.isEmpty());
        response.setLotes(lotes);
        response.setTransaccionesAlmacen(transaccionesDto);
        response.setAsientosContables(new ArrayList<>(asientosSet));
        return response;
    }

    @Transactional(readOnly = true)
    public EstudiarEliminacionMaterialResponseDTO estudiarEliminacionMaterial(String productoId) {
        Material material = requireMaterial(productoId);
        log.info("[ELIM_FORZADA][MATERIAL][ESTUDIO] Inicio estudio. productoId={}, materialNombre={}, tipoMaterial={}, unidad={}",
                productoId, material.getNombre(), material.getTipoMaterial(), material.getTipoUnidades());

        List<ItemOrdenCompraMaterialResumenDTO> itemsOrdenCompra = itemOrdenCompraRepo
                .findByMaterial_ProductoId(productoId)
                .stream()
                .map(this::toItemOrdenCompraMaterialResumen)
                .toList();
        List<SemiTerminado> semiTerminadosReceta = listSemiTerminadosQueReferencianMaterial(material);
        List<Terminado> terminadosReceta = listTerminadosQueReferencianMaterial(material);
        List<InsumoRecetaResumenDTO> insumosReceta = buildInsumosRecetaResumen(material, semiTerminadosReceta, terminadosReceta);
        List<InsumoEmpaqueResumenDTO> insumosEmpaque = buildInsumosEmpaqueResumen(productoId);
        log.info("[ELIM_FORZADA][MATERIAL][ESTUDIO] Padres receta con material. productoId={}, semiTerminadoIds={}, terminadoIds={}, totalSemi={}, totalTerminado={}",
                productoId,
                semiTerminadosReceta.stream().map(SemiTerminado::getProductoId).collect(Collectors.joining(",")),
                terminadosReceta.stream().map(Terminado::getProductoId).collect(Collectors.joining(",")),
                semiTerminadosReceta.size(),
                terminadosReceta.size());
        List<TransaccionAlmacen> transacciones = loadMaterialTransactionsWithAllMovimientos(productoId);

        log.info("[ELIM_FORZADA][MATERIAL][ESTUDIO] Transacciones impactadas. productoId={}, totalTransacciones={}, transaccionIds={}",
                productoId, transacciones.size(), transaccionIds(transacciones));

        List<TransaccionAlmacenResumenDTO> transaccionesDto = new ArrayList<>();
        Set<AsientoContableResumenDTO> asientosSet = new LinkedHashSet<>();
        Set<LoteResumenDTO> lotesSet = new LinkedHashSet<>();
        for (TransaccionAlmacen transaccion : transacciones) {
            List<Movimiento> movimientosTransaccion = safeList(transaccion.getMovimientosTransaccion());
            long movimientosMaterial = movimientosTransaccion.stream()
                    .filter(movimiento -> productoId.equals(getProductoId(movimiento)))
                    .count();
            log.debug("[ELIM_FORZADA][MATERIAL][TRANSACCION] Estudio transaccion. productoId={}, transaccionId={}, tipoEntidadCausante={}, idEntidadCausante={}, estadoContable={}, asientoId={}, totalMovimientos={}, movimientosMaterial={}, movimientoIds={}, detalleMovimientos={}",
                    productoId,
                    transaccion.getTransaccionId(),
                    transaccion.getTipoEntidadCausante(),
                    transaccion.getIdEntidadCausante(),
                    transaccion.getEstadoContable(),
                    getAsientoId(transaccion),
                    movimientosTransaccion.size(),
                    movimientosMaterial,
                    movimientoIds(movimientosTransaccion),
                    movementDiagnostics(movimientosTransaccion));
            transaccionesDto.add(toTransaccionResumen(transaccion));
            if (transaccion.getAsientoContable() != null) {
                asientosSet.add(toAsientoResumen(transaccion.getAsientoContable()));
            }
            for (Movimiento movimiento : movimientosTransaccion) {
                if (productoId.equals(getProductoId(movimiento)) && movimiento.getLote() != null) {
                    lotesSet.add(toLoteResumen(movimiento.getLote()));
                }
            }
        }

        EstudiarEliminacionMaterialResponseDTO response = new EstudiarEliminacionMaterialResponseDTO();
        response.setMaterial(toMaterialResumen(material));
        response.setEliminable(true);
        response.setItemsOrdenCompra(itemsOrdenCompra);
        response.setLotes(new ArrayList<>(lotesSet));
        response.setTransaccionesAlmacen(transaccionesDto);
        response.setAsientosContables(new ArrayList<>(asientosSet));
        response.setInsumosReceta(insumosReceta);
        response.setInsumosEmpaque(insumosEmpaque);
        log.info("[ELIM_FORZADA][MATERIAL][ESTUDIO] Resumen dependencias. productoId={}, itemsOCM={}, transacciones={}, asientos={}, lotes={}, insumosReceta={}, insumosEmpaque={}",
                productoId,
                itemsOrdenCompra.size(),
                transacciones.size(),
                asientosSet.size(),
                lotesSet.size(),
                insumosReceta.size(),
                insumosEmpaque.size());
        return response;
    }

    @Transactional
    public void ejecutarEliminacionOrdenProduccion(int ordenProduccionId) {
        if (!ordenProduccionRepo.existsById(ordenProduccionId)) {
            throw new RuntimeException("OrdenProduccion not found with id: " + ordenProduccionId);
        }

        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteAndIdEntidadCausanteWithMovimientos(
                        TransaccionAlmacen.TipoEntidadCausante.OP,
                        ordenProduccionId
                );
        if (!transacciones.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede eliminar la orden de producción: tiene " + transacciones.size() + " transacción(es) de almacén asociada(s).");
        }

        List<Lote> lotes = loteRepo.findByOrdenProduccion_OrdenId(ordenProduccionId);
        for (Lote lote : lotes) {
            loteRepo.delete(lote);
        }

        ordenProduccionRepo.deleteById(ordenProduccionId);
        log.info("Eliminación forzada ejecutada para OP id: {}", ordenProduccionId);
    }

    @Transactional
    public void ejecutarEliminacionMaterial(String productoId) {
        Material material = requireMaterial(productoId);
        List<Integer> transaccionIdsDetectados = transaccionAlmacenHeaderRepo.findDistinctIdsByProductoId(productoId);
        List<ItemOrdenCompraMaterialResumenDTO> itemsOrdenCompra = itemOrdenCompraRepo.findByMaterial_ProductoId(productoId).stream()
                .map(this::toItemOrdenCompraMaterialResumen)
                .toList();
        List<SemiTerminado> semiTerminadosReceta = listSemiTerminadosQueReferencianMaterial(material);
        List<Terminado> terminadosReceta = listTerminadosQueReferencianMaterial(material);
        List<InsumoRecetaResumenDTO> insumosReceta = buildInsumosRecetaResumen(material, semiTerminadosReceta, terminadosReceta);
        List<InsumoEmpaqueResumenDTO> insumosEmpaque = buildInsumosEmpaqueResumen(productoId);

        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Inicio ejecucion. productoId={}, materialNombre={}, tipoMaterial={}, unidad={}, itemsOCM={}, transacciones={}, transaccionIds={}, insumosReceta={}, insumosEmpaque={}, semiTerminadoIdsReceta={}, terminadoIdsReceta={}",
                productoId,
                material.getNombre(),
                material.getTipoMaterial(),
                material.getTipoUnidades(),
                itemsOrdenCompra.size(),
                transaccionIdsDetectados.size(),
                integerIds(transaccionIdsDetectados),
                insumosReceta.size(),
                insumosEmpaque.size(),
                semiTerminadosReceta.stream().map(SemiTerminado::getProductoId).collect(Collectors.joining(",")),
                terminadosReceta.stream().map(Terminado::getProductoId).collect(Collectors.joining(",")));

        Set<Long> candidateStandaloneLoteIds = new LinkedHashSet<>();

        removeInsumosReferencingProductFromLoadedRecipeParents(productoId, semiTerminadosReceta, terminadosReceta);
        entityManager.flush();
        deleteRecipeConsumers(productoId);
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION][RECETA_CORRELACION] Lineas insumo en estudio previo (insumosReceta)={}. Comparar con cantidadEncontrada del ultimo log [ELIM_FORZADA][RECETA][INSUMO]; discrepancia puede indicar desajuste columna/JPQL o filas huérfanas en BD. productoId={}",
                insumosReceta.size(), productoId);
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Etapa receta completada. productoId={}", productoId);
        deletePackagingConsumers(productoId);
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Etapa empaque completada. productoId={}", productoId);
        processOrdenCompraItemsForMaterial(productoId, candidateStandaloneLoteIds);
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Etapa OCM completada. productoId={}, lotesCandidatosAcumulados={}",
                productoId, setIds(candidateStandaloneLoteIds));
        processMaterialTransactions(productoId, candidateStandaloneLoteIds);
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Etapa transacciones completada. productoId={}, lotesCandidatosAcumulados={}",
                productoId, setIds(candidateStandaloneLoteIds));
        cleanupTouchedStandaloneLotes(candidateStandaloneLoteIds);
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Cleanup de lotes completado. productoId={}, lotesEvaluados={}",
                productoId, setIds(candidateStandaloneLoteIds));

        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Solicitando productoRepo.deleteById. Las restricciones FK contra productos se validan al flush/commit de esta transaccion. productoId={}", productoId);
        productoRepo.deleteById(productoId);
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] deleteById(producto) invocado sin excepcion local; borrado definitivo sujeto a commit. productoId={}, materialNombre={}",
                productoId, material.getNombre());
        log.info("[ELIM_FORZADA][MATERIAL][EJECUCION] Pipeline de eliminacion material en sesion JPA listo; commit de transaccion pendiente. productoId={}", productoId);
        log.info("Eliminación forzada de Material id: {} — operaciones registradas en sesion; confirmar resultado en commit (sin error 23503 u otros).", productoId);
    }

    public EliminacionTerminadosBatchResultDTO ejecutarEliminacionTodosLosTerminados() {
        dangerousOperationGuard.assertNotProduction(PURGA_TERMINADOS_OPERATION);

        List<String> productoIds = terminadoRepo.findAllProductoIdsOrderByProductoIdAsc();
        EliminacionTerminadosBatchResultDTO result = new EliminacionTerminadosBatchResultDTO();
        result.setPermitted(true);
        result.setExecuted(true);
        result.setTotalTerminados(productoIds.size());
        result.setProductoIdsProcesados(new ArrayList<>(productoIds));

        if (productoIds.isEmpty()) {
            result.setMessage("No se encontraron terminados para purgar.");
            return result;
        }

        int eliminados = 0;
        List<EliminacionBatchFailureDTO> failures = new ArrayList<>();

        for (String productoId : productoIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> ejecutarEliminacionTerminado(productoId));
                eliminados++;
            } catch (RuntimeException e) {
                log.warn("Falló la purga del terminado {}: {}", productoId, e.getMessage(), e);
                failures.add(new EliminacionBatchFailureDTO(productoId, e.getMessage()));
            }
        }

        result.setEliminados(eliminados);
        result.setFailures(failures);
        result.setFallidos(failures.size());
        result.setMessage(failures.isEmpty()
                ? "Purga total de terminados completada."
                : "Purga total de terminados finalizada con errores parciales.");
        return result;
    }

    @Transactional
    public void ejecutarEliminacionTerminado(String productoId) {
        dangerousOperationGuard.assertNotProduction(ELIMINACION_TERMINADO_OPERATION);

        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con ID: " + productoId));

        if (!(producto instanceof Terminado)) {
            throw new IllegalStateException("El producto con ID " + productoId + " no es un Terminado.");
        }

        terminadoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el terminado con ID: " + productoId));

        deleteRecipeConsumers(productoId);
        deleteProductionHistory(productoId);
        deleteResidualProductTransactions(productoId);
        deleteManufacturingVersions(productoId);
        deleteSalesArtifacts(productoId);
        deleteAssociatedProcess(productoId);

        terminadoRepo.deleteById(productoId);
        log.info("Eliminación forzada ejecutada para Terminado id: {}", productoId);
    }

    private void deleteRecipeConsumers(String productoId) {
        List<Insumo> insumosConsumidores = insumoRepo.findByProducto_ProductoId(productoId);
        String insumoIds = insumosConsumidores.stream()
                .map(insumo -> String.valueOf(insumo.getInsumoId()))
                .collect(Collectors.joining(","));
        String inputProductoIdsEfectivos = insumosConsumidores.stream()
                .map(insumo -> insumo.getProducto() != null ? insumo.getProducto().getProductoId() : "null")
                .collect(Collectors.joining(","));
        log.info("[ELIM_FORZADA][RECETA][INSUMO] Busqueda por findByProducto_ProductoId (insumo de entrada / producto asociado). productoId={}, cantidadEncontrada={}, insumoIds={}, inputProductoIdsEfectivos={}",
                productoId, insumosConsumidores.size(), insumoIds, inputProductoIdsEfectivos);
        if (!insumosConsumidores.isEmpty()) {
            insumoRepo.deleteAll(insumosConsumidores);
            insumoRepo.flush();
            log.info("[ELIM_FORZADA][RECETA][INSUMO] deleteAll y flush ejecutados para {} entidades Insumo. productoId={}, insumoIds={}",
                    insumosConsumidores.size(), productoId, insumoIds);
        }
    }

    /**
     * Quita líneas de receta que referencian el producto como insumo de entrada desde las colecciones
     * ya cargadas de padres (sesión JPA), guarda los cambios y permite que orphanRemoval persista el borrado de hijos
     * antes de otras operaciones en la misma transacción.
     */
    private void removeInsumosReferencingProductFromLoadedRecipeParents(
            String productoId,
            List<SemiTerminado> semiTerminados,
            List<Terminado> terminados
    ) {
        int semiTerminadosGuardados = 0;
        int terminadosGuardados = 0;

        for (SemiTerminado semi : semiTerminados) {
            List<Insumo> insumos = semi.getInsumos();
            if (insumos == null || insumos.isEmpty()) {
                continue;
            }
            boolean changed = insumos.removeIf(insumo -> productoId.equals(
                    insumo.getProducto() != null ? insumo.getProducto().getProductoId() : null));
            if (changed) {
                semiTerminadoRepo.save(semi);
                semiTerminadosGuardados++;
            }
        }

        for (Terminado terminado : terminados) {
            List<Insumo> insumos = terminado.getInsumos();
            if (insumos == null || insumos.isEmpty()) {
                continue;
            }
            boolean changed = insumos.removeIf(insumo -> productoId.equals(
                    insumo.getProducto() != null ? insumo.getProducto().getProductoId() : null));
            if (changed) {
                terminadoRepo.save(terminado);
                terminadosGuardados++;
            }
        }

        log.info("[ELIM_FORZADA][MATERIAL][RECETA_SYNC] Lineas de receta retiradas desde colecciones cargadas (orphanRemoval). productoId={}, semiTerminadosGuardados={}, terminadosGuardados={}",
                productoId, semiTerminadosGuardados, terminadosGuardados);
    }

    private void deletePackagingConsumers(String productoId) {
        List<Terminado> terminados = terminadoRepo
                .findDistinctByCasePack_InsumosEmpaque_Material_ProductoId(productoId);
        int conCasePackInsumos = 0;
        int terminadosGuardadosPorQuitarEmpaque = 0;

        for (Terminado terminado : terminados) {
            if (terminado.getCasePack() == null || terminado.getCasePack().getInsumosEmpaque() == null) {
                continue;
            }

            conCasePackInsumos++;
            boolean changed = terminado.getCasePack().getInsumosEmpaque()
                    .removeIf(insumoEmpaque -> productoId.equals(getMaterialId(insumoEmpaque)));

            if (changed) {
                terminadosGuardadosPorQuitarEmpaque++;
                terminadoRepo.save(terminado);
            }
        }

        log.info("[ELIM_FORZADA][EMPAQUE] productoId={}, terminadosEnConsulta={}, conCasePackEInsumosEmpaque={}, terminadosGuardadosTrasQuitarInsumoEmpaqueDelMaterial={}",
                productoId, terminados.size(), conCasePackInsumos, terminadosGuardadosPorQuitarEmpaque);
    }

    private void deleteProductionHistory(String productoId) {
        List<OrdenProduccion> ordenesProduccion = ordenProduccionRepo.findByProducto_ProductoId(productoId);
        for (OrdenProduccion ordenProduccion : ordenesProduccion) {
            deleteOrderRootedTransactions(ordenProduccion.getOrdenId());

            List<Lote> lotes = loteRepo.findByOrdenProduccion_OrdenId(ordenProduccion.getOrdenId());
            if (!lotes.isEmpty()) {
                loteRepo.deleteAll(lotes);
            }

            ordenProduccionRepo.delete(ordenProduccion);
        }
    }

    private void deleteOrderRootedTransactions(int ordenProduccionId) {
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo
                .findByTipoEntidadCausanteInAndIdEntidadCausanteWithMovimientos(
                        TIPOS_TRANSACCION_OP_PURGA,
                        ordenProduccionId
                );

        for (TransaccionAlmacen transaccion : transacciones) {
            deleteWholeTransaction(transaccion);
        }
    }

    private void deleteResidualProductTransactions(String productoId) {
        List<TransaccionAlmacen> transacciones = transaccionAlmacenHeaderRepo.findDistinctByProductoIdWithMovimientos(productoId);
        Set<Long> candidateStandaloneLoteIds = new LinkedHashSet<>();

        for (TransaccionAlmacen transaccion : transacciones) {
            List<Movimiento> movimientosObjetivo = safeList(transaccion.getMovimientosTransaccion()).stream()
                    .filter(movimiento -> productoId.equals(getProductoId(movimiento)))
                    .toList();

            for (Movimiento movimiento : movimientosObjetivo) {
                if (movimiento.getLote() != null && movimiento.getLote().getId() != null) {
                    candidateStandaloneLoteIds.add(movimiento.getLote().getId());
                }
            }

            if (movimientosObjetivo.isEmpty()) {
                continue;
            }

            if (movimientosObjetivo.size() == safeList(transaccion.getMovimientosTransaccion()).size()) {
                deleteWholeTransaction(transaccion);
                continue;
            }

            transaccion.getMovimientosTransaccion().removeIf(movimiento -> productoId.equals(getProductoId(movimiento)));
            transaccionAlmacenHeaderRepo.save(transaccion);
        }

        deleteOrphanStandaloneLotes(candidateStandaloneLoteIds);
    }

    private void processOrdenCompraItemsForMaterial(String productoId, Set<Long> candidateStandaloneLoteIds) {
        List<ItemOrdenCompra> items = itemOrdenCompraRepo.findByMaterial_ProductoId(productoId);
        if (items.isEmpty()) {
            log.debug("[ELIM_FORZADA][MATERIAL][OCM] No se encontraron items OCM para productoId={}", productoId);
            return;
        }

        Set<Integer> ordenesAfectadas = items.stream()
                .map(ItemOrdenCompra::getOrdenCompraMateriales)
                .filter(Objects::nonNull)
                .map(orden -> orden.getOrdenCompraId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        log.info("[ELIM_FORZADA][MATERIAL][OCM] Procesando items OCM. productoId={}, totalItems={}, itemIds={}, ordenesAfectadas={}",
                productoId,
                items.size(),
                items.stream().map(item -> String.valueOf(item.getItemOrdenId())).collect(Collectors.joining(",")),
                setIds(new LinkedHashSet<>(ordenesAfectadas)));

        itemOrdenCompraRepo.deleteAll(items);
        itemOrdenCompraRepo.flush();
        log.info("[ELIM_FORZADA][MATERIAL][OCM] Items OCM eliminados. productoId={}, totalItems={}", productoId, items.size());

        for (Integer ordenCompraId : ordenesAfectadas) {
            if (itemOrdenCompraRepo.countByOrdenCompraMateriales_OrdenCompraId(ordenCompraId) > 0) {
                log.info("[ELIM_FORZADA][MATERIAL][OCM] Orden de compra conservada. productoId={}, ordenCompraId={}, motivo=TIENE_OTROS_ITEMS",
                        productoId, ordenCompraId);
                continue;
            }

            List<Lote> lotesDeOrden = loteRepo.findByOrdenCompraMateriales_OrdenCompraId(ordenCompraId);
            for (Lote lote : lotesDeOrden) {
                lote.setOrdenCompraMateriales(null);
                loteRepo.save(lote);
                if (lote.getId() != null) {
                    candidateStandaloneLoteIds.add(lote.getId());
                }
            }

            log.info("[ELIM_FORZADA][MATERIAL][OCM] Orden de compra eliminada. productoId={}, ordenCompraId={}, lotesTocados={}",
                    productoId, ordenCompraId, lotesDeOrden.stream()
                            .map(Lote::getId)
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")));
            ordenCompraRepo.deleteById(ordenCompraId);
        }
    }

    private void processMaterialTransactions(String productoId, Set<Long> candidateStandaloneLoteIds) {
        List<TransaccionAlmacen> transacciones = loadMaterialTransactionsWithAllMovimientos(productoId);
        log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Procesando transacciones. productoId={}, totalTransacciones={}, transaccionIds={}",
                productoId, transacciones.size(), transaccionIds(transacciones));

        for (TransaccionAlmacen transaccion : transacciones) {
            List<Movimiento> movimientosTotales = safeList(transaccion.getMovimientosTransaccion());
            List<Movimiento> movimientosObjetivo = movimientosTotales.stream()
                    .filter(movimiento -> productoId.equals(getProductoId(movimiento)))
                    .toList();

            for (Movimiento movimiento : movimientosObjetivo) {
                if (movimiento.getLote() != null && movimiento.getLote().getId() != null) {
                    candidateStandaloneLoteIds.add(movimiento.getLote().getId());
                }
            }

            if (movimientosObjetivo.isEmpty()) {
                log.debug("[ELIM_FORZADA][MATERIAL][TRANSACCION] Transaccion ignorada al ejecutar. productoId={}, transaccionId={}, motivo=SIN_MOVIMIENTOS_DEL_MATERIAL",
                        productoId, transaccion.getTransaccionId());
                continue;
            }

            if (movimientosObjetivo.size() == movimientosTotales.size()) {
                log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Decision tomada. productoId={}, transaccionId={}, decision=TRANSACCION_EXCLUSIVA_ELIMINAR_HEADER, totalMovimientos={}, movimientosMaterial={}, movimientoIdsMaterial={}, lotesCandidatos={}",
                        productoId,
                        transaccion.getTransaccionId(),
                        movimientosTotales.size(),
                        movimientosObjetivo.size(),
                        movimientoIds(movimientosObjetivo),
                        movimientoLoteIds(movimientosObjetivo));
                deleteWholeMaterialTransaction(productoId, transaccion);
                continue;
            }

            int beforeCount = movimientosTotales.size();
            detachMovimientosFromTransaccion(transaccion, movimientosObjetivo);
            transaccionAlmacenHeaderRepo.saveAndFlush(transaccion);
            log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Decision tomada. productoId={}, transaccionId={}, decision=TRANSACCION_MIXTA_CONSERVAR_HEADER, estrategia=DESASOCIAR_MOVIMIENTOS_Y_APLICAR_ORPHAN_REMOVAL, totalMovimientosAntes={}, totalMovimientosDespues={}, movimientosMaterialRemovidos={}, movimientoIdsMaterial={}, lotesCandidatos={}",
                    productoId,
                    transaccion.getTransaccionId(),
                    beforeCount,
                    safeList(transaccion.getMovimientosTransaccion()).size(),
                    movimientosObjetivo.size(),
                    movimientoIds(movimientosObjetivo),
                    movimientoLoteIds(movimientosObjetivo));
        }
    }

    private List<TransaccionAlmacen> loadMaterialTransactionsWithAllMovimientos(String productoId) {
        List<Integer> transaccionIds = transaccionAlmacenHeaderRepo.findDistinctIdsByProductoId(productoId);
        log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] IDs detectados para recarga. productoId={}, transaccionIds={}",
                productoId, integerIds(transaccionIds));

        List<TransaccionAlmacen> transacciones = new ArrayList<>();
        List<Integer> missingIds = new ArrayList<>();

        for (Integer transaccionId : transaccionIds) {
            var loaded = transaccionAlmacenHeaderRepo.findByIdWithMovimientos(transaccionId);
            if (loaded.isPresent()) {
                TransaccionAlmacen transaccion = loaded.get();
                transacciones.add(transaccion);
                log.debug("[ELIM_FORZADA][MATERIAL][TRANSACCION] Transaccion recargada completa. productoId={}, transaccionId={}, totalMovimientos={}, movimientoIds={}, detalleMovimientos={}",
                        productoId,
                        transaccion.getTransaccionId(),
                        safeList(transaccion.getMovimientosTransaccion()).size(),
                        movimientoIds(safeList(transaccion.getMovimientosTransaccion())),
                        movementDiagnostics(safeList(transaccion.getMovimientosTransaccion())));
            } else {
                missingIds.add(transaccionId);
            }
        }

        if (!missingIds.isEmpty()) {
            log.warn("[ELIM_FORZADA][MATERIAL][TRANSACCION] Algunas transacciones no pudieron recargarse. productoId={}, transaccionIdsNoRecargadas={}",
                    productoId, integerIds(missingIds));
        }

        log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Recarga completa finalizada. productoId={}, totalRecargadas={}, transaccionIdsRecargadas={}",
                productoId, transacciones.size(), transaccionIds(transacciones));
        return transacciones;
    }

    private void deleteWholeMaterialTransaction(String productoId, TransaccionAlmacen transaccion) {
        AsientoContable asientoContable = transaccion.getAsientoContable();
        List<Movimiento> movimientos = new ArrayList<>(safeList(transaccion.getMovimientosTransaccion()));
        try {
            log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Inicio borrado completo. productoId={}, transaccionId={}, asientoId={}, estrategia=ELIMINAR_AGREGADO_RAIZ_CON_CASCADE_REMOVE, totalMovimientos={}, movimientoIds={}, detalleMovimientos={}",
                    productoId,
                    transaccion.getTransaccionId(),
                    asientoContable != null ? asientoContable.getId() : null,
                    movimientos.size(),
                    movimientoIds(movimientos),
                    movementDiagnostics(movimientos));

            if (asientoContable != null) {
                transaccion.setAsientoContable(null);
                transaccionAlmacenHeaderRepo.saveAndFlush(transaccion);
                log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Asiento desacoplado. productoId={}, transaccionId={}, asientoId={}",
                        productoId, transaccion.getTransaccionId(), asientoContable.getId());
            }

            transaccionAlmacenHeaderRepo.delete(transaccion);
            transaccionAlmacenHeaderRepo.flush();
            log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Header eliminado con cascade/orphanRemoval. productoId={}, transaccionId={}, totalMovimientosCascada={}, movimientoIdsCascada={}",
                    productoId, transaccion.getTransaccionId(), movimientos.size(), movimientoIds(movimientos));

            if (asientoContable != null) {
                if (!isAsientoStillReferenced(asientoContable.getId())) {
                    asientoContableRepo.deleteById(asientoContable.getId());
                    log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Asiento eliminado. productoId={}, transaccionId={}, asientoId={}",
                            productoId, transaccion.getTransaccionId(), asientoContable.getId());
                } else {
                    log.info("[ELIM_FORZADA][MATERIAL][TRANSACCION] Asiento conservado por referencias activas. productoId={}, transaccionId={}, asientoId={}",
                            productoId, transaccion.getTransaccionId(), asientoContable.getId());
                }
            }
        } catch (RuntimeException e) {
            log.error("[ELIM_FORZADA][MATERIAL][TRANSACCION] Error en borrado completo. productoId={}, transaccionId={}, asientoId={}, totalMovimientos={}, movimientoIds={}, message={}",
                    productoId,
                    transaccion.getTransaccionId(),
                    asientoContable != null ? asientoContable.getId() : null,
                    movimientos.size(),
                    movimientoIds(movimientos),
                    e.getMessage(),
                    e);
            throw e;
        }
    }

    private void detachMovimientosFromTransaccion(TransaccionAlmacen transaccion, List<Movimiento> movimientos) {
        for (Movimiento movimiento : movimientos) {
            detachMovimientoFromTransaccion(transaccion, movimiento);
        }
    }

    private void detachMovimientoFromTransaccion(TransaccionAlmacen transaccion, Movimiento movimiento) {
        List<Movimiento> movimientosTransaccion = transaccion.getMovimientosTransaccion();
        if (movimientosTransaccion != null) {
            movimientosTransaccion.remove(movimiento);
        }
        movimiento.setTransaccionAlmacen(null);
    }

    private void cleanupTouchedStandaloneLotes(Set<Long> loteIds) {
        log.info("[ELIM_FORZADA][MATERIAL][LOTE] Iniciando cleanup de lotes tocados. loteIds={}", setIds(loteIds));
        for (Long loteId : loteIds) {
            loteRepo.findById(loteId).ifPresent(lote -> {
                long transaccionesReferenciandoLote = transaccionAlmacenRepo.countByLote_Id(loteId);
                if (transaccionesReferenciandoLote == 0 && lote.getOrdenCompraMateriales() != null) {
                    lote.setOrdenCompraMateriales(null);
                    loteRepo.save(lote);
                    log.info("[ELIM_FORZADA][MATERIAL][LOTE] Lote desacoplado de OCM. loteId={}, batchNumber={}, motivo=SIN_MOVIMIENTOS_RESTANTES",
                            loteId, lote.getBatchNumber());
                } else {
                    log.debug("[ELIM_FORZADA][MATERIAL][LOTE] Lote conservado en cleanup inicial. loteId={}, batchNumber={}, movimientosRestantes={}, tieneOCM={}, tieneOP={}",
                            loteId,
                            lote.getBatchNumber(),
                            transaccionesReferenciandoLote,
                            lote.getOrdenCompraMateriales() != null,
                            lote.getOrdenProduccion() != null);
                }
            });
        }

        deleteOrphanStandaloneLotes(loteIds);
    }

    private void deleteOrphanStandaloneLotes(Set<Long> loteIds) {
        for (Long loteId : loteIds) {
            loteRepo.findById(loteId).ifPresent(lote -> {
                boolean noTieneRelacionesRaiz = lote.getOrdenCompraMateriales() == null && lote.getOrdenProduccion() == null;
                boolean sinMovimientos = transaccionAlmacenRepo.countByLote_Id(loteId) == 0;
                if (noTieneRelacionesRaiz && sinMovimientos) {
                    log.info("[ELIM_FORZADA][MATERIAL][LOTE] Lote eliminado por quedar huerfano. loteId={}, batchNumber={}",
                            loteId, lote.getBatchNumber());
                    loteRepo.delete(lote);
                } else {
                    log.debug("[ELIM_FORZADA][MATERIAL][LOTE] Lote conservado. loteId={}, batchNumber={}, noTieneRelacionesRaiz={}, sinMovimientos={}",
                            loteId, lote.getBatchNumber(), noTieneRelacionesRaiz, sinMovimientos);
                }
            });
        }
    }

    private Long getAsientoId(TransaccionAlmacen transaccion) {
        return transaccion.getAsientoContable() != null ? transaccion.getAsientoContable().getId() : null;
    }

    private String transaccionIds(List<TransaccionAlmacen> transacciones) {
        return transacciones.stream()
                .map(transaccion -> String.valueOf(transaccion.getTransaccionId()))
                .collect(Collectors.joining(","));
    }

    private String integerIds(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String setIds(Set<?> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String movimientoIds(List<Movimiento> movimientos) {
        return movimientos.stream()
                .map(movimiento -> String.valueOf(movimiento.getMovimientoId()))
                .collect(Collectors.joining(","));
    }

    private String movimientoLoteIds(List<Movimiento> movimientos) {
        return movimientos.stream()
                .map(Movimiento::getLote)
                .filter(Objects::nonNull)
                .map(Lote::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private String movementDiagnostics(List<Movimiento> movimientos) {
        return movimientos.stream()
                .map(movimiento -> "movimientoId=" + movimiento.getMovimientoId()
                        + ",productoId=" + getProductoId(movimiento)
                        + ",cantidad=" + movimiento.getCantidad()
                        + ",tipoMovimiento=" + movimiento.getTipoMovimiento()
                        + ",almacen=" + movimiento.getAlmacen()
                        + ",loteId=" + (movimiento.getLote() != null ? movimiento.getLote().getId() : null))
                .collect(Collectors.joining(" | "));
    }

    private void deleteManufacturingVersions(String productoId) {
        List<ManufacturingVersions> versions = manufacturingVersionRepo.findByProducto_ProductoId(productoId);
        if (!versions.isEmpty()) {
            manufacturingVersionRepo.deleteAll(versions);
        }
    }

    private void deleteSalesArtifacts(String productoId) {
        List<ItemFacturaVenta> itemsFactura = itemFacturaVentaRepo.findByProducto_ProductoId(productoId);
        Set<Integer> facturaIds = itemsFactura.stream()
                .map(ItemFacturaVenta::getFacturaVenta)
                .filter(Objects::nonNull)
                .map(factura -> factura.getFacturaVentaId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> ordenIds = itemsFactura.stream()
                .map(ItemFacturaVenta::getFacturaVenta)
                .filter(Objects::nonNull)
                .map(factura -> factura.getOrdenVenta() != null ? factura.getOrdenVenta().getOrdenVentaId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!itemsFactura.isEmpty()) {
            itemFacturaVentaRepo.deleteAll(itemsFactura);
        }

        List<ItemOrdenVenta> itemsOrden = itemOrdenVentaRepo.findByProducto_ProductoId(productoId);
        ordenIds.addAll(itemsOrden.stream()
                .map(ItemOrdenVenta::getOrdenVenta)
                .filter(Objects::nonNull)
                .map(orden -> orden.getOrdenVentaId())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        if (!itemsOrden.isEmpty()) {
            itemOrdenVentaRepo.deleteAll(itemsOrden);
        }

        for (Integer facturaId : facturaIds) {
            if (itemFacturaVentaRepo.countByFacturaVenta_FacturaVentaId(facturaId) == 0) {
                facturaVentaRepo.deleteById(facturaId);
            }
        }

        for (Integer ordenId : ordenIds) {
            boolean hasItems = itemOrdenVentaRepo.countByOrdenVenta_OrdenVentaId(ordenId) > 0;
            boolean hasFacturas = facturaVentaRepo.existsByOrdenVenta_OrdenVentaId(ordenId);
            if (!hasItems && !hasFacturas) {
                ordenVentaRepo.deleteById(ordenId);
            }
        }
    }

    private void deleteAssociatedProcess(String productoId) {
        List<ProcesoProduccionCompleto> procesos = procesoProduccionCompletoRepo.findByProducto_ProductoIdWithNodes(productoId);

        terminadoRepo.clearProcesoProduccionCompletoByProductoId(productoId);
        entityManager.flush();

        if (!procesos.isEmpty()) {
            procesoProduccionCompletoRepo.deleteAll(procesos);
            entityManager.flush();
        }

        procesoProduccionCompletoRepo.clearProductoByProductoId(productoId);
        entityManager.flush();
        entityManager.clear();
    }

    private void deleteWholeTransaction(TransaccionAlmacen transaccion) {
        AsientoContable asientoContable = transaccion.getAsientoContable();
        if (asientoContable != null) {
            transaccion.setAsientoContable(null);
            transaccionAlmacenHeaderRepo.saveAndFlush(transaccion);
        }

        transaccionAlmacenHeaderRepo.delete(transaccion);
        transaccionAlmacenHeaderRepo.flush();

        if (asientoContable != null && !isAsientoStillReferenced(asientoContable.getId())) {
            asientoContableRepo.deleteById(asientoContable.getId());
        }
    }

    private boolean isAsientoStillReferenced(Long asientoId) {
        return transaccionAlmacenHeaderRepo.existsByAsientoContable_Id(asientoId)
                || incorporacionActivoHeaderRepo.existsByAsientoContable_Id(asientoId)
                || depreciacionActivoRepo.existsByAsientoContable_Id(asientoId)
                || documentoBajaActivoRepo.existsByAsientoContable_Id(asientoId);
    }

    private String getProductoId(Movimiento movimiento) {
        return movimiento.getProducto() != null ? movimiento.getProducto().getProductoId() : null;
    }

    private String getMaterialId(InsumoEmpaque insumoEmpaque) {
        return insumoEmpaque.getMaterial() != null ? insumoEmpaque.getMaterial().getProductoId() : null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Material requireMaterial(String productoId) {
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con ID: " + productoId));

        if (!(producto instanceof Material material)) {
            throw new IllegalStateException("El producto con ID " + productoId + " no es un Material.");
        }

        return material;
    }

    private MaterialEliminacionResumenDTO toMaterialResumen(Material material) {
        return new MaterialEliminacionResumenDTO(
                material.getProductoId(),
                material.getNombre(),
                material.getTipoMaterial(),
                material.getTipoUnidades()
        );
    }

    private ItemOrdenCompraMaterialResumenDTO toItemOrdenCompraMaterialResumen(ItemOrdenCompra item) {
        return new ItemOrdenCompraMaterialResumenDTO(
                item.getItemOrdenId(),
                item.getOrdenCompraMateriales() != null ? item.getOrdenCompraMateriales().getOrdenCompraId() : 0,
                item.getOrdenCompraMateriales() != null && item.getOrdenCompraMateriales().getProveedor() != null
                        ? item.getOrdenCompraMateriales().getProveedor().getNombre()
                        : null,
                item.getOrdenCompraMateriales() != null ? item.getOrdenCompraMateriales().getEstado() : null,
                item.getCantidad(),
                item.getPrecioUnitario(),
                item.getSubTotal()
        );
    }

    /**
     * Terminados cuya lista {@code insumos} referencia este material como producto de entrada.
     */
    private List<Terminado> listTerminadosQueReferencianMaterial(Material material) {
        return terminadoRepo.findByInsumos_Producto(material);
    }

    /**
     * SemiTerminados cuya lista {@code insumos} referencia este material como producto de entrada.
     */
    private List<SemiTerminado> listSemiTerminadosQueReferencianMaterial(Material material) {
        return semiTerminadoRepo.findByInsumos_Producto(material);
    }

    private List<InsumoRecetaResumenDTO> buildInsumosRecetaResumen(
            Material material,
            List<SemiTerminado> semiTerminados,
            List<Terminado> terminados
    ) {
        List<InsumoRecetaResumenDTO> result = new ArrayList<>();

        for (SemiTerminado semiTerminado : semiTerminados) {
            for (Insumo insumo : safeList(semiTerminado.getInsumos())) {
                if (material.getProductoId().equals(insumo.getProducto() != null ? insumo.getProducto().getProductoId() : null)) {
                    result.add(new InsumoRecetaResumenDTO(
                            insumo.getInsumoId(),
                            semiTerminado.getProductoId(),
                            semiTerminado.getNombre(),
                            semiTerminado.getTipo_producto(),
                            insumo.getCantidadRequerida()
                    ));
                }
            }
        }

        for (Terminado terminado : terminados) {
            for (Insumo insumo : safeList(terminado.getInsumos())) {
                if (material.getProductoId().equals(insumo.getProducto() != null ? insumo.getProducto().getProductoId() : null)) {
                    result.add(new InsumoRecetaResumenDTO(
                            insumo.getInsumoId(),
                            terminado.getProductoId(),
                            terminado.getNombre(),
                            terminado.getTipo_producto(),
                            insumo.getCantidadRequerida()
                    ));
                }
            }
        }

        return result;
    }

    private List<InsumoEmpaqueResumenDTO> buildInsumosEmpaqueResumen(String productoId) {
        List<InsumoEmpaqueResumenDTO> result = new ArrayList<>();

        for (Terminado terminado : terminadoRepo.findDistinctByCasePack_InsumosEmpaque_Material_ProductoId(productoId)) {
            if (terminado.getCasePack() == null) {
                continue;
            }

            for (InsumoEmpaque insumoEmpaque : safeList(terminado.getCasePack().getInsumosEmpaque())) {
                if (!productoId.equals(getMaterialId(insumoEmpaque))) {
                    continue;
                }

                result.add(new InsumoEmpaqueResumenDTO(
                        insumoEmpaque.getId(),
                        terminado.getProductoId(),
                        terminado.getNombre(),
                        terminado.getCasePack().getUnitsPerCase(),
                        insumoEmpaque.getCantidad(),
                        insumoEmpaque.getUom()
                ));
            }
        }

        return result;
    }

    private ItemOrdenCompraResumenDTO toItemResumen(ItemOrdenCompra item) {
        ItemOrdenCompraResumenDTO dto = new ItemOrdenCompraResumenDTO();
        dto.setItemOrdenId(item.getItemOrdenId());
        dto.setProductId(item.getMaterial() != null ? item.getMaterial().getProductoId() : null);
        dto.setCantidad(item.getCantidad());
        dto.setPrecioUnitario(item.getPrecioUnitario());
        dto.setSubTotal(item.getSubTotal());
        return dto;
    }

    private LoteResumenDTO toLoteResumen(Lote lote) {
        LoteResumenDTO dto = new LoteResumenDTO();
        dto.setId(lote.getId());
        dto.setBatchNumber(lote.getBatchNumber());
        dto.setProductionDate(lote.getProductionDate());
        dto.setExpirationDate(lote.getExpirationDate());
        return dto;
    }

    private MovimientoResumenDTO toMovimientoResumen(Movimiento m) {
        MovimientoResumenDTO dto = new MovimientoResumenDTO();
        dto.setMovimientoId(m.getMovimientoId());
        dto.setCantidad(m.getCantidad());
        dto.setProductId(m.getProducto() != null ? m.getProducto().getProductoId() : null);
        dto.setTipoMovimiento(m.getTipoMovimiento() != null ? m.getTipoMovimiento().name() : null);
        dto.setAlmacen(m.getAlmacen() != null ? m.getAlmacen().name() : null);
        dto.setFechaMovimiento(m.getFechaMovimiento());
        return dto;
    }

    private AsientoContableResumenDTO toAsientoResumen(AsientoContable a) {
        AsientoContableResumenDTO dto = new AsientoContableResumenDTO();
        dto.setId(a.getId());
        dto.setFecha(a.getFecha());
        dto.setDescripcion(a.getDescripcion());
        dto.setModulo(a.getModulo());
        dto.setDocumentoOrigen(a.getDocumentoOrigen());
        dto.setEstado(a.getEstado() != null ? a.getEstado().name() : null);
        return dto;
    }

    private TransaccionAlmacenResumenDTO toTransaccionResumen(TransaccionAlmacen ta) {
        TransaccionAlmacenResumenDTO dto = new TransaccionAlmacenResumenDTO();
        dto.setTransaccionId(ta.getTransaccionId());
        dto.setFechaTransaccion(ta.getFechaTransaccion());
        dto.setEstadoContable(ta.getEstadoContable() != null ? ta.getEstadoContable().name() : null);
        dto.setObservaciones(ta.getObservaciones());
        if (ta.getMovimientosTransaccion() != null) {
            dto.setMovimientos(ta.getMovimientosTransaccion().stream()
                    .map(this::toMovimientoResumen)
                    .collect(Collectors.toList()));
        } else {
            dto.setMovimientos(List.of());
        }
        dto.setAsientoContable(ta.getAsientoContable() != null ? toAsientoResumen(ta.getAsientoContable()) : null);
        return dto;
    }
}
