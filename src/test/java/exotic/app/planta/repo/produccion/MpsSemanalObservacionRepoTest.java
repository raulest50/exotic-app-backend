package exotic.app.planta.repo.produccion;

import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalObservacion;
import exotic.app.planta.model.produccion.SemanaMPS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class MpsSemanalObservacionRepoTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mps_observaciones_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SemanaMPSRepo semanaRepo;

    @Autowired
    private MasterProductionScheduleSemanalRepo mpsRepo;

    @Autowired
    private MpsSemanalObservacionRepo observacionRepo;

    @BeforeEach
    void cleanDatabase() {
        observacionRepo.deleteAll();
        mpsRepo.deleteAll();
        semanaRepo.deleteAll();
    }

    @Test
    void persistsObservationAssociatedToMpsAndCountsOpenObservations() {
        MasterProductionScheduleSemanal mps = persistMps(LocalDate.of(2026, 6, 1));

        MpsSemanalObservacion observacion = new MpsSemanalObservacion();
        observacion.setMpsSemanal(mps);
        observacion.setRevisionMps(mps.getRevisionNumero());
        observacion.setAutorUsername("aprobador");
        observacion.setMensaje("Ajustar unidades del miercoles.");

        MpsSemanalObservacion saved = observacionRepo.saveAndFlush(observacion);

        assertNotNull(saved.getObservacionId());
        assertNotNull(saved.getFechaCreacion());
        assertEquals(EstadoMpsSemanalObservacion.ABIERTA, saved.getEstado());

        List<MpsSemanalObservacion> byMps = observacionRepo
                .findAllByMpsSemanal_MpsIdOrderByFechaCreacionAsc(mps.getMpsId());

        assertEquals(1, byMps.size());
        assertEquals("aprobador", byMps.getFirst().getAutorUsername());
        assertEquals(1, observacionRepo.countByMpsSemanal_MpsIdAndEstado(
                mps.getMpsId(),
                EstadoMpsSemanalObservacion.ABIERTA
        ));
        assertTrue(observacionRepo.existsByMpsSemanal_MpsIdAndEstado(
                mps.getMpsId(),
                EstadoMpsSemanalObservacion.ABIERTA
        ));
    }

    @Test
    void storesConceptualTransitionFromOpenToAttendedAndClosed() {
        MasterProductionScheduleSemanal mps = persistMps(LocalDate.of(2026, 6, 8));
        MpsSemanalObservacion observacion = new MpsSemanalObservacion();
        observacion.setMpsSemanal(mps);
        observacion.setRevisionMps(1);
        observacion.setAutorUsername("aprobador");
        observacion.setMensaje("Revisar volumen del viernes.");
        observacion = observacionRepo.saveAndFlush(observacion);

        observacion.setEstado(EstadoMpsSemanalObservacion.ATENDIDA);
        observacion.setRespuestaCorreccion("Volumen ajustado en revision 2.");
        observacion.setAtendidaPorUsername("programador");
        observacion.setFechaAtencion(LocalDateTime.of(2026, 6, 2, 10, 30));
        observacion = observacionRepo.saveAndFlush(observacion);

        observacion.setEstado(EstadoMpsSemanalObservacion.CERRADA);
        observacion.setCerradaPorUsername("aprobador");
        observacion.setFechaCierre(LocalDateTime.of(2026, 6, 2, 11, 15));
        MpsSemanalObservacion closed = observacionRepo.saveAndFlush(observacion);

        assertEquals(EstadoMpsSemanalObservacion.CERRADA, closed.getEstado());
        assertEquals("programador", closed.getAtendidaPorUsername());
        assertEquals("aprobador", closed.getCerradaPorUsername());
        assertEquals(0, observacionRepo.countByMpsSemanal_MpsIdAndEstado(
                mps.getMpsId(),
                EstadoMpsSemanalObservacion.ABIERTA
        ));
    }

    private MasterProductionScheduleSemanal persistMps(LocalDate weekStartDate) {
        int anioSemana = weekStartDate.get(IsoFields.WEEK_BASED_YEAR);
        int numeroSemana = weekStartDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        SemanaMPS semana = new SemanaMPS();
        semana.setCodigo("S%02d-%d".formatted(numeroSemana, anioSemana));
        semana.setAnioSemana(anioSemana);
        semana.setNumeroSemana(numeroSemana);
        semana.setStartDate(weekStartDate);
        semana.setEndDate(weekStartDate.plusDays(5));
        semana.setStandard(SemanaMPS.STANDARD_ISO_8601_MONDAY_SATURDAY);
        semana = semanaRepo.saveAndFlush(semana);

        MasterProductionScheduleSemanal mps = new MasterProductionScheduleSemanal();
        mps.setSemanaMps(semana);
        mps.setWeekStartDate(weekStartDate);
        mps.setWeekEndDate(weekStartDate.plusDays(5));
        mps.setRevisionNumero(1);
        mps.setEstado(EstadoMpsSemanal.BORRADOR);
        return mpsRepo.saveAndFlush(mps);
    }
}
