package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.costos.RecetaCostosRevision;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.costos.RecetaCostosRevisionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ProductoCostoPropagacionService {
    public static final String ALGORITMO_VERSION = "RECETA_COSTO_V1";
    private static final short REVISION_ID = 1;

    private final ProductoRepo productoRepo;
    private final InsumoRepo insumoRepo;
    private final RecetaCostosRevisionRepo revisionRepo;
    private final ProductoCostoService productoCostoService;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlanPropagacion calcularPlan(List<CambioCostoRaiz> cambios) {
        RecetaCostosRevision revision = revisionRepo.findById(REVISION_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el control de revision de recetas"));
        GrafoRecetas grafo = descubrirGrafo(cambios);
        Map<String, Producto> productos = cargarProductos(grafo.productoIds(), false);
        return construirPlan(cambios, grafo, productos, revision.getVersion());
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            noRollbackFor = PlanPropagacionModificadoException.class)
    public ResultadoPropagacion validarYAplicar(
            String algoritmoEsperado,
            String hashEsperado,
            List<CambioCostoRaiz> cambios,
            List<ItemPropagacionEsperado> itemsEsperados,
            ProductoCostoService.ContextoCambio contextoMaterial,
            ProductoCostoService.ContextoCambio contextoDependencia
    ) {
        RecetaCostosRevision revision = revisionRepo.findSingletonForUpdate()
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el control de revision de recetas"));

        GrafoRecetas grafo;
        Map<String, Producto> productos;
        PlanPropagacion actual;
        try {
            grafo = descubrirGrafo(cambios);
            productos = cargarProductos(grafo.productoIds(), true);
            actual = construirPlan(cambios, grafo, productos, revision.getVersion());
        } catch (ProductoCostoPropagacionException ex) {
            throw new PlanPropagacionModificadoException();
        }

        if (!ALGORITMO_VERSION.equals(algoritmoEsperado)
                || !Objects.equals(hashEsperado, actual.sha256())
                || !coincidenItems(itemsEsperados, actual.items())) {
            throw new PlanPropagacionModificadoException();
        }

        int materialesActualizados = 0;
        int materialesSinCambio = 0;
        List<CambioCostoRaiz> cambiosOrdenados = cambios.stream()
                .sorted(Comparator.comparing(CambioCostoRaiz::productoId))
                .toList();
        for (CambioCostoRaiz cambio : cambiosOrdenados) {
            Producto producto = productos.get(cambio.productoId());
            ProductoCostoService.ResultadoCambio resultado =
                    productoCostoService.actualizarProductoBloqueado(
                            producto,
                            cambio.costoVersionAnterior(),
                            cambio.costoAnterior(),
                            cambio.costoNuevo(),
                            contextoMaterial);
            if (resultado.actualizado()) {
                materialesActualizados++;
            } else {
                materialesSinCambio++;
            }
        }

        int dependenciasActualizadas = 0;
        int dependenciasSinCambio = 0;
        for (ItemPlan item : actual.items()) {
            Producto producto = productos.get(item.productoId());
            ProductoCostoService.ResultadoCambio resultado =
                    productoCostoService.actualizarProductoBloqueado(
                            producto,
                            item.costoVersionAnterior(),
                            item.costoAnterior(),
                            item.costoNuevo(),
                            contextoDependencia);
            if (resultado.actualizado()) {
                dependenciasActualizadas++;
            } else {
                dependenciasSinCambio++;
            }
        }

        return new ResultadoPropagacion(
                actual,
                materialesActualizados,
                materialesSinCambio,
                dependenciasActualizadas,
                dependenciasSinCambio);
    }

    private GrafoRecetas descubrirGrafo(List<CambioCostoRaiz> cambios) {
        List<CambioCostoRaiz> cambiosSeguros = cambios == null ? List.of() : cambios;
        Set<String> raices = new TreeSet<>();
        Set<String> raicesCambiadas = new TreeSet<>();
        for (CambioCostoRaiz cambio : cambiosSeguros) {
            if (cambio == null || cambio.productoId() == null || cambio.productoId().isBlank()) {
                throw new IllegalArgumentException("Cada cambio de costo debe tener productoId");
            }
            if (!raices.add(cambio.productoId())) {
                throw new IllegalArgumentException(
                        "El material esta repetido en el plan: " + cambio.productoId());
            }
            BigDecimal anterior = productoCostoService.normalizar(cambio.costoAnterior());
            BigDecimal nuevo = productoCostoService.normalizar(cambio.costoNuevo());
            if (anterior.compareTo(nuevo) != 0) {
                raicesCambiadas.add(cambio.productoId());
            }
        }

        Set<String> afectados = new LinkedHashSet<>();
        Set<String> consultados = new HashSet<>();
        ArrayDeque<String> pendientes = new ArrayDeque<>(raicesCambiadas);
        while (!pendientes.isEmpty()) {
            Set<String> frontera = new TreeSet<>();
            while (!pendientes.isEmpty()) {
                String productoId = pendientes.removeFirst();
                if (consultados.add(productoId)) {
                    frontera.add(productoId);
                }
            }
            if (frontera.isEmpty()) {
                continue;
            }
            for (InsumoRepo.CostoRecetaEdgeProjection edge
                    : insumoRepo.findCostoEdgesByInputProductoIds(frontera)) {
                String outputId = edge.getOutputProductoId();
                if (outputId != null && afectados.add(outputId)
                        && !consultados.contains(outputId)) {
                    pendientes.addLast(outputId);
                }
            }
        }

        List<AristaReceta> recetas = afectados.isEmpty()
                ? List.of()
                : insumoRepo.findCostoEdgesByOutputProductoIds(afectados).stream()
                        .map(this::toArista)
                        .toList();

        Set<String> productoIds = new TreeSet<>(raices);
        productoIds.addAll(afectados);
        for (AristaReceta arista : recetas) {
            if (arista.inputProductoId() != null) {
                productoIds.add(arista.inputProductoId());
            }
        }
        return new GrafoRecetas(
                Set.copyOf(raicesCambiadas),
                Set.copyOf(afectados),
                List.copyOf(recetas),
                Set.copyOf(productoIds));
    }

    private AristaReceta toArista(InsumoRepo.CostoRecetaEdgeProjection edge) {
        return new AristaReceta(
                edge.getInsumoId(),
                edge.getInputProductoId(),
                edge.getOutputProductoId(),
                edge.getCantidadRequerida());
    }

    private Map<String, Producto> cargarProductos(Collection<String> ids, boolean forUpdate) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Producto> productos = forUpdate
                ? productoRepo.findAllByProductoIdInForUpdate(ids)
                : productoRepo.findAllById(ids);
        Map<String, Producto> byId = new LinkedHashMap<>();
        for (Producto producto : productos) {
            byId.put(producto.getProductoId(), producto);
        }
        if (byId.size() != ids.size()) {
            Set<String> faltantes = new TreeSet<>(ids);
            faltantes.removeAll(byId.keySet());
            throw invalid(faltantes.stream().findFirst().orElse(null),
                    "La receta referencia productos inexistentes: " + String.join(", ", faltantes));
        }
        return byId;
    }

    private PlanPropagacion construirPlan(
            List<CambioCostoRaiz> cambios,
            GrafoRecetas grafo,
            Map<String, Producto> productos,
            long recetaRevision
    ) {
        Map<String, CambioCostoRaiz> cambiosById = new HashMap<>();
        for (CambioCostoRaiz cambio : cambios) {
            Producto producto = requireProducto(productos, cambio.productoId());
            if (!(producto instanceof Material)) {
                throw invalid(cambio.productoId(),
                        "Solo los materiales pueden ser raices de propagacion");
            }
            productoCostoService.normalizar(cambio.costoAnterior());
            productoCostoService.normalizar(cambio.costoNuevo());
            cambiosById.put(cambio.productoId(), cambio);
        }

        Map<String, List<AristaReceta>> recetaByOutput = new HashMap<>();
        Map<String, Set<String>> sucesores = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String afectado : grafo.afectados()) {
            Producto output = requireProducto(productos, afectado);
            String tipo = output.getTipo_producto();
            if (!"S".equals(tipo) && !"T".equals(tipo)) {
                throw invalid(afectado,
                        "Una receta de costos solo puede producir un Semiterminado o Terminado");
            }
            indegree.put(afectado, 0);
        }

        for (AristaReceta arista : grafo.recetas()) {
            String outputId = arista.outputProductoId();
            if (!grafo.afectados().contains(outputId)) {
                continue;
            }
            if (arista.inputProductoId() == null) {
                throw invalid(outputId, "La receta contiene un insumo sin producto");
            }
            requireProducto(productos, arista.inputProductoId());
            if (arista.cantidadRequerida() == null
                    || !Double.isFinite(arista.cantidadRequerida())
                    || arista.cantidadRequerida() <= 0d) {
                throw invalid(outputId,
                        "La receta contiene una cantidad requerida invalida");
            }
            recetaByOutput.computeIfAbsent(outputId, ignored -> new ArrayList<>())
                    .add(arista);
            if (grafo.afectados().contains(arista.inputProductoId())) {
                Set<String> outputs = sucesores.computeIfAbsent(
                        arista.inputProductoId(), ignored -> new TreeSet<>());
                if (outputs.add(outputId)) {
                    indegree.compute(outputId, (ignored, value) -> value == null ? 1 : value + 1);
                }
            }
        }

        for (String afectado : grafo.afectados()) {
            if (recetaByOutput.getOrDefault(afectado, List.of()).isEmpty()) {
                throw invalid(afectado, "El producto alcanzado no tiene lineas de receta");
            }
        }

        Map<String, BigDecimal> costosEfectivos = new HashMap<>();
        for (Map.Entry<String, Producto> entry : productos.entrySet()) {
            costosEfectivos.put(
                    entry.getKey(),
                    productoCostoService.normalizar(entry.getValue().getCosto()));
        }
        for (String raiz : grafo.raicesCambiadas()) {
            costosEfectivos.put(
                    raiz,
                    productoCostoService.normalizar(cambiosById.get(raiz).costoNuevo()));
        }

        PriorityQueue<String> disponibles = new PriorityQueue<>();
        indegree.forEach((productoId, degree) -> {
            if (degree == 0) {
                disponibles.add(productoId);
            }
        });

        Map<String, Integer> niveles = new HashMap<>();
        List<ItemPlan> items = new ArrayList<>();
        while (!disponibles.isEmpty()) {
            String outputId = disponibles.remove();
            Producto output = requireProducto(productos, outputId);
            BigDecimal costo = BigDecimal.ZERO;
            int nivel = 1;
            boolean alcanzadoDesdeRaiz = false;

            for (AristaReceta arista : recetaByOutput.get(outputId)) {
                String inputId = arista.inputProductoId();
                BigDecimal costoInput = costosEfectivos.get(inputId);
                if (costoInput == null) {
                    throw invalid(outputId,
                            "No fue posible resolver el costo del insumo " + inputId);
                }
                BigDecimal cantidad = BigDecimal.valueOf(arista.cantidadRequerida());
                costo = costo.add(costoInput.multiply(cantidad));

                if (grafo.raicesCambiadas().contains(inputId)) {
                    alcanzadoDesdeRaiz = true;
                }
                Integer nivelInput = niveles.get(inputId);
                if (nivelInput != null) {
                    alcanzadoDesdeRaiz = true;
                    nivel = Math.max(nivel, nivelInput + 1);
                }
            }

            if (!alcanzadoDesdeRaiz) {
                throw invalid(outputId,
                        "El producto no tiene una ruta valida desde un material modificado");
            }

            BigDecimal costoAnterior = productoCostoService.normalizar(output.getCosto());
            BigDecimal costoNuevo;
            try {
                costoNuevo = productoCostoService.normalizar(costo);
            } catch (IllegalArgumentException ex) {
                throw invalid(outputId, ex.getMessage());
            }
            costosEfectivos.put(outputId, costoNuevo);
            niveles.put(outputId, nivel);
            items.add(new ItemPlan(
                    outputId,
                    output.getNombre(),
                    output.getTipo_producto(),
                    nivel,
                    costoAnterior,
                    costoNuevo,
                    output.getCostoVersion()));

            for (String sucesor : sucesores.getOrDefault(outputId, Set.of())) {
                int nuevoIndegree = indegree.computeIfPresent(sucesor, (ignored, value) -> value - 1);
                if (nuevoIndegree == 0) {
                    disponibles.add(sucesor);
                }
            }
        }

        if (items.size() != grafo.afectados().size()) {
            Set<String> ciclo = new TreeSet<>(grafo.afectados());
            items.forEach(item -> ciclo.remove(item.productoId()));
            throw invalid(ciclo.stream().findFirst().orElse(null),
                    "Se detecto un ciclo en las recetas alcanzadas: " + String.join(", ", ciclo));
        }

        items.sort(Comparator.comparingInt(ItemPlan::nivel).thenComparing(ItemPlan::productoId));
        String sha256 = calcularHash(cambios, grafo, productos, items);
        return new PlanPropagacion(
                recetaRevision,
                ALGORITMO_VERSION,
                sha256,
                List.copyOf(items));
    }

    private boolean coincidenItems(
            List<ItemPropagacionEsperado> esperados,
            List<ItemPlan> actuales
    ) {
        if (esperados == null || esperados.size() != actuales.size()) {
            return false;
        }
        Map<String, ItemPropagacionEsperado> expectedById = new HashMap<>();
        for (ItemPropagacionEsperado item : esperados) {
            if (expectedById.put(item.productoId(), item) != null) {
                return false;
            }
        }
        for (ItemPlan actual : actuales) {
            ItemPropagacionEsperado esperado = expectedById.get(actual.productoId());
            if (esperado == null
                    || !Objects.equals(esperado.productoNombre(), actual.productoNombre())
                    || !Objects.equals(esperado.tipoProducto(), actual.tipoProducto())
                    || esperado.nivel() != actual.nivel()
                    || esperado.costoVersionAnterior() != actual.costoVersionAnterior()
                    || productoCostoService.normalizar(esperado.costoAnterior())
                            .compareTo(actual.costoAnterior()) != 0
                    || productoCostoService.normalizar(esperado.costoNuevo())
                            .compareTo(actual.costoNuevo()) != 0) {
                return false;
            }
        }
        return true;
    }

    private String calcularHash(
            List<CambioCostoRaiz> cambios,
            GrafoRecetas grafo,
            Map<String, Producto> productos,
            List<ItemPlan> items
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hashField(digest, ALGORITMO_VERSION);

            cambios.stream()
                    .sorted(Comparator.comparing(CambioCostoRaiz::productoId))
                    .forEach(cambio -> {
                        Producto actual = requireProducto(productos, cambio.productoId());
                        hashField(digest, "RAIZ");
                        hashField(digest, cambio.productoId());
                        hashField(digest, actual.getNombre());
                        hashField(digest, actual.getTipo_producto());
                        hashField(digest, Long.toString(actual.getCostoVersion()));
                        hashField(digest, canonicalCosto(actual.getCosto()));
                        hashField(digest, canonicalCosto(cambio.costoNuevo()));
                    });

            grafo.productoIds().stream().sorted().forEach(productoId -> {
                Producto producto = requireProducto(productos, productoId);
                hashField(digest, "PRODUCTO");
                hashField(digest, productoId);
                hashField(digest, producto.getNombre());
                hashField(digest, producto.getTipo_producto());
                hashField(digest, Long.toString(producto.getCostoVersion()));
                hashField(digest, canonicalCosto(producto.getCosto()));
            });

            grafo.recetas().stream()
                    .sorted(Comparator
                            .comparing(
                                    AristaReceta::outputProductoId,
                                    Comparator.nullsFirst(String::compareTo))
                            .thenComparing(
                                    AristaReceta::inputProductoId,
                                    Comparator.nullsFirst(String::compareTo))
                            .thenComparing(
                                    AristaReceta::insumoId,
                                    Comparator.nullsFirst(Integer::compareTo)))
                    .forEach(arista -> {
                        hashField(digest, "RECETA");
                        hashField(digest, arista.outputProductoId());
                        hashField(digest, arista.inputProductoId());
                        hashField(digest, arista.insumoId() == null
                                ? null
                                : arista.insumoId().toString());
                        hashField(digest, canonicalCantidad(arista.cantidadRequerida()));
                    });

            for (ItemPlan item : items) {
                hashField(digest, "RESULTADO");
                hashField(digest, item.productoId());
                hashField(digest, Integer.toString(item.nivel()));
                hashField(digest, canonicalCosto(item.costoAnterior()));
                hashField(digest, canonicalCosto(item.costoNuevo()));
                hashField(digest, Long.toString(item.costoVersionAnterior()));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no esta disponible", ex);
        }
    }

    private void hashField(MessageDigest digest, String value) {
        String safe = value == null ? "<null>" : value;
        digest.update(Integer.toString(safe.length()).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(safe.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ';');
    }

    private String canonicalCosto(BigDecimal value) {
        return productoCostoService.normalizar(value).toPlainString();
    }

    private String canonicalCantidad(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private Producto requireProducto(Map<String, Producto> productos, String productoId) {
        Producto producto = productos.get(productoId);
        if (producto == null) {
            throw invalid(productoId, "Producto no encontrado en el grafo de costos");
        }
        return producto;
    }

    private ProductoCostoPropagacionException invalid(String productoId, String message) {
        return new ProductoCostoPropagacionException(productoId, message);
    }

    public record CambioCostoRaiz(
            String productoId,
            BigDecimal costoAnterior,
            long costoVersionAnterior,
            BigDecimal costoNuevo
    ) {}

    public record ItemPlan(
            String productoId,
            String productoNombre,
            String tipoProducto,
            int nivel,
            BigDecimal costoAnterior,
            BigDecimal costoNuevo,
            long costoVersionAnterior
    ) {}

    public record ItemPropagacionEsperado(
            String productoId,
            String productoNombre,
            String tipoProducto,
            int nivel,
            BigDecimal costoAnterior,
            BigDecimal costoNuevo,
            long costoVersionAnterior
    ) {}

    public record PlanPropagacion(
            long recetaRevision,
            String algoritmoVersion,
            String sha256,
            List<ItemPlan> items
    ) {}

    public record ResultadoPropagacion(
            PlanPropagacion plan,
            int materialesActualizados,
            int materialesSinCambio,
            int dependenciasActualizadas,
            int dependenciasSinCambio
    ) {}

    private record AristaReceta(
            Integer insumoId,
            String inputProductoId,
            String outputProductoId,
            Double cantidadRequerida
    ) {}

    private record GrafoRecetas(
            Set<String> raicesCambiadas,
            Set<String> afectados,
            List<AristaReceta> recetas,
            Set<String> productoIds
    ) {}

    public static class PlanPropagacionModificadoException extends RuntimeException {
        public PlanPropagacionModificadoException() {
            super("La receta o alguno de sus costos relacionados cambio");
        }
    }
}
