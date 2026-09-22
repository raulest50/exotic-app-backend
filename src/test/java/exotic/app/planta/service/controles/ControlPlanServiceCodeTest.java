package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.controles.dto.ControlDTOs.*;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlPlanServiceCodeTest {
    @Mock private PlanControlRepo planRepo;
    @Mock private VersionPlanControlRepo versionRepo;
    @Mock private MagnitudControlRepo magnitudRepo;
    @Mock private UnidadControlRepo unidadRepo;
    @Mock private ProductoRepo productoRepo;
    @Mock private CategoriaRepo categoriaRepo;
    @Mock private AreaProduccionRepo areaRepo;
    @Mock private ProcesoProduccionRepo procesoRepo;
    @InjectMocks private ControlPlanService service;

    @ParameterizedTest
    @ValueSource(strings = {"ensayo 01", " ENSAYO_01 ", "ensayo_01"})
    void consultaElCodigoNormalizadoSinLimitarPorModuloOVersion(String entrada) {
        when(planRepo.existsByCodigoIgnoreCase("ENSAYO_01")).thenReturn(true);
        var result = service.disponibilidadCodigo(entrada);
        assertEquals("ENSAYO_01", result.codigoNormalizado());
        assertFalse(result.disponible());
        verify(planRepo).existsByCodigoIgnoreCase("ENSAYO_01");
        verifyNoInteractions(versionRepo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "@@@"})
    void rechazaCodigosVaciosOInvalidosSinConsultar(String codigo) {
        assertThrows(IllegalArgumentException.class, () -> service.disponibilidadCodigo(codigo));
        verifyNoInteractions(planRepo);
    }

    @Test
    void rechazaCodigoDemasiadoLargo() {
        assertThrows(IllegalArgumentException.class, () -> service.disponibilidadCodigo("A".repeat(61)));
        verifyNoInteractions(planRepo);
    }

    @ParameterizedTest
    @EnumSource(AmbitoControl.class)
    void noCreaPlanSiElCodigoYaExiste(AmbitoControl ambito) {
        when(planRepo.existsByCodigoIgnoreCase("ENSAYO_01")).thenReturn(true);
        assertThrows(CodigoPlanDuplicadoException.class,
                () -> service.crear(ambito, mock(User.class), request()));
        verify(planRepo, never()).saveAndFlush(any());
        verifyNoInteractions(versionRepo);
    }

    @Test
    void colisionConcurrenteSeTraduceSinVolverAConsultarEnLaTransaccionFallida() {
        var failure = integrity("control_plan_codigo_key", "23505");
        when(planRepo.saveAndFlush(any())).thenThrow(failure);
        var error = assertThrows(CodigoPlanDuplicadoException.class,
                () -> service.crear(AmbitoControl.CALIDAD, mock(User.class), request()));
        assertSame(failure, error.getCause());
        verify(planRepo, times(1)).existsByCodigoIgnoreCase("ENSAYO_01");
        verify(planRepo).saveAndFlush(any());
        verifyNoMoreInteractions(planRepo);
        verifyNoInteractions(versionRepo, magnitudRepo, categoriaRepo);
    }

    @Test
    void otrasRestriccionesConservanSuErrorOriginal() {
        var failure = integrity("uq_control_plan_version_vigente", "23505");
        when(planRepo.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.crear(AmbitoControl.CALIDAD, mock(User.class), request())));
        assertFalse(CodigoPlanDuplicadoException.correspondeA(integrity("control_plan_codigo_key", "23502")));
    }

    @Test
    void unCodigoDisponiblePermiteCrearElBorradorConSuContenido() {
        assertTrue(service.disponibilidadCodigo("ensayo 01").disponible());
        var categoria = new Categoria();
        categoria.setCategoriaId(7);
        var magnitud = new MagnitudControl();
        magnitud.setId(1L);
        magnitud.setActivo(true);
        when(categoriaRepo.findById(7)).thenReturn(Optional.of(categoria));
        when(magnitudRepo.findById(1L)).thenReturn(Optional.of(magnitud));
        var saved = new AtomicReference<PlanControl>();
        when(planRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            PlanControl plan = invocation.getArgument(0);
            plan.setId(11L);
            saved.set(plan);
            return plan;
        });
        when(planRepo.findByIdAndAmbito(11L, AmbitoControl.CALIDAD))
                .thenAnswer(invocation -> Optional.of(saved.get()));

        var result = service.crear(AmbitoControl.CALIDAD, mock(User.class), request());

        assertEquals("ENSAYO_01", result.codigo());
        assertEquals(EstadoVersionPlanControl.BORRADOR, result.versiones().getFirst().estado());
        assertEquals(Boolean.FALSE, result.versiones().getFirst().caracteristicas().getFirst().valorBooleanoEsperado());
    }

    private DataIntegrityViolationException integrity(String constraint, String sqlState) {
        return new DataIntegrityViolationException("Integrity failure",
                new ConstraintViolationException("Constraint failure", new SQLException("Failure", sqlState), constraint));
    }

    private PlanWriteRequest request() {
        return new PlanWriteRequest("ensayo 01", "Inspección", null, null,
                List.of(new AplicabilidadWriteRequest(null, 7, TipoOrdenControl.OP, PuntoAplicacionControl.LOTE_FINAL,
                        null, null, null, null, null, false, List.of())),
                List.of(new CaracteristicaWriteRequest("Aspecto", TipoCaracteristicaControl.BOOLEANA,
                        1L, null, 1, 1, 1, 0, null, null, null, false)));
    }
}
