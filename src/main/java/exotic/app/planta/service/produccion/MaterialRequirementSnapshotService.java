package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Construye la BOM dispensable congelada; nunca consulta recetas al dispensar. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialRequirementSnapshotService {

    private static final int SCALE = 6;

    private final ProductoRepo productoRepo;
    private final ManufacturingVersionRepo manufacturingVersionRepo;
    private final ObjectMapper objectMapper;

    public String construirJson(
            Producto productoResultado,
            ManufacturingVersions version,
            BigDecimal cantidadOrden
    ) {
        validarBase(productoResultado, version, cantidadOrden);
        Map<String, Requirement> requirements = new LinkedHashMap<>();
        expandirDispensables(version, cantidadOrden, requirements,
                new LinkedHashSet<>(Set.of(productoResultado.getProductoId())));
        if (productoResultado instanceof Terminado) {
            agregarEmpaque(version, cantidadOrden, requirements);
        }
        try {
            return objectMapper.writeValueAsString(requirements.values().stream()
                    .map(Requirement::toMap)
                    .toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudieron congelar los requerimientos de materiales.", exception);
        }
    }

    /** Cantidades de semiterminados reales que requieren una OF para abastecer la OP. */
    public Map<SemiTerminado, BigDecimal> calcularOrdenesFabricacion(
            ManufacturingVersions version,
            BigDecimal cantidadOrden
    ) {
        if (version == null || cantidadOrden == null || cantidadOrden.signum() <= 0) {
            throw new IllegalArgumentException("La version y cantidad de la OP son obligatorias.");
        }
        Map<String, SemiDemand> demandas = new LinkedHashMap<>();
        String rootId = version.getProducto() == null ? "<raiz>" : version.getProducto().getProductoId();
        expandirOrdenes(version, cantidadOrden, demandas, new LinkedHashSet<>(Set.of(rootId)));
        Map<SemiTerminado, BigDecimal> result = new LinkedHashMap<>();
        demandas.values().forEach(demanda -> result.put(demanda.semi(), demanda.cantidad()));
        return result;
    }

    public List<RequirementView> leer(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new IllegalStateException("El snapshot de materiales no es una lista valida.");
            }
            List<RequirementView> result = new ArrayList<>();
            for (JsonNode item : root) {
                result.add(new RequirementView(
                        textRequired(item, "productoId"),
                        item.path("productoNombre").asText(""),
                        item.path("tipoProducto").asText("MATERIAL"),
                        item.path("unidadMedida").asText(""),
                        item.path("inventareable").asBoolean(true),
                        item.path("consumoDirecto").asBoolean(false),
                        item.path("cantidad").decimalValue()
                ));
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo leer el snapshot de materiales.", exception);
        }
    }

    public boolean requiereRegistroDispensacion(String json) {
        return leer(json).stream().anyMatch(requirement ->
                requirement.inventareable() || requirement.consumoDirecto());
    }

    private void expandirDispensables(
            ManufacturingVersions version,
            BigDecimal multiplicador,
            Map<String, Requirement> requirements,
            Set<String> ruta
    ) {
        for (Ingredient ingredient : leerIngredientes(version)) {
            Producto producto = requireProducto(ingredient.productoId());
            BigDecimal cantidad = multiplicador.multiply(ingredient.cantidad()).setScale(SCALE, RoundingMode.HALF_UP);
            if (producto instanceof SemiTerminado semi && !semi.isRequiereOrdenFabricacion()) {
                entrarRuta(ruta, semi.getProductoId());
                expandirDispensables(requireLatestVersion(semi), cantidad, requirements, ruta);
                ruta.remove(semi.getProductoId());
                continue;
            }
            if (producto instanceof Terminado) {
                throw new IllegalStateException(
                        "Una receta de manufactura no puede consumir un producto terminado: "
                                + producto.getProductoId());
            }
            addRequirement(requirements, producto, cantidad,
                    producto instanceof Material ? "MATERIAL" : "SEMITERMINADO");
        }
    }

    private void expandirOrdenes(
            ManufacturingVersions version,
            BigDecimal multiplicador,
            Map<String, SemiDemand> demandas,
            Set<String> ruta
    ) {
        for (Ingredient ingredient : leerIngredientes(version)) {
            Producto producto = requireProducto(ingredient.productoId());
            if (!(producto instanceof SemiTerminado semi)) continue;
            BigDecimal cantidad = multiplicador.multiply(ingredient.cantidad()).setScale(SCALE, RoundingMode.HALF_UP);
            entrarRuta(ruta, semi.getProductoId());
            if (semi.isRequiereOrdenFabricacion()) {
                demandas.compute(semi.getProductoId(), (ignored, actual) -> actual == null
                        ? new SemiDemand(semi, cantidad)
                        : new SemiDemand(semi, actual.cantidad().add(cantidad)));
            }
            expandirOrdenes(requireLatestVersion(semi), cantidad, demandas, ruta);
            ruta.remove(semi.getProductoId());
        }
    }

    private void agregarEmpaque(
            ManufacturingVersions version,
            BigDecimal cantidadOrden,
            Map<String, Requirement> requirements
    ) {
        String json = version.getCasePackJson();
        if (json == null || json.isBlank() || "null".equals(json)) return;
        try {
            JsonNode casePack = objectMapper.readTree(json);
            JsonNode items = casePack.path("insumosEmpaque");
            if (!items.isArray()) return;
            BigDecimal unitsPerCase = decimal(casePack.get("unitsPerCase"));
            for (JsonNode item : items) {
                Producto producto = requireProducto(textRequired(item, "materialId"));
                BigDecimal configured = decimalRequired(item.get("cantidad"),
                        "La cantidad del material de empaque es invalida.");
                BigDecimal total = unitsPerCase != null && unitsPerCase.signum() > 0
                        ? cantidadOrden.divide(unitsPerCase, SCALE, RoundingMode.HALF_UP).multiply(configured)
                        : cantidadOrden.multiply(configured);
                String unidadConfigurada = item.path("uom").asText(null);
                addRequirement(
                        requirements,
                        producto,
                        total,
                        "MATERIAL_EMPAQUE",
                        unidadConfigurada == null || unidadConfigurada.isBlank()
                                ? unidadObligatoria(producto)
                                : unidadConfigurada.trim());
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo interpretar el empaque congelado.", exception);
        }
    }

    private List<Ingredient> leerIngredientes(ManufacturingVersions version) {
        String json = version.getInsumosJson();
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                throw new IllegalStateException("La receta congelada no tiene formato de lista.");
            }
            List<Ingredient> result = new ArrayList<>();
            for (JsonNode item : root) {
                result.add(new Ingredient(
                        textRequired(item, "productoId"),
                        decimalRequired(item.get("cantidadRequerida"),
                                "La cantidad requerida de la receta es invalida.")));
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo interpretar la receta congelada.", exception);
        }
    }

    private void addRequirement(
            Map<String, Requirement> requirements,
            Producto producto,
            BigDecimal cantidad,
            String tipo
    ) {
        addRequirement(requirements, producto, cantidad, tipo, unidadObligatoria(producto));
    }

    private void addRequirement(
            Map<String, Requirement> requirements,
            Producto producto,
            BigDecimal cantidad,
            String tipo,
            String unidadMedida
    ) {
        if (cantidad.signum() <= 0) return;
        boolean consumoDirecto = producto instanceof Material material && material.isConsumoDirecto();
        Requirement incoming = new Requirement(
                producto.getProductoId(), producto.getNombre(), tipo,
                unidadMedida, producto.isInventareable(), consumoDirecto,
                cantidad.setScale(SCALE, RoundingMode.HALF_UP));
        requirements.merge(producto.getProductoId(), incoming, Requirement::merge);
    }

    private Producto requireProducto(String productoId) {
        return productoRepo.findById(productoId)
                .orElseThrow(() -> new IllegalStateException(
                        "La receta congelada referencia un producto inexistente: " + productoId));
    }

    private String unidadObligatoria(Producto producto) {
        String unidad = producto.getTipoUnidades();
        if (unidad == null || unidad.isBlank()) {
            throw new IllegalStateException(
                    "El producto " + producto.getProductoId() + " no tiene unidad de medida.");
        }
        return unidad.trim();
    }

    private ManufacturingVersions requireLatestVersion(SemiTerminado semi) {
        return manufacturingVersionRepo.findTopByProductoOrderByVersionNumberDesc(semi)
                .orElseThrow(() -> new IllegalStateException(
                        "El semiterminado " + semi.getProductoId()
                                + " no tiene version de manufactura para explotar su receta."));
    }

    private void entrarRuta(Set<String> ruta, String productoId) {
        if (!ruta.add(productoId)) {
            throw new IllegalStateException(
                    "Se detecto un ciclo en la receta de manufactura: "
                            + String.join(" -> ", ruta) + " -> " + productoId);
        }
    }

    private void validarBase(Producto producto, ManufacturingVersions version, BigDecimal cantidad) {
        if (producto == null || version == null || cantidad == null || cantidad.signum() <= 0
                || version.getProducto() == null
                || !producto.getProductoId().equals(version.getProducto().getProductoId())) {
            throw new IllegalArgumentException(
                    "Producto, version correspondiente y cantidad positiva son obligatorios.");
        }
    }

    private String textRequired(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("La receta congelada no contiene " + field + ".");
        }
        return value.trim();
    }

    private BigDecimal decimalRequired(JsonNode node, String message) {
        BigDecimal value = decimal(node);
        if (value == null || value.signum() <= 0) throw new IllegalStateException(message);
        return value;
    }

    private BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() || !node.isNumber() ? null : node.decimalValue();
    }

    public record RequirementView(
            String productoId,
            String productoNombre,
            String tipoProducto,
            String unidadMedida,
            boolean inventareable,
            boolean consumoDirecto,
            BigDecimal cantidad
    ) {}

    private record Ingredient(String productoId, BigDecimal cantidad) {}
    private record SemiDemand(SemiTerminado semi, BigDecimal cantidad) {}

    private record Requirement(
            String productoId,
            String productoNombre,
            String tipoProducto,
            String unidadMedida,
            boolean inventareable,
            boolean consumoDirecto,
            BigDecimal cantidad
    ) {
        Requirement merge(Requirement other) {
            if (!unidadMedida.equalsIgnoreCase(other.unidadMedida)) {
                throw new IllegalStateException(
                        "El producto " + productoId + " aparece con unidades incompatibles.");
            }
            return new Requirement(productoId, productoNombre, tipoProducto, unidadMedida,
                    inventareable && other.inventareable,
                    consumoDirecto || other.consumoDirecto,
                    cantidad.add(other.cantidad));
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("productoId", productoId);
            map.put("productoNombre", productoNombre);
            map.put("tipoProducto", tipoProducto);
            map.put("unidadMedida", unidadMedida);
            map.put("inventareable", inventareable);
            map.put("consumoDirecto", consumoDirecto);
            map.put("cantidad", cantidad);
            return map;
        }
    }
}
