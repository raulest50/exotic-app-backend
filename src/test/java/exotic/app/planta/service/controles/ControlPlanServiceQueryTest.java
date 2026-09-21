package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.*;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.repo.producto.CategoriaRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.ProcesoProduccionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlPlanServiceQueryTest {
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
    @EnumSource(AmbitoControl.class)
    void paginaPlanesYConservaReferenciaAlBorradorOculto(AmbitoControl ambito) {
        var pageable = PageRequest.of(1, 1);
        var estados = List.of(EstadoVersionPlanControl.VIGENTE);
        var plan = mock(PlanControlRepo.ResumenPlan.class);
        when(plan.getId()).thenReturn(7L);
        when(plan.getCodigo()).thenReturn("PLAN-7");
        when(plan.getNombre()).thenReturn("Peso");
        when(plan.getAmbito()).thenReturn(ambito);
        when(planRepo.findResumenes(ambito, "peso", estados, pageable))
                .thenReturn(new PageImpl<>(List.of(plan), pageable, 3));

        var vigente = mock(VersionPlanControlRepo.ResumenVersion.class);
        when(vigente.getPlanId()).thenReturn(7L);
        when(vigente.getId()).thenReturn(71L);
        when(vigente.getNumero()).thenReturn(1);
        when(vigente.getEstado()).thenReturn(EstadoVersionPlanControl.VIGENTE);
        when(vigente.getCantidadAplicabilidades()).thenReturn(1);
        when(vigente.getCantidadCaracteristicas()).thenReturn(4);
        when(versionRepo.findResumenes(List.of(7L), estados)).thenReturn(List.of(vigente));
        var borrador = referencia(7L, 72L, 2, EstadoVersionPlanControl.BORRADOR);
        when(versionRepo.findReferencias(eq(List.of(7L)), anyCollection(), eq(EstadoVersionPlanControl.RETIRADA)))
                .thenReturn(List.of(vigente, borrador));

        var result = service.listarResumenes(ambito, "  PeSo  ", EstadoVersionPlanControl.VIGENTE, 1, 1);

        assertEquals(3, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(1, result.getNumber());
        var row = result.getContent().get(0);
        assertEquals(ambito, row.ambito());
        assertEquals(72L, row.borrador().id());
        assertEquals(2, row.borrador().numero());
        assertEquals(71L, row.vigente().id());
        assertNull(row.ultimaRetirada());
        assertEquals(List.of(71L), row.versiones().stream().map(v -> v.id()).toList());
        assertEquals(4, row.versiones().get(0).cantidadCaracteristicas());
        verify(planRepo).findResumenes(ambito, "peso", estados, pageable);
        verify(planRepo, never()).findByAmbitoOrderByCodigoAsc(any());
        verify(versionRepo, never()).findByIdAndPlan_Ambito(anyLong(), any());
    }

    @ParameterizedTest
    @EnumSource(EstadoVersionPlanControl.class)
    void aplicaCadaEstadoEnLaConsultaPaginada(EstadoVersionPlanControl estado) {
        var pageable = PageRequest.of(0, 10);
        when(planRepo.findResumenes(AmbitoControl.CALIDAD, "", List.of(estado), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        assertTrue(service.listarResumenes(AmbitoControl.CALIDAD, null, estado, 0, 10).isEmpty());
        verifyNoInteractions(versionRepo);
    }

    @Test
    void todasIncluyeLosTresEstadosYUnaPaginaFueraDeRangoConservaElTotal() {
        var pageable = PageRequest.of(4, 10);
        when(planRepo.findResumenes(AmbitoControl.PROCESO, "", List.of(EstadoVersionPlanControl.values()), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 20));
        var result = service.listarResumenes(AmbitoControl.PROCESO, null, null, 4, 10);
        assertTrue(result.isEmpty());
        assertEquals(20, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals(4, result.getNumber());
        verifyNoInteractions(versionRepo);
    }

    @ParameterizedTest
    @CsvSource({"-1,10", "0,0", "0,-1", "0,51"})
    void rechazaLimitesInvalidosAntesDeConsultar(int page, int size) {
        assertThrows(IllegalArgumentException.class,
                () -> service.listarResumenes(AmbitoControl.CALIDAD, null, null, page, size));
        verifyNoInteractions(planRepo, versionRepo);
    }

    @ParameterizedTest
    @EnumSource(AmbitoControl.class)
    void detalleCargaSoloLaVersionSolicitadaYLasReferenciasDelPlan(AmbitoControl ambito) {
        PlanControl plan = new PlanControl();
        plan.setId(7L);
        plan.setCodigo("PLAN-7");
        plan.setNombre("Peso");
        plan.setAmbito(ambito);
        // The detail must not traverse all versions, including old historical content.
        plan.setVersiones(null);
        VersionPlanControl version = new VersionPlanControl();
        version.setId(71L);
        version.setNumero(1);
        version.setEstado(EstadoVersionPlanControl.RETIRADA);
        version.setPlan(plan);
        when(versionRepo.findByIdAndPlan_Ambito(71L, ambito)).thenReturn(Optional.of(version));
        var retirada = referencia(7L, 71L, 1, EstadoVersionPlanControl.RETIRADA);
        when(versionRepo.findReferencias(eq(List.of(7L)), anyCollection(), eq(EstadoVersionPlanControl.RETIRADA)))
                .thenReturn(List.of(retirada));

        var detail = service.detalleVersion(ambito, 7L, 71L);

        assertEquals(7L, detail.plan().id());
        assertEquals(ambito, detail.plan().ambito());
        assertEquals(71L, detail.version().id());
        assertEquals(EstadoVersionPlanControl.RETIRADA, detail.version().estado());
        assertEquals(71L, detail.plan().ultimaRetirada().id());
        assertEquals(1, detail.plan().versiones().size());
        verifyNoInteractions(planRepo);
    }

    @Test
    void detalleRechazaVersionDeOtroPlanOAmbito() {
        PlanControl plan = new PlanControl();
        plan.setId(8L);
        VersionPlanControl version = new VersionPlanControl();
        version.setPlan(plan);
        when(versionRepo.findByIdAndPlan_Ambito(71L, AmbitoControl.PROCESO)).thenReturn(Optional.of(version));
        when(versionRepo.findByIdAndPlan_Ambito(71L, AmbitoControl.CALIDAD)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.detalleVersion(AmbitoControl.PROCESO, 7L, 71L));
        assertThrows(NoSuchElementException.class, () -> service.detalleVersion(AmbitoControl.CALIDAD, 8L, 71L));
        verify(versionRepo, never()).findReferencias(anyCollection(), anyCollection(), any());
    }

    private VersionPlanControlRepo.ReferenciaVersion referencia(
            Long planId, Long id, int numero, EstadoVersionPlanControl estado) {
        var ref = mock(VersionPlanControlRepo.ReferenciaVersion.class);
        when(ref.getPlanId()).thenReturn(planId);
        when(ref.getId()).thenReturn(id);
        when(ref.getNumero()).thenReturn(numero);
        when(ref.getEstado()).thenReturn(estado);
        return ref;
    }
}
