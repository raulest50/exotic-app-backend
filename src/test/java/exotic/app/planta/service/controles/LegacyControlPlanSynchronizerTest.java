package exotic.app.planta.service.controles;

import exotic.app.planta.model.calidad.*;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoPlantillaRepo;
import exotic.app.planta.repo.controles.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LegacyControlPlanSynchronizerTest {

    private ControlProcesoPlantillaRepo legacyRepo;
    private PlanControlRepo planRepo;
    private VersionPlanControlRepo versionRepo;
    private ControlRequeridoRepo requiredRepo;
    private MagnitudControlRepo magnitudeRepo;
    private UnidadControlRepo unitRepo;
    private LegacyControlPlanSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        legacyRepo = mock(ControlProcesoPlantillaRepo.class);
        planRepo = mock(PlanControlRepo.class);
        versionRepo = mock(VersionPlanControlRepo.class);
        requiredRepo = mock(ControlRequeridoRepo.class);
        magnitudeRepo = mock(MagnitudControlRepo.class);
        unitRepo = mock(UnidadControlRepo.class);
        synchronizer = new LegacyControlPlanSynchronizer(
                legacyRepo, planRepo, versionRepo, requiredRepo, magnitudeRepo, unitRepo);
    }

    @Test
    void synchronizeArea_creaProyeccionDeterministaDeNuevaPlantilla() {
        ControlProcesoPlantilla legacy = legacyPlan(EstadoControlProcesoPlantilla.BORRADOR);
        when(legacyRepo.buscar(7, null)).thenReturn(List.of(legacy));
        when(planRepo.findByCodigoIgnoreCase("LEGACY-PROCESO-AREA-7")).thenReturn(Optional.empty());
        when(planRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            PlanControl plan = invocation.getArgument(0);
            plan.setId(50L);
            return plan;
        });
        MagnitudControl magnitude = new MagnitudControl();
        magnitude.setId(60L); magnitude.setCodigo("PESO"); magnitude.setNombre("Peso");
        magnitude.setSimbolo("m"); magnitude.setDimension("MASA"); magnitude.setActivo(true);
        UnidadControl unit = new UnidadControl();
        unit.setId(70L); unit.setCodigo("G"); unit.setNombre("Gramo");
        unit.setSimbolo("g"); unit.setDimension("MASA"); unit.setActivo(true);
        when(magnitudeRepo.findByCodigoIgnoreCase("PESO")).thenReturn(Optional.of(magnitude));
        when(unitRepo.findByCodigoIgnoreCase("G")).thenReturn(Optional.of(unit));

        synchronizer.synchronizeArea(7, actor());

        ArgumentCaptor<VersionPlanControl> captor = ArgumentCaptor.forClass(VersionPlanControl.class);
        verify(versionRepo).saveAndFlush(captor.capture());
        VersionPlanControl projected = captor.getValue();
        assertSame(legacy, projected.getLegacyPlantilla());
        assertEquals(EstadoVersionPlanControl.BORRADOR, projected.getEstado());
        assertTrue(projected.getAplicabilidades().getFirst().isLegadoGlobal());
        assertEquals(PuntoExigenciaControl.INFORMATIVO,
                projected.getAplicabilidades().getFirst().getPuntoExigencia());
        assertEquals("PESO", projected.getCaracteristicas().getFirst().getMagnitudCodigoSnapshot());
        assertEquals("m", projected.getCaracteristicas().getFirst().getMagnitudSimboloSnapshot());
        assertEquals("G", projected.getCaracteristicas().getFirst().getUnidadCodigoSnapshot());
    }

    @Test
    void prepareLegacyDraftReplacement_rechazaBorradorYaMaterializado() {
        ControlProcesoPlantilla legacy = legacyPlan(EstadoControlProcesoPlantilla.BORRADOR);
        VersionPlanControl projected = new VersionPlanControl();
        projected.setId(80L);
        projected.setEstado(EstadoVersionPlanControl.BORRADOR);
        when(legacyRepo.findFirstByAreaOperativa_AreaIdAndEstado(
                7, EstadoControlProcesoPlantilla.BORRADOR)).thenReturn(Optional.of(legacy));
        when(versionRepo.findByLegacyPlantilla_Id(10L)).thenReturn(Optional.of(projected));
        when(requiredRepo.existsByVersionPlan_Id(80L)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> synchronizer.prepareLegacyDraftReplacement(7));

        verify(versionRepo, never()).saveAndFlush(any());
    }

    @Test
    void synchronizeArea_marcaNombreYUnidadVaciosSinInventarAdimensionalidad() {
        ControlProcesoPlantilla legacy = legacyPlan(EstadoControlProcesoPlantilla.BORRADOR);
        ControlProcesoCaracteristica source = legacy.getCaracteristicas().getFirst();
        source.setNombre("   ");
        source.setUnidad(null);
        when(legacyRepo.buscar(7, null)).thenReturn(List.of(legacy));
        when(planRepo.findByCodigoIgnoreCase("LEGACY-PROCESO-AREA-7")).thenReturn(Optional.empty());
        when(planRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            PlanControl plan = invocation.getArgument(0);
            plan.setId(50L);
            return plan;
        });
        when(magnitudeRepo.findByCodigoIgnoreCase("LEGACY_ID_11")).thenReturn(Optional.empty());
        when(unitRepo.findByCodigoIgnoreCase("LEGACY_SIN_UNIDAD_11")).thenReturn(Optional.empty());
        when(magnitudeRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            MagnitudControl item = invocation.getArgument(0);
            item.setId(60L);
            return item;
        });
        when(unitRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            UnidadControl item = invocation.getArgument(0);
            item.setId(70L);
            return item;
        });

        synchronizer.synchronizeArea(7, actor());

        ArgumentCaptor<VersionPlanControl> captor = ArgumentCaptor.forClass(VersionPlanControl.class);
        verify(versionRepo).saveAndFlush(captor.capture());
        CaracteristicaPlanControl projected = captor.getValue().getCaracteristicas().getFirst();
        assertEquals("[LEGACY SIN NOMBRE #11]", projected.getNombre());
        assertEquals("LEGACY_ID_11", projected.getMagnitudCodigoSnapshot());
        assertEquals("?", projected.getMagnitudSimboloSnapshot());
        assertEquals("LEGACY_SIN_UNIDAD_11", projected.getUnidadCodigoSnapshot());
        assertNotEquals("ADIMENSIONAL", projected.getUnidadCodigoSnapshot());
        assertTrue(projected.isRequiereDepuracion());
    }

    @Test
    void synchronizeArea_marcaDimensionHistoricaIncompatibleParaDepuracion() {
        ControlProcesoPlantilla legacy = legacyPlan(EstadoControlProcesoPlantilla.BORRADOR);
        legacy.getCaracteristicas().getFirst().setUnidad("cP");
        when(legacyRepo.buscar(7, null)).thenReturn(List.of(legacy));
        when(planRepo.findByCodigoIgnoreCase("LEGACY-PROCESO-AREA-7")).thenReturn(Optional.empty());
        when(planRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            PlanControl plan = invocation.getArgument(0);
            plan.setId(50L);
            return plan;
        });
        MagnitudControl magnitude = new MagnitudControl();
        magnitude.setId(60L); magnitude.setCodigo("PESO"); magnitude.setNombre("Peso");
        magnitude.setSimbolo("m"); magnitude.setDimension("MASA"); magnitude.setActivo(true);
        UnidadControl unit = new UnidadControl();
        unit.setId(70L); unit.setCodigo("CP"); unit.setNombre("Centipoise");
        unit.setSimbolo("cP"); unit.setDimension("VISCOSIDAD_DINAMICA"); unit.setActivo(true);
        when(magnitudeRepo.findByCodigoIgnoreCase("PESO")).thenReturn(Optional.of(magnitude));
        when(unitRepo.findByCodigoIgnoreCase("CP")).thenReturn(Optional.of(unit));

        synchronizer.synchronizeArea(7, actor());

        ArgumentCaptor<VersionPlanControl> captor = ArgumentCaptor.forClass(VersionPlanControl.class);
        verify(versionRepo).saveAndFlush(captor.capture());
        assertTrue(captor.getValue().getCaracteristicas().getFirst().isRequiereDepuracion());
    }

    private ControlProcesoPlantilla legacyPlan(EstadoControlProcesoPlantilla state) {
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(7);
        area.setNombre("Envasado");
        ControlProcesoPlantilla plan = new ControlProcesoPlantilla();
        plan.setId(10L);
        plan.setAreaOperativa(area);
        plan.setVersion(1);
        plan.setEstado(state);
        ControlProcesoCaracteristica characteristic = new ControlProcesoCaracteristica();
        characteristic.setId(11L);
        characteristic.setPlantilla(plan);
        characteristic.setNombre("Peso");
        characteristic.setTipo(TipoCaracteristicaControlProceso.NUMERICA);
        characteristic.setUnidad("g");
        characteristic.setOrden(1);
        characteristic.setCantidadMuestras(1);
        characteristic.setUnidadesPorMuestra(1);
        characteristic.setLimiteInferior(9.5);
        characteristic.setLimiteSuperior(10.5);
        plan.getCaracteristicas().add(characteristic);
        return plan;
    }

    private User actor() {
        User actor = new User();
        actor.setId(5L);
        actor.setUsername("tecnico");
        return actor;
    }
}
