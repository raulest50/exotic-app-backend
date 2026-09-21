package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.AplicabilidadPlanControl;
import exotic.app.planta.model.controles.EstadoVersionPlanControl;
import exotic.app.planta.model.controles.TipoOrdenControl;
import exotic.app.planta.model.controles.dto.ControlRutaResumen;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.controles.AplicabilidadPlanControlRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ControlRutaService {
    private final AplicabilidadPlanControlRepo aplicabilidadRepo;
    private final ProductoRepo productoRepo;
    private final CategoriaRepo categoriaRepo;

    public List<ControlRutaResumen> listar(Integer categoriaId, String productoId) {
        String productoBuscado = productoId == null || productoId.isBlank() ? null : productoId.trim();
        if ((categoriaId == null) == (productoBuscado == null)) {
            throw new IllegalArgumentException("Indique una categoría o un producto, exclusivamente.");
        }
        boolean categoriaCompleta = productoBuscado == null;
        TipoOrdenControl tipoOrden = TipoOrdenControl.OP;
        if (categoriaCompleta) {
            if (!categoriaRepo.existsById(categoriaId)) {
                throw new NoSuchElementException("Categoría no encontrada.");
            }
        } else {
            Producto producto = (Producto) Hibernate.unproxy(productoRepo.findById(productoBuscado)
                    .orElseThrow(() -> new NoSuchElementException("Producto no encontrado.")));
            if (producto instanceof Terminado terminado) {
                categoriaId = terminado.getCategoria() == null ? null : terminado.getCategoria().getCategoriaId();
            } else if (producto instanceof SemiTerminado semi && semi.isRequiereOrdenFabricacion()) {
                tipoOrden = TipoOrdenControl.OF;
            } else {
                throw new IllegalArgumentException("El producto no tiene una ruta de producción u OF propia.");
            }
        }
        return aplicabilidadRepo.findParaRuta(EstadoVersionPlanControl.VIGENTE, tipoOrden,
                        productoBuscado, categoriaId, categoriaCompleta).stream().distinct()
                // En la vista de categoría se conservan las exclusiones como información de alcance.
                .filter(item -> productoBuscado == null || item.getProductosExcluidos().stream()
                        .noneMatch(excluido -> productoBuscado.equals(excluido.getProductoId())))
                .map(this::toResumen)
                .toList();
    }

    private ControlRutaResumen toResumen(AplicabilidadPlanControl item) {
        var version = item.getVersion();
        var plan = version.getPlan();
        return new ControlRutaResumen(plan.getId(), plan.getCodigo(), plan.getNombre(), plan.getAmbito(),
                version.getNumero(), item.getPuntoAplicacion(), item.getFrontendNodeId(),
                item.getAreaOperativa() == null ? null : item.getAreaOperativa().getAreaId(),
                item.getProceso() == null ? null : item.getProceso().getProcesoId(),
                item.getProducto() == null ? null : item.getProducto().getProductoId(),
                item.getProducto() == null ? null : item.getProducto().getNombre(),
                item.getCategoria() == null ? null : item.getCategoria().getCategoriaId(),
                item.isLegadoGlobal(), item.getProductosExcluidos().stream()
                        .map(Producto::getProductoId).sorted().toList());
    }
}
