package exotic.app.planta.service.productos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.fabricacion.MpsFabricacionSemanalRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.TerminadoRepo;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.model.producto.manufacturing.receta.Insumo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SemiTerService {

    private final ProductoRepo productoRepo;
    private final SemiTerminadoRepo semiTerminadoRepo;
    private final TerminadoRepo terminadoRepo;
    private final InsumoRepo insumoRepo;
    private final TransaccionAlmacenRepo transaccionAlmacenRepo;
    private final OrdenProduccionRepo ordenProduccionRepo;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final ObjectMapper objectMapper;
    private final MpsFabricacionSemanalRepo mpsFabricacionSemanalRepo;

    private static final String PRODUCTO_EN_MPS_OF =
            "El semiterminado tiene propuestas en MPS OF. Retire esas propuestas antes de eliminarlo.";

    /**
     * Verifica si un Semiterminado o Terminado puede ser eliminado.
     * Un producto es eliminable si:
     * 1. Su stock es 0
     * 2. No existe ninguna orden de producción abierta que lo relacione
     *
     * @param productoId ID del producto a verificar
     * @return Un objeto con información sobre si el producto es eliminable y la razón si no lo es
     */
    @Transactional(readOnly = true)
    public Map<String, Object> isProductoDeletable(String productoId) {
        log.info("Verificando si el producto con ID: {} es eliminable", productoId);
        Map<String, Object> result = new HashMap<>();

        // Verificar que el producto existe y es un Semiterminado o Terminado
        Optional<Producto> optProducto = productoRepo.findById(productoId);
        if (optProducto.isEmpty()) {
            result.put("deletable", false);
            result.put("reason", "No se encontró el producto con ID: " + productoId);
            return result;
        }

        Producto producto = optProducto.get();
        if (!(producto instanceof SemiTerminado) && !(producto instanceof Terminado)) {
            result.put("deletable", false);
            result.put("reason", "El producto con ID: " + productoId + " no es un Semiterminado ni un Terminado");
            return result;
        }

        if (producto instanceof SemiTerminado && mpsFabricacionSemanalRepo.existsByDetalles_SemiTerminado_ProductoId(productoId)) {
            result.put("deletable", false);
            result.put("reason", PRODUCTO_EN_MPS_OF);
            return result;
        }

        // Verificar que el stock es 0
        Double stock = transaccionAlmacenRepo.findTotalCantidadByProductoId(productoId);
        stock = (stock != null) ? stock : 0.0;

        if (stock > 0) {
            result.put("deletable", false);
            result.put("reason", "El producto tiene stock disponible: " + stock);
            result.put("stock", stock);
            return result;
        }

        // Verificar que no existen órdenes de producción abiertas que lo relacionen
        long openOrders = ordenProduccionRepo.countByProducto_ProductoIdAndEstadoOrden(productoId, 0);
        if (openOrders > 0) {
            result.put("deletable", false);
            result.put("reason", "El producto está relacionado con " + openOrders + " orden(es) de producción abierta(s)");
            result.put("openOrdersCount", openOrders);
            return result;
        }

        // Si no hay problemas, el producto es eliminable
        result.put("deletable", true);
        return result;
    }

    /**
     * Elimina un Semiterminado o Terminado de forma provisional.
     * Este método solo debe usarse en fases iniciales de adopción de la aplicación
     * para eliminar productos de prueba o con errores.
     * 
     * @param productoId ID del producto a eliminar
     * @return Un objeto con información sobre el resultado de la operación
     * @throws IllegalStateException Si el producto no es eliminable
     */
    @Transactional
    public Map<String, Object> deleteProductoProvisional(String productoId) {
        log.info("Eliminando provisionalmente el producto con ID: {}", productoId);

        // Verificar si el producto es eliminable
        Map<String, Object> checkResult = isProductoDeletable(productoId);
        if (!(boolean)checkResult.get("deletable")) {
            log.warn("No se puede eliminar el producto con ID {}: {}", productoId, checkResult.get("reason"));
            throw new IllegalStateException(checkResult.get("reason").toString());
        }

        // Obtener el producto
        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con ID: " + productoId));

        // Eliminar órdenes de producción relacionadas (incluso las cerradas)
        List<OrdenProduccion> ordenes = ordenProduccionRepo.findByProducto_ProductoId(productoId);
        if (!ordenes.isEmpty()) {
            log.info("Eliminando {} órdenes de producción relacionadas con el producto {}", ordenes.size(), productoId);
            ordenProduccionRepo.deleteAll(ordenes);
        }

        // Eliminar el producto según su tipo
        if (producto instanceof SemiTerminado) {
            log.info("Eliminando SemiTerminado con ID: {}", productoId);
            semiTerminadoRepo.deleteById(productoId);
        } else if (producto instanceof Terminado) {
            log.info("Eliminando Terminado con ID: {}", productoId);
            terminadoRepo.deleteById(productoId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Producto eliminado correctamente");
        return result;
    }

    /**
     * Fuerza la eliminación de un Semiterminado o Terminado junto con sus dependencias directas
     * para evitar bloqueos por integridad referencial.
     *
     * @param productoId ID del producto a eliminar
     * @return Mapa con el resultado de la operación y conteos de entidades eliminadas
     */
    @Transactional
    public Map<String, Object> forceDeleteProducto(String productoId) {
        log.info("Forzando eliminación del producto con ID: {}", productoId);

        Producto producto = productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con ID: " + productoId));

        if (!(producto instanceof SemiTerminado) && !(producto instanceof Terminado)) {
            throw new IllegalStateException("Solo se pueden eliminar productos Semiterminados o Terminados");
        }

        if (producto instanceof SemiTerminado && mpsFabricacionSemanalRepo.existsByDetalles_SemiTerminado_ProductoId(productoId)) {
            throw new IllegalStateException(PRODUCTO_EN_MPS_OF);
        }

        Map<String, Object> result = new HashMap<>();

        List<Movimiento> movimientos = transaccionAlmacenRepo.findByProducto_ProductoId(productoId);
        int deletedMovimientos = movimientos.size();
        if (!movimientos.isEmpty()) {
            log.info("Eliminando {} movimientos de inventario asociados al producto {}", deletedMovimientos, productoId);
            transaccionAlmacenRepo.deleteAll(movimientos);
        }

        List<OrdenProduccion> ordenes = ordenProduccionRepo.findByProducto_ProductoId(productoId);
        int deletedOrdenes = ordenes.size();
        if (!ordenes.isEmpty()) {
            log.info("Eliminando {} órdenes de producción asociadas al producto {}", deletedOrdenes, productoId);
            ordenProduccionRepo.deleteAll(ordenes);
        }

        List<Insumo> insumos = insumoRepo.findByProducto_ProductoId(productoId);
        int deletedInsumos = insumos.size();
        if (!insumos.isEmpty()) {
            log.info("Eliminando {} insumos que referencian al producto {}", deletedInsumos, productoId);
            insumoRepo.deleteAll(insumos);
        }

        if (producto instanceof SemiTerminado) {
            log.info("Eliminando Semiterminado con ID: {}", productoId);
            semiTerminadoRepo.deleteById(productoId);
        } else {
            log.info("Eliminando Terminado con ID: {}", productoId);
            terminadoRepo.deleteById(productoId);
        }

        result.put("success", true);
        result.put("message", "Producto eliminado correctamente");
        result.put("deletedMovimientos", deletedMovimientos);
        result.put("deletedOrdenesProduccion", deletedOrdenes);
        result.put("deletedInsumos", deletedInsumos);

        return result;
    }

    @Transactional
    public Producto saveManufacturingVersion(Producto productoData) {
        if (productoData == null || productoData.getProductoId() == null) {
            throw new IllegalArgumentException("El producto y su ID no pueden ser nulos");
        }

        Producto existingProducto = productoRepo.findById(productoData.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el producto con ID: " + productoData.getProductoId()));

        if (!(existingProducto instanceof SemiTerminado) && !(existingProducto instanceof Terminado)) {
            throw new IllegalArgumentException("Solo se pueden modificar productos Semiterminados o Terminados");
        }

        try {
            ManufacturingVersions version = new ManufacturingVersions();
            version.setProducto(existingProducto);

            int nextVersion = manufacturingVersionRepo.findTopByProductoOrderByVersionNumberDesc(existingProducto)
                    .map(v -> v.getVersionNumber() + 1)
                    .orElse(1);
            version.setVersionNumber(nextVersion);

            if (productoData instanceof Terminado terminadoData) {
                version.setInsumosJson(objectMapper.writeValueAsString(terminadoData.getInsumos()));
                version.setProcesoProduccionJson(objectMapper.writeValueAsString(terminadoData.getProcesoProduccionCompleto()));
                version.setCasePackJson(objectMapper.writeValueAsString(terminadoData.getCasePack()));

                Terminado existingTerminado = (Terminado) existingProducto;
                List<Insumo> updatedInsumos = Optional.ofNullable(terminadoData.getInsumos()).orElse(List.of())
                        .stream()
                        .map(insumo -> {
                            Producto inputProducto = insumo.getProducto();
                            if (inputProducto != null && inputProducto.getProductoId() != null) {
                                insumo.setProducto(productoRepo.findById(inputProducto.getProductoId())
                                        .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + inputProducto.getProductoId())));
                            }
                            return insumo;
                        })
                        .collect(Collectors.toList());

                List<Insumo> managedInsumos = existingTerminado.getInsumos();
                if (managedInsumos == null) {
                    managedInsumos = new ArrayList<>();
                    existingTerminado.setInsumos(managedInsumos);
                }
                managedInsumos.clear();
                managedInsumos.addAll(updatedInsumos);
                existingTerminado.setProcesoProduccionCompleto(terminadoData.getProcesoProduccionCompleto());
                existingTerminado.setCasePack(terminadoData.getCasePack());

                manufacturingVersionRepo.save(version);
                return productoRepo.save(existingTerminado);
            }

            SemiTerminado semiData = (SemiTerminado) productoData;
            version.setInsumosJson(objectMapper.writeValueAsString(semiData.getInsumos()));
            version.setProcesoProduccionJson(objectMapper.writeValueAsString(semiData.getProcesoProduccionCompleto()));
            version.setCasePackJson(null);

            SemiTerminado existingSemi = (SemiTerminado) existingProducto;
            List<Insumo> updatedInsumos = Optional.ofNullable(semiData.getInsumos()).orElse(List.of())
                    .stream()
                    .map(insumo -> {
                        Producto inputProducto = insumo.getProducto();
                        if (inputProducto != null && inputProducto.getProductoId() != null) {
                            insumo.setProducto(productoRepo.findById(inputProducto.getProductoId())
                                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + inputProducto.getProductoId())));
                        }
                        return insumo;
                    })
                    .collect(Collectors.toList());

            List<Insumo> managedInsumos = existingSemi.getInsumos();
            if (managedInsumos == null) {
                managedInsumos = new ArrayList<>();
                existingSemi.setInsumos(managedInsumos);
            }
            managedInsumos.clear();
            managedInsumos.addAll(updatedInsumos);
            existingSemi.setProcesoProduccionCompleto(semiData.getProcesoProduccionCompleto());

            manufacturingVersionRepo.save(version);
            return productoRepo.save(existingSemi);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando la información de manufactura: " + e.getMessage(), e);
        }
    }
}
