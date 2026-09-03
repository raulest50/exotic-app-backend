package exotic.app.planta.service.controles;

import exotic.app.planta.model.calidad.ControlProcesoCaracteristica;
import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LegacyControlExecutionProjectionTest {

    private ControlProcesoEjecucionRepo legacyRepo;
    private LegacyControlExecutionProjection projection;
    private ControlRequerido required;
    private MuestraEjecucionControl sample;

    @BeforeEach
    void setUp() {
        legacyRepo = mock(ControlProcesoEjecucionRepo.class);
        projection = new LegacyControlExecutionProjection(legacyRepo);

        ControlProcesoPlantilla legacyPlan = new ControlProcesoPlantilla();
        legacyPlan.setId(10L);
        VersionPlanControl version = new VersionPlanControl();
        version.setLegacyPlantilla(legacyPlan);
        Lote lot = new Lote();
        lot.setId(20L);
        required = new ControlRequerido();
        required.setVersionPlan(version);
        required.setLote(lot);

        ControlProcesoCaracteristica legacyCharacteristic = new ControlProcesoCaracteristica();
        legacyCharacteristic.setId(30L);
        CaracteristicaPlanControl characteristic = new CaracteristicaPlanControl();
        characteristic.setLegacyCaracteristica(legacyCharacteristic);
        sample = new MuestraEjecucionControl();
        sample.setCaracteristica(characteristic);
        sample.setNumeroMuestra(1);

        when(legacyRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            ControlProcesoEjecucion saved = invocation.getArgument(0);
            saved.setId(40L);
            return saved;
        });
    }

    @Test
    void createMirror_preservaMatrizYDecimalRepresentable() {
        LecturaEjecucionControl reading = new LecturaEjecucionControl();
        reading.setIndiceUnidad(1);
        reading.setValorNumerico(new BigDecimal("1.25"));
        sample.getLecturas().add(reading);
        User actor = new User();
        actor.setId(5L);

        ControlProcesoEjecucion mirror = projection.createMirror(
                required, actor, LocalDateTime.of(2026, 9, 2, 10, 0),
                ResultadoEjecucionControl.CONFORME, "registro", List.of(sample));

        assertEquals(40L, mirror.getId());
        assertEquals(1.25d, mirror.getMuestras().getFirst()
                .getLecturas().getFirst().getValorNumerico());
        assertEquals(ResultadoEjecucionControl.CONFORME.name(), mirror.getResultado().name());
        assertSame(actor, mirror.getUsuario());
    }

    @Test
    void createMirror_rechazaPerdidaDePrecisionEnDoubleLegado() {
        LecturaEjecucionControl reading = new LecturaEjecucionControl();
        reading.setIndiceUnidad(1);
        reading.setValorNumerico(new BigDecimal("123456789012.12345678"));
        sample.getLecturas().add(reading);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> projection.createMirror(required, new User(), LocalDateTime.now(),
                        ResultadoEjecucionControl.CONFORME, null, List.of(sample)));

        assertTrue(error.getMessage().contains("sin perdida"));
        verify(legacyRepo, never()).saveAndFlush(any());
    }
}
