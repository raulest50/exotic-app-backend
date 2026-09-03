package exotic.app.planta.service.calidad;

import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaRequest;
import exotic.app.planta.model.calidad.dto.CalidadControlProcesoDTOs.PlantillaResponse;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.controles.*;
import exotic.app.planta.service.controles.ControlExecutionService;
import exotic.app.planta.service.controles.LegacyControlPlanSynchronizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyControlCompatibilityServiceTest {

    private CalidadControlProcesoService legacyService;
    private LegacyControlPlanSynchronizer synchronizer;
    private ControlProcesoPlantillaRepo legacyPlanRepo;
    private LegacyControlCompatibilityService service;
    private User actor;

    @BeforeEach
    void setUp() {
        legacyService = mock(CalidadControlProcesoService.class);
        synchronizer = mock(LegacyControlPlanSynchronizer.class);
        legacyPlanRepo = mock(ControlProcesoPlantillaRepo.class);
        service = new LegacyControlCompatibilityService(
                legacyService, synchronizer, mock(ControlExecutionService.class),
                legacyPlanRepo, mock(ControlProcesoEjecucionRepo.class),
                mock(VersionPlanControlRepo.class), mock(CaracteristicaPlanControlRepo.class),
                mock(ControlRequeridoRepo.class), mock(EjecucionControlRepo.class));
        actor = new User();
        actor.setId(9L);
        actor.setUsername("tecnico");
    }

    @Test
    void guardarBorrador_escribeLegadoYSincronizaNeutralEnLaMismaOperacion() throws Exception {
        PlantillaRequest request = PlantillaRequest.builder()
                .areaOperativaId(7).caracteristicas(List.of()).build();
        PlantillaResponse response = PlantillaResponse.builder().id(15L).build();
        when(legacyService.guardarBorrador(request)).thenReturn(response);

        assertSame(response, service.guardarBorrador(actor, request));

        InOrder order = inOrder(synchronizer, legacyService);
        order.verify(synchronizer).requireLegacyOwnedFamily(7);
        order.verify(synchronizer).prepareLegacyDraftReplacement(7);
        order.verify(legacyService).guardarBorrador(request);
        order.verify(synchronizer).synchronizeArea(7, actor);
        assertNotNull(LegacyControlCompatibilityService.class
                .getMethod("guardarBorrador", User.class, PlantillaRequest.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void guardarBorrador_noTocaLegadoCuandoLaFamiliaYaEsNativa() {
        PlantillaRequest request = PlantillaRequest.builder()
                .areaOperativaId(7).caracteristicas(List.of()).build();
        doThrow(new IllegalStateException("familia nativa"))
                .when(synchronizer).requireLegacyOwnedFamily(7);

        assertThrows(IllegalStateException.class,
                () -> service.guardarBorrador(actor, request));

        verifyNoInteractions(legacyService);
        verify(synchronizer, never()).prepareLegacyDraftReplacement(anyInt());
    }

    @Test
    void retirarEsElUnicoCortePermitidoDespuesDeCrearBorradorNativo() {
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(7);
        ControlProcesoPlantilla plan = new ControlProcesoPlantilla();
        plan.setId(15L);
        plan.setAreaOperativa(area);
        PlantillaResponse response = PlantillaResponse.builder().id(15L).build();
        when(legacyPlanRepo.findByIdForUpdate(15L)).thenReturn(Optional.of(plan));
        when(legacyService.retirarPlantilla(15L)).thenReturn(response);

        assertSame(response, service.retirarPlantilla(actor, 15L));

        verify(synchronizer, never()).requireLegacyOwnedFamily(anyInt());
        verify(synchronizer).synchronizeRetirement(7, actor);
    }
}
