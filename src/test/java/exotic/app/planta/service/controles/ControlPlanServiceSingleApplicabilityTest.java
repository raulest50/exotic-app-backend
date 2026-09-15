package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.AplicabilidadWriteRequest;
import exotic.app.planta.model.controles.dto.ControlDTOs.CaracteristicaWriteRequest;
import exotic.app.planta.model.controles.dto.ControlDTOs.PlanWriteRequest;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlPlanServiceSingleApplicabilityTest {

    @Mock private PlanControlRepo planRepo;
    @Mock private VersionPlanControlRepo versionRepo;
    @Mock private MagnitudControlRepo magnitudRepo;
    @Mock private UnidadControlRepo unidadRepo;
    @Mock private ProductoRepo productoRepo;
    @Mock private CategoriaRepo categoriaRepo;
    @Mock private AreaProduccionRepo areaRepo;
    @Mock private ProcesoProduccionRepo procesoRepo;

    @InjectMocks private ControlPlanService service;

    @Test
    void rechazaCrearPlanSinAplicabilidad() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.crear(AmbitoControl.CALIDAD, mock(User.class), planRequest(List.of())));

        assertTrue(error.getMessage().contains("exactamente una"));
    }

    @Test
    void rechazaCrearPlanConVariasAplicabilidades() {
        AplicabilidadWriteRequest aplicabilidad = categoryApplicability(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.crear(AmbitoControl.PROCESO, mock(User.class),
                        planRequest(List.of(aplicabilidad, aplicabilidad))));

        assertTrue(error.getMessage().contains("exactamente una"));
    }

    @Test
    void rechazaPublicarVersionConVariasAplicabilidades() {
        PlanControl plan = new PlanControl();
        plan.setId(10L);
        plan.setAmbito(AmbitoControl.CALIDAD);
        VersionPlanControl version = new VersionPlanControl();
        version.setId(20L);
        version.setPlan(plan);
        version.setEstado(EstadoVersionPlanControl.BORRADOR);
        version.getAplicabilidades().add(new AplicabilidadPlanControl());
        version.getAplicabilidades().add(new AplicabilidadPlanControl());

        when(planRepo.findByIdAndAmbitoForUpdate(10L, AmbitoControl.CALIDAD)).thenReturn(Optional.of(plan));
        when(versionRepo.findByIdAndPlan_Ambito(20L, AmbitoControl.CALIDAD)).thenReturn(Optional.of(version));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.publicar(AmbitoControl.CALIDAD, mock(User.class), 10L, 20L));

        assertTrue(error.getMessage().contains("exactamente una"));
    }

    @Test
    void rechazaExclusionFueraDeLaCategoria() {
        Categoria selectedCategory = category(1, "Cremas");
        Terminado excluded = product("T-2", "Gel", category(2, "Geles"));
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(selectedCategory));
        when(productoRepo.findById("T-2")).thenReturn(Optional.of(excluded));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "crearAplicabilidad",
                        new VersionPlanControl(), AmbitoControl.CALIDAD,
                        categoryApplicability(List.of("T-2"))));

        assertTrue(error.getMessage().contains("no pertenece a la categoria"));
    }

    @Test
    void aceptaYDeduplicaExclusionesDeLaCategoria() {
        Categoria selectedCategory = category(1, "Cremas");
        Terminado excluded = product("T-1", "Crema uno", selectedCategory);
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(selectedCategory));
        when(productoRepo.findById("T-1")).thenReturn(Optional.of(excluded));

        AplicabilidadPlanControl result = ReflectionTestUtils.invokeMethod(service, "crearAplicabilidad",
                new VersionPlanControl(), AmbitoControl.CALIDAD,
                categoryApplicability(List.of("T-1", "T-1")));

        assertEquals(1, result.getProductosExcluidos().size());
        verify(productoRepo, times(1)).findById("T-1");
    }

    private PlanWriteRequest planRequest(List<AplicabilidadWriteRequest> aplicabilidades) {
        CaracteristicaWriteRequest characteristic = new CaracteristicaWriteRequest(
                "Aspecto", TipoCaracteristicaControl.BOOLEANA, 1L, null,
                1, 1, 1, 0, null, null, null, true);
        return new PlanWriteRequest(
                "PLAN-1", "Plan", null, null, aplicabilidades, List.of(characteristic));
    }

    private AplicabilidadWriteRequest categoryApplicability(List<String> exclusions) {
        return new AplicabilidadWriteRequest(
                null, 1, TipoOrdenControl.OP, PuntoAplicacionControl.LOTE_FINAL,
                null, null, null, MomentoControl.REVISION_FINAL,
                PuntoExigenciaControl.INFORMATIVO, false, exclusions);
    }

    private Categoria category(int id, String name) {
        Categoria category = new Categoria();
        category.setCategoriaId(id);
        category.setCategoriaNombre(name);
        return category;
    }

    private Terminado product(String id, String name, Categoria category) {
        Terminado product = new Terminado();
        product.setProductoId(id);
        product.setNombre(name);
        product.setCategoria(category);
        return product;
    }
}
