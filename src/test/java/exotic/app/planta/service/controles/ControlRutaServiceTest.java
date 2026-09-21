package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.controles.AplicabilidadPlanControlRepo;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlRutaServiceTest {
    @Mock private AplicabilidadPlanControlRepo aplicabilidadRepo;
    @Mock private ProductoRepo productoRepo;
    @Mock private CategoriaRepo categoriaRepo;
    @InjectMocks private ControlRutaService service;

    @Test
    void productoExcluidoNoMuestraElControlDeSuCategoria() {
        Terminado producto = terminado();
        var excluido = regla(1L, AmbitoControl.CALIDAD, producto.getCategoria());
        excluido.getProductosExcluidos().add(producto);
        var proceso = regla(2L, AmbitoControl.PROCESO, producto.getCategoria());
        when(productoRepo.findById("T-1")).thenReturn(Optional.of(producto));
        when(aplicabilidadRepo.findParaRuta(EstadoVersionPlanControl.VIGENTE, TipoOrdenControl.OP,
                "T-1", 7, false)).thenReturn(List.of(excluido, proceso));

        var result = service.listar(null, "T-1");

        assertEquals(List.of(2L), result.stream().map(item -> item.planId()).toList());
        assertEquals(AmbitoControl.PROCESO, result.get(0).ambito());
    }

    @Test
    void categoriaConservaAlcancePorProductoYExclusiones() {
        Terminado producto = terminado();
        var categoria = regla(1L, AmbitoControl.CALIDAD, producto.getCategoria());
        categoria.getProductosExcluidos().add(producto);
        var particular = regla(2L, AmbitoControl.PROCESO, null);
        particular.setProducto(producto);
        when(categoriaRepo.existsById(7)).thenReturn(true);
        when(aplicabilidadRepo.findParaRuta(EstadoVersionPlanControl.VIGENTE, TipoOrdenControl.OP,
                null, 7, true)).thenReturn(List.of(categoria, particular));

        var result = service.listar(7, null);

        assertEquals(2, result.size());
        assertEquals(List.of("T-1"), result.get(0).productosExcluidosIds());
        assertEquals("T-1", result.get(1).productoId());
        assertNull(result.get(1).categoriaId());
    }

    @Test
    void semiterminadoConsultaOFPropiaSinInferirCategoriaDeUnaOP() {
        SemiTerminado producto = new SemiTerminado();
        producto.setProductoId("S-1");
        producto.setRequiereOrdenFabricacion(true);
        when(productoRepo.findById("S-1")).thenReturn(Optional.of(producto));
        when(aplicabilidadRepo.findParaRuta(EstadoVersionPlanControl.VIGENTE, TipoOrdenControl.OF,
                "S-1", null, false)).thenReturn(List.of());
        assertEquals(List.of(), service.listar(null, "S-1"));
        verify(aplicabilidadRepo).findParaRuta(EstadoVersionPlanControl.VIGENTE, TipoOrdenControl.OF,
                "S-1", null, false);
    }

    @Test
    void rechazaAmbitosAmbiguosOProductosSinRutaPropia() {
        assertThrows(IllegalArgumentException.class, () -> service.listar(null, null));
        assertThrows(IllegalArgumentException.class, () -> service.listar(7, "T-1"));
        SemiTerminado integrado = new SemiTerminado();
        integrado.setProductoId("S-1");
        when(productoRepo.findById("S-1")).thenReturn(Optional.of(integrado));
        assertThrows(IllegalArgumentException.class, () -> service.listar(null, "S-1"));
        verifyNoInteractions(aplicabilidadRepo);
    }

    private Terminado terminado() {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(7);
        Terminado producto = new Terminado();
        producto.setProductoId("T-1");
        producto.setNombre("Crema");
        producto.setCategoria(categoria);
        return producto;
    }

    private AplicabilidadPlanControl regla(Long id, AmbitoControl ambito, Categoria categoria) {
        PlanControl plan = new PlanControl();
        plan.setId(id);
        plan.setCodigo("PLAN-" + id);
        plan.setNombre("Control " + id);
        plan.setAmbito(ambito);
        VersionPlanControl version = new VersionPlanControl();
        version.setPlan(plan);
        version.setNumero(1);
        version.setEstado(EstadoVersionPlanControl.VIGENTE);
        AplicabilidadPlanControl item = new AplicabilidadPlanControl();
        item.setVersion(version);
        item.setCategoria(categoria);
        item.setTipoOrden(TipoOrdenControl.OP);
        item.setPuntoAplicacion(PuntoAplicacionControl.SALIDA_OPERACION);
        item.setFrontendNodeId("mix");
        return item;
    }
}
