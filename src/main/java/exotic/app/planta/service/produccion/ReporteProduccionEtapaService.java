package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.EstadoSeguimientoOrdenArea;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.ReporteProduccionEtapaDTOs.*;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion;
import exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaEventoRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionEventoRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** Mediciones informativas por etapa: congela su contexto y nunca mueve inventario. */
@Service
@RequiredArgsConstructor
public class ReporteProduccionEtapaService {
    private static final Configuracion NO_REQUERIDO = new Configuracion(false, List.of());
    private static final BigDecimal MAX_CANTIDAD = new BigDecimal("99999999999999.9999");
    private final ProductoRepo productoRepo;
    private final ManufacturingVersionRepo versionRepo;
    private final SeguimientoOrdenAreaEventoRepo eventoOpRepo;
    private final OrdenFabricacionOperacionEventoRepo eventoOfRepo;
    private final ObjectMapper objectMapper;

    public Map<Long, Configuracion> congelarOp(OrdenProduccion orden, RutaProcesoCatVersion ruta) {
        Set<Integer> areas = new LinkedHashSet<>();
        ruta.getNodes().stream().map(n -> n.getAreaOperativa()).filter(Objects::nonNull)
                .filter(a -> a.getAreaId() != -1 && a.getAlcanceEjecucion() != AreaOperativa.AlcanceEjecucion.SOLO_OF
                        && a.isRequiereReporteProduccionEtapa()).forEach(a -> areas.add(a.getAreaId()));
        List<Asignacion> asignaciones = areas.isEmpty() ? List.of()
                : inferir(orden.getProducto(), orden.getManufacturingVersion());
        Map<Long, Configuracion> result = new LinkedHashMap<>();
        for (var nodo : ruta.getNodes()) {
            AreaOperativa area = nodo.getAreaOperativa();
            result.put(nodo.getId(), area != null && areas.contains(area.getAreaId())
                    ? configuracion(area, asignaciones.stream().filter(a -> a.areaId() == area.getAreaId())
                    .filter(a -> nodo.getProcesoProduccion() == null
                            || Objects.equals(nodo.getProcesoProduccion().getProcesoId(), a.procesoId())).toList())
                    : NO_REQUERIDO);
        }
        return result;
    }

    public Map<String, Configuracion> congelarOf(OrdenFabricacion orden) {
        // Solo los nodos del grafo propio son operaciones de esta OF.
        List<Asignacion> propias = asignaciones(orden.getSemiTerminado(), orden.getManufacturingVersion(),
                orden.getSemiTerminado().getProductoId());
        Map<String, Configuracion> result = new LinkedHashMap<>();
        for (Asignacion a : propias) result.put(a.frontendId(),
                new Configuracion(true, List.of(a.producto())));
        return result;
    }

    /** La configuración del área se congela antes del primer INSERT de la operación. */
    public Configuracion paraOperacionOf(AreaOperativa area, String frontendId,
                                         Map<String, Configuracion> previstas) {
        if (!area.isRequiereReporteProduccionEtapa()) return NO_REQUERIDO;
        Configuracion config = previstas.get(frontendId);
        if (config == null || config.productos().isEmpty()) throw configuracionIncompleta(area);
        return config;
    }

    private Configuracion configuracion(AreaOperativa area, List<Asignacion> asignaciones) {
        Map<String, Producto> productos = new LinkedHashMap<>();
        for (Asignacion a : asignaciones) {
            Producto previo = productos.get(a.producto().productoId());
            if (previo == null) productos.put(a.producto().productoId(), a.producto());
            else {
                Set<String> nodos = new LinkedHashSet<>(previo.nodosOrigen());
                nodos.addAll(a.producto().nodosOrigen());
                productos.put(previo.productoId(), new Producto(previo.productoId(), previo.productoNombre(),
                        previo.unidadMedida(), previo.manufacturingVersionId(), List.copyOf(nodos), previo.productoFinalOrden()));
            }
        }
        if (productos.isEmpty()) throw configuracionIncompleta(area);
        return new Configuracion(true, List.copyOf(productos.values()));
    }

    private IllegalStateException configuracionIncompleta(AreaOperativa area) {
        return new IllegalStateException("El area " + area.getNombre()
                + " exige reporte de produccion, pero los grafos de esta orden no identifican su producto y unidad. "
                + "Revise las areas del Process Designer antes de emitir; no se asignara un semiterminado arbitrario.");
    }

    private List<Asignacion> inferir(exotic.app.planta.model.producto.Producto principal, ManufacturingVersions version) {
        List<Asignacion> result = new ArrayList<>();
        recorrer(principal, version, principal.getProductoId(), new LinkedHashSet<>(), new HashMap<>(), result);
        return result;
    }

    private void recorrer(exotic.app.planta.model.producto.Producto producto, ManufacturingVersions version, String principalId,
                          Set<String> ruta, Map<String, ManufacturingVersions> versiones, List<Asignacion> result) {
        if (ruta.size() >= 50 || !ruta.add(producto.getProductoId()))
            throw new IllegalStateException("La receta de reportes contiene un ciclo o supera 50 niveles: " + producto.getProductoId());
        result.addAll(asignaciones(producto, version, principalId));
        JsonNode insumos = leer(version.getInsumosJson(), "la receta de " + producto.getProductoId());
        if (!insumos.isArray()) throw new IllegalStateException("La receta de reportes no tiene formato de lista.");
        for (JsonNode item : insumos) {
            String id = item.path("productoId").asText("");
            exotic.app.planta.model.producto.Producto input = (exotic.app.planta.model.producto.Producto) Hibernate.unproxy(productoRepo.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Insumo inexistente al inferir reportes: " + id)));
            if (input instanceof SemiTerminado semi && !semi.isRequiereOrdenFabricacion()) {
                ManufacturingVersions child = versiones.computeIfAbsent(id, ignored -> versionRepo
                        .findTopByProductoOrderByVersionNumberDesc(semi).orElseThrow(() -> new IllegalStateException(
                                "El semiterminado " + id + " no tiene una version de manufactura para identificar su reporte.")));
                recorrer(semi, child, principalId, ruta, versiones, result);
            }
            // Una OF independiente se fabrica y reporta en su propia ejecución.
        }
        ruta.remove(producto.getProductoId());
    }

    private List<Asignacion> asignaciones(exotic.app.planta.model.producto.Producto producto, ManufacturingVersions version, String principalId) {
        if (version == null || version.getProducto() == null
                || !producto.getProductoId().equals(version.getProducto().getProductoId()))
            throw new IllegalStateException("La orden no tiene una version de manufactura valida para congelar sus reportes.");
        if (producto.getTipoUnidades() == null || producto.getTipoUnidades().isBlank())
            throw new IllegalStateException("El producto " + producto.getProductoId() + " no tiene unidad para su reporte.");
        JsonNode graph = grafo(version);
        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        Map<String, Set<String>> predecessors = new HashMap<>();
        for (JsonNode n : graph.path("nodes")) {
            String id = n.path("frontendId").asText("");
            if (id.isBlank() || nodes.putIfAbsent(id, n) != null)
                throw new IllegalStateException("El grafo contiene identificadores de nodo ausentes o repetidos.");
        }
        for (JsonNode edge : graph.path("edges")) {
            String source = edge.path("sourceFrontendId").asText("");
            String target = edge.path("targetFrontendId").asText("");
            if (!nodes.containsKey(source) || !nodes.containsKey(target))
                throw new IllegalStateException("El grafo del reporte contiene una conexion sin nodos validos.");
            predecessors.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
        }
        validarSinCiclos(nodes.keySet(), predecessors);
        Set<String> haciaProducto = new LinkedHashSet<>();
        Deque<String> pendientes = new ArrayDeque<>();
        nodes.forEach((id, node) -> { if ("TARGET".equals(node.path("nodeType").asText())) pendientes.add(id); });
        if (pendientes.size() != 1) throw new IllegalStateException("El grafo de " + producto.getProductoId()
                + " debe identificar un unico producto destino para inferir reportes.");
        while (!pendientes.isEmpty()) {
            String id = pendientes.removeFirst();
            if (haciaProducto.add(id)) pendientes.addAll(predecessors.getOrDefault(id, Set.of()));
        }
        List<Asignacion> result = new ArrayList<>();
        for (var entry : nodes.entrySet()) {
            JsonNode node = entry.getValue();
            if (!esProceso(node) || !haciaProducto.contains(entry.getKey())) continue;
            int area = node.path("areaOperativaId").asInt(-1);
            if (area <= 0) throw new IllegalStateException("Un proceso de " + producto.getProductoId()
                    + " no tiene area operativa para identificar su reporte.");
            Producto esperado = new Producto(producto.getProductoId(), producto.getNombre(),
                    producto.getTipoUnidades().trim(), version.getId(), List.of(entry.getKey()),
                    producto.getProductoId().equals(principalId));
            result.add(new Asignacion(area, node.path("procesoId").isNumber()
                    ? node.path("procesoId").intValue() : null, entry.getKey(), esperado));
        }
        return result;
    }

    private JsonNode grafo(ManufacturingVersions version) {
        if (version == null) throw new IllegalStateException("La orden necesita una version de manufactura para el reporte por etapa.");
        JsonNode graph = leer(version.getProcesoProduccionJson(), "el proceso de manufactura");
        if (!graph.path("nodes").isArray() || !graph.path("edges").isArray())
            throw new IllegalStateException("El proceso no contiene nodos y conexiones para inferir reportes.");
        return graph;
    }

    private JsonNode leer(String json, String contexto) {
        try {
            if (json == null || json.isBlank()) throw new IllegalArgumentException("JSON ausente");
            return objectMapper.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo interpretar " + contexto + " para el reporte de produccion.", error);
        }
    }

    private boolean esProceso(JsonNode node) { return "PROCESO".equals(node.path("nodeType").asText()); }

    private void validarSinCiclos(Set<String> nodos, Map<String, Set<String>> predecesores) {
        Map<String, Integer> pendientes = new HashMap<>();
        Map<String, Set<String>> siguientes = new HashMap<>();
        nodos.forEach(id -> pendientes.put(id, predecesores.getOrDefault(id, Set.of()).size()));
        predecesores.forEach((target, sources) -> sources.forEach(source ->
                siguientes.computeIfAbsent(source, ignored -> new HashSet<>()).add(target)));
        Deque<String> raices = new ArrayDeque<>();
        pendientes.forEach((id, cantidad) -> { if (cantidad == 0) raices.add(id); });
        int visitados = 0;
        while (!raices.isEmpty()) {
            String id = raices.removeFirst();
            visitados++;
            for (String siguiente : siguientes.getOrDefault(id, Set.of()))
                if (pendientes.compute(siguiente, (key, cantidad) -> cantidad - 1) == 0) raices.add(siguiente);
        }
        if (visitados != nodos.size()) throw new IllegalStateException("El grafo del reporte contiene un ciclo.");
    }

    /** Valida la captura contra el snapshot, sin aceptar nombres o unidades del cliente. */
    public List<Reporte> validar(Configuracion config, List<Solicitud> solicitudes, BigDecimal cantidadFinal) {
        List<Solicitud> entradas = solicitudes == null ? List.of() : solicitudes;
        if (config == null || !config.requerido()) {
            if (!entradas.isEmpty()) throw new IllegalArgumentException("Esta etapa no tiene habilitado el reporte de produccion.");
            return null;
        }
        if (config.productos().isEmpty()) throw new IllegalStateException("El reporte de esta etapa no tiene productos congelados.");
        if (entradas.size() > 100) throw new IllegalArgumentException("El reporte no admite mas de 100 productos.");
        Map<String, Solicitud> porProducto = new LinkedHashMap<>();
        Set<String> permitidos = new HashSet<>();
        config.productos().forEach(p -> permitidos.add(p.productoId()));
        for (Solicitud entrada : entradas) {
            if (entrada == null || !permitidos.contains(entrada.productoId())
                    || porProducto.putIfAbsent(entrada.productoId(), entrada) != null)
                throw new IllegalArgumentException("El reporte contiene productos ajenos a la etapa o repetidos.");
        }
        List<Reporte> result = new ArrayList<>();
        for (Producto producto : config.productos()) {
            Solicitud entrada = porProducto.get(producto.productoId());
            BigDecimal cantidad = producto.productoFinalOrden() && cantidadFinal != null
                    ? cantidadFinal : entrada == null ? null : entrada.cantidad();
            if (producto.productoFinalOrden() && cantidadFinal != null && entrada != null
                    && (entrada.cantidad() == null || entrada.cantidad().compareTo(cantidadFinal) != 0))
                throw new IllegalArgumentException("La cantidad del producto final debe coincidir con el cierre de la orden.");
            if (cantidad == null || cantidad.signum() <= 0 || cantidad.compareTo(MAX_CANTIDAD) > 0
                    || cantidad.stripTrailingZeros().scale() > 4)
                throw new IllegalArgumentException("Reporte una cantidad positiva, de hasta cuatro decimales, para "
                        + producto.productoNombre() + " (" + producto.unidadMedida() + ").");
            result.add(new Reporte(producto.productoId(), producto.productoNombre(), producto.unidadMedida(), cantidad, null));
        }
        return List.copyOf(result);
    }

    public List<Registro> historial(SeguimientoOrdenArea etapa) {
        var eventos = eventoOpRepo.findBySeguimientoOrdenArea_IdOrderByFechaEventoAscIdAsc(etapa.getId());
        Set<Long> revertidos = new HashSet<>();
        eventos.forEach(e -> { if (e.getEventoRevertido() != null) revertidos.add(e.getEventoRevertido().getId()); });
        return vigencia(eventos.stream().filter(e -> e.getReportesProduccion() != null)
                .map(e -> new Registro(e.getId(), e.getFechaEvento(), nombre(e.getUsuario()),
                        !revertidos.contains(e.getId()), e.getReportesProduccion())).toList(),
                etapa.getEstadoEnum() == EstadoSeguimientoOrdenArea.COMPLETADO);
    }

    public List<Registro> historial(OrdenFabricacionOperacion etapa) {
        var eventos = eventoOfRepo.findByOperacion_IdOrderByFechaEventoAscIdAsc(etapa.getId());
        Set<Long> revertidos = new HashSet<>();
        eventos.forEach(e -> { if (e.getEventoRevertido() != null) revertidos.add(e.getEventoRevertido().getId()); });
        return vigencia(eventos.stream().filter(e -> e.getReportesProduccion() != null)
                .map(e -> new Registro(e.getId(), e.getFechaEvento(), nombre(e.getUsuario()),
                        !revertidos.contains(e.getId()), e.getReportesProduccion())).toList(),
                etapa.getEstadoEnum() == EstadoSeguimientoOrdenArea.COMPLETADO);
    }

    private List<Registro> vigencia(List<Registro> registros, boolean completada) {
        Long vigente = completada ? registros.stream().filter(Registro::vigente).reduce((a, b) -> b)
                .map(Registro::eventoId).orElse(null) : null;
        return registros.stream().map(r -> new Registro(r.eventoId(), r.fechaEvento(), r.usuario(),
                Objects.equals(vigente, r.eventoId()), r.reportesProduccion())).toList();
    }

    public List<Reporte> vigentes(List<Registro> historial) {
        return historial.stream().filter(Registro::vigente).findFirst().map(Registro::reportesProduccion).orElse(List.of());
    }

    private String nombre(User usuario) {
        return usuario == null ? null : usuario.getNombreCompleto() == null || usuario.getNombreCompleto().isBlank()
                ? usuario.getUsername() : usuario.getNombreCompleto();
    }

    private record Asignacion(int areaId, Integer procesoId, String frontendId, Producto producto) {}
}
