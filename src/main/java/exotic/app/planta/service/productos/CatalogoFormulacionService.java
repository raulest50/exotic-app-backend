package exotic.app.planta.service.productos;

import exotic.app.planta.model.producto.dto.CatalogoFormulacionDTO.Formulacion;
import exotic.app.planta.model.producto.dto.CatalogoFormulacionDTO.InsumoNodo;
import exotic.app.planta.model.producto.dto.CatalogoFormulacionDTO.ProductoResumen;
import exotic.app.planta.repo.producto.InsumoRepo;
import exotic.app.planta.repo.producto.InsumoRepo.FormulacionInsumoProjection;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.ProductoRepo.CatalogoFormulacionProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CatalogoFormulacionService {
    private static final int MAX_NODOS = 2000;
    private static final int MAX_PROFUNDIDAD = 32;
    private final ProductoRepo productoRepo;
    private final InsumoRepo insumoRepo;

    public Page<ProductoResumen> buscar(String search, String tipo, int page, int size) {
        String termino = search == null ? "" : search.strip();
        String clasificacion = tipo == null ? "" : tipo.strip().toUpperCase(Locale.ROOT);
        if (termino.length() > 200 || !Set.of("", "T", "S").contains(clasificacion)
                || page < 0 || page > 100000 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetros de catálogo inválidos.");
        }
        return productoRepo.buscarCatalogoFormulacion(termino, clasificacion, PageRequest.of(page, size))
                .map(this::resumen);
    }

    public Formulacion detalle(String productoId) {
        var producto = productoRepo.findCatalogoFormulacionById(productoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el terminado o semiterminado."));
        var contexto = new Lectura();
        if (sinUnidad(producto.getTipoUnidades())) {
            contexto.advertencias.add("El producto no tiene unidad de inventario registrada.");
        }
        boolean historica = producto.getCantidadSalidaReceta() == null;
        if (historica) {
            contexto.advertencias.add("Receta histórica: cálculo basado en coeficientes registrados.");
        }
        var ruta = new LinkedHashSet<String>();
        ruta.add(productoId);
        List<InsumoNodo> insumos = leerReceta(productoId, ruta, 0, contexto);
        return new Formulacion(resumen(producto), insumos, List.copyOf(contexto.advertencias),
                historica, producto.getCantidadSalidaReceta());
    }

    private List<InsumoNodo> leerReceta(String productoId, Set<String> ruta, int profundidad, Lectura contexto) {
        var resultado = new ArrayList<InsumoNodo>();
        var filas = contexto.recetas.computeIfAbsent(productoId, insumoRepo::findFormulacionByProductoId);
        for (var fila : filas) {
            if (contexto.nodos >= MAX_NODOS) {
                contexto.advertencias.add("Desglose incompleto: se alcanzó el límite de 2000 componentes.");
                break;
            }
            contexto.nodos++;
            var observaciones = new ArrayList<String>();
            String hijo = fila.getProductoId();
            String tipo = fila.getTipoProducto();
            boolean fabricable = "S".equals(tipo) || "T".equals(tipo);
            Double coeficiente = fila.getCantidadRequerida();
            BigDecimal cantidad = coeficiente != null && Double.isFinite(coeficiente) && coeficiente > 0
                    ? BigDecimal.valueOf(coeficiente) : null;
            if (cantidad == null) observaciones.add("Coeficiente inválido: cantidad no calculable.");
            if (sinUnidad(fila.getTipoUnidades())) observaciones.add("Sin unidad de inventario registrada.");
            if (tipo == null || hijo == null) observaciones.add("El insumo no está vinculado a un producto válido.");

            List<InsumoNodo> hijos = List.of();
            if (fabricable && hijo != null) {
                if (ruta.contains(hijo)) {
                    observaciones.add("Ciclo detectado: no se puede completar esta rama de la receta.");
                } else if (profundidad + 1 >= MAX_PROFUNDIDAD) {
                    observaciones.add("Desglose incompleto: se alcanzó el límite de profundidad.");
                } else {
                    if (fila.getCantidadSalidaReceta() == null) {
                        observaciones.add("Receta histórica: desglose basado en coeficientes registrados.");
                    }
                    ruta.add(hijo);
                    hijos = leerReceta(hijo, ruta, profundidad + 1, contexto);
                    ruta.remove(hijo);
                    if (hijos.isEmpty()) observaciones.add("No hay componentes de receta registrados para este producto.");
                }
            }
            if (!observaciones.isEmpty()) {
                contexto.advertencias.add((hijo == null ? "Insumo " + fila.getInsumoId() : hijo)
                        + ": " + String.join(" ", observaciones));
            }
            resultado.add(new InsumoNodo(fila.getInsumoId(), hijo, fila.getNombre(), tipo,
                    fila.getTipoUnidades(), cantidad, "S".equals(tipo) && Boolean.TRUE.equals(fila.getRequiereOrdenFabricacion()),
                    hijos, observaciones.isEmpty() ? null : String.join(" ", observaciones)));
        }
        return List.copyOf(resultado);
    }

    private ProductoResumen resumen(CatalogoFormulacionProjection producto) {
        return new ProductoResumen(producto.getProductoId(), producto.getNombre(), producto.getTipoProducto(),
                producto.getTipoUnidades(), producto.getFotoUrl(), producto.getCategoriaNombre(),
                "T".equals(producto.getTipoProducto()) ? producto.getStatus() : null);
    }

    private boolean sinUnidad(String unidad) {
        return unidad == null || unidad.isBlank();
    }

    private static final class Lectura {
        private int nodos;
        private final Map<String, List<FormulacionInsumoProjection>> recetas = new HashMap<>();
        private final Set<String> advertencias = new LinkedHashSet<>();
    }
}
