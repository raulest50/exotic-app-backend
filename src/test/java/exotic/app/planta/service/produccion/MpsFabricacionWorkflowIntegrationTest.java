package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.EstadoMpsSemanal;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.*;
import exotic.app.planta.model.produccion.fabricacion.MpsFabricacionDetalle;
import exotic.app.planta.model.produccion.fabricacion.MpsFabricacionSemanal;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.SemiTerminadoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import exotic.app.planta.repo.produccion.fabricacion.*;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostgreSQL, Flyway, Hibernate y transacciones reales, sin iniciar la aplicacion.
 * Solo se simulan directivas y colaboradores de ejecucion/proyeccion documental.
 * El directorio temporal V1/V119/V120 construye exclusivamente este fixture;
 * no valida la configuracion operativa actual del perfil schema-v1.
 */
@Testcontainers(disabledWithoutDocker = true)
class MpsFabricacionWorkflowIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.1");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate WEEK = LocalDate.of(2026, 11, 9);
    private static final String[] MIGRATIONS = {
            "db/migration-v1/V001__esquema_actual_consolidado.sql",
            "db/migration/V119__add_despeje_linea.sql",
            "db/migration/V120__mps_of_independiente.sql"
    };
    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManagerFactory entityManagerFactory;
    private static JpaTransactionManager transactionManager;
    private static JdbcTemplate jdbc;
    private static Path migrationsDirectory;

    private EntityManager em;
    private TransactionTemplate tx;
    private MpsFabricacionSemanalRepo programaRepo;
    private MpsFabricacionObservacionRepo observacionRepo;
    private MpsFabricacionService programaService;
    private MpsFabricacionEmisionService emisionService;
    private MpsFabricacionAprobacionService aprobacionService;
    private MpsFabricacionObservacionService observacionService;
    private OrdenFabricacionOperacionService operacionService;
    private User actor;

    @BeforeAll
    static void construirEsquemaYValidarMappings() throws Exception {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrationsDirectory = Files.createTempDirectory("mps-of-integration-");
        for (String resource : MIGRATIONS) {
            try (InputStream input = Objects.requireNonNull(
                    MpsFabricacionWorkflowIntegrationTest.class.getClassLoader().getResourceAsStream(resource), resource)) {
                Files.copy(input, migrationsDirectory.resolve(Path.of(resource).getFileName()));
            }
        }
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + migrationsDirectory.toAbsolutePath().toString().replace('\\', '/'))
                .baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true)
                .validateMigrationNaming(true).load();
        flyway.migrate();
        flyway.validate();

        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("exotic.app.planta.model");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                "hibernate.globally_quoted_identifiers", "true"));
        factory.afterPropertiesSet();
        entityManagerFactory = Objects.requireNonNull(factory.getObject());
        transactionManager = new JpaTransactionManager(entityManagerFactory);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void cerrarFixture() throws Exception {
        if (factory != null) factory.destroy();
        if (migrationsDirectory != null) {
            for (String resource : MIGRATIONS) {
                Files.deleteIfExists(migrationsDirectory.resolve(Path.of(resource).getFileName()));
            }
            Files.deleteIfExists(migrationsDirectory);
        }
    }

    @BeforeEach
    void serviciosRealesConRepositoriosReales() {
        // El unico destino posible es el contenedor efimero de esta clase.
        jdbc.execute("TRUNCATE TABLE public.mps_fabricacion_semanal, public.productos, public.users RESTART IDENTITY CASCADE");
        em = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        tx = new TransactionTemplate(transactionManager);
        var repositories = new JpaRepositoryFactory(em);
        programaRepo = repositories.getRepository(MpsFabricacionSemanalRepo.class);
        observacionRepo = repositories.getRepository(MpsFabricacionObservacionRepo.class);
        var semiRepo = repositories.getRepository(SemiTerminadoRepo.class);
        var manufacturingRepo = repositories.getRepository(ManufacturingVersionRepo.class);
        var ordenRepo = repositories.getRepository(OrdenFabricacionRepo.class);
        var loteRepo = repositories.getRepository(LoteRepo.class);
        var productoRepo = repositories.getRepository(ProductoRepo.class);
        var directivas = mock(MasterDirectiveService.class);
        when(directivas.lockBatchRecordWorkflowForNewOrder()).thenReturn(true);
        operacionService = mock(OrdenFabricacionOperacionService.class);
        when(operacionService.listar(anyLong())).thenReturn(List.of());

        var ordenService = transaccional(new OrdenFabricacionService(
                ordenRepo, semiRepo, manufacturingRepo, loteRepo, mock(UserRepository.class),
                mock(BatchRecordRepo.class), mock(VencimientoLoteService.class),
                mock(BatchRecordProjectionQueueService.class), directivas, operacionService,
                mock(OrdenFabricacionOperacionEventoRepo.class), mock(TransaccionAlmacenHeaderRepo.class),
                transaccional(new LoteManufacturaNumeroService(productoRepo, loteRepo, CLOCK)), CLOCK));
        programaService = transaccional(new MpsFabricacionService(
                programaRepo, semiRepo, manufacturingRepo, ordenRepo, loteRepo, ordenService,
                new ObjectMapper(), CLOCK, directivas, observacionRepo));
        emisionService = transaccional(new MpsFabricacionEmisionService(programaRepo, programaService, ordenService, CLOCK));
        aprobacionService = transaccional(new MpsFabricacionAprobacionService(programaService, observacionRepo, CLOCK));
        observacionService = transaccional(new MpsFabricacionObservacionService(programaService, programaRepo, observacionRepo, CLOCK));
        actor = tx.execute(status -> {
            User user = new User();
            user.setUsername("aprobador-fixture");
            user.setNombreCompleto("Aprobador fixture");
            user.setCedula(1L);
            em.persist(user);
            return user;
        });
    }

    @Test
    void fallaSegundaTandaReviertePrimeraOfLoteYEmision() {
        Programa programa = preparar(EstadoMpsSemanal.APROBADO, true, false);

        assertThrows(IllegalStateException.class, () -> emisionService.emitir(WEEK, programa.version(), actor));

        // La primera tanda llego a persistir OF y lote antes del fallo de la segunda.
        verify(operacionService).inicializar(any(), isNull());
        assertEquals(0, contar("orden_fabricacion"));
        assertEquals(0, contar("lote"));
        assertEquals("APROBADO", jdbc.queryForObject(
                "SELECT estado FROM mps_fabricacion_semanal WHERE id = ?", String.class, programa.id()));
        assertNull(jdbc.queryForObject(
                "SELECT fecha_emision FROM mps_fabricacion_semanal WHERE id = ?", Object.class, programa.id()));
    }

    @Test
    void reintentoDeEmisionConVersionAnteriorDevuelveMismasOfYLotes() {
        Programa programa = preparar(EstadoMpsSemanal.APROBADO, true, true);
        ProgramaResponse primero = emisionService.emitir(WEEK, programa.version(), actor);
        List<Long> ids = primero.getPropuestas().stream().map(LineaResponse::getOrdenFabricacionId).toList();

        ProgramaResponse reintento = emisionService.emitir(WEEK, programa.version(), actor);

        assertEquals(EstadoMpsSemanal.CERRADO, reintento.getEstado());
        assertEquals(ids, reintento.getPropuestas().stream().map(LineaResponse::getOrdenFabricacionId).toList());
        assertTrue(ids.stream().allMatch(Objects::nonNull));
        assertEquals(2, ids.stream().distinct().count());
        assertEquals(2, contar("orden_fabricacion"));
        assertEquals(2, contar("lote"));
        verify(operacionService, times(2)).inicializar(any(), isNull());
    }

    @Test
    void baseRechazaDosOfParaLaMismaTanda() {
        Programa programa = preparar(EstadoMpsSemanal.APROBADO, true, true);
        var emitido = emisionService.emitir(WEEK, programa.version(), actor);
        Long segundaOf = emitido.getPropuestas().get(1).getOrdenFabricacionId();

        assertRestriccion("uk_of_mps_fabricacion_detalle", () -> jdbc.update(
                "UPDATE orden_fabricacion SET mps_fabricacion_detalle_id = ? WHERE orden_fabricacion_id = ?",
                programa.detalles().getFirst(), segundaOf));
    }

    @Test
    void baseRechazaOfConOrigenOpYMpsSimultaneo() {
        Programa programa = preparar(EstadoMpsSemanal.APROBADO, true);
        Long ofId = emisionService.emitir(WEEK, programa.version(), actor)
                .getPropuestas().getFirst().getOrdenFabricacionId();
        Integer opId = tx.execute(status -> {
            OrdenProduccion op = new OrdenProduccion();
            em.persist(op);
            return op.getOrdenId();
        });

        assertRestriccion("chk_of_origen_independiente", () -> jdbc.update(
                "UPDATE orden_fabricacion SET orden_produccion_origen_id = ? WHERE orden_fabricacion_id = ?", opId, ofId));
    }

    @Test
    void baseImpideBorrarUnaTandaEmitida() {
        Programa programa = preparar(EstadoMpsSemanal.APROBADO, true);
        emisionService.emitir(WEEK, programa.version(), actor);

        assertRestriccion("fk_of_mps_fabricacion_detalle", () -> jdbc.update(
                "DELETE FROM mps_fabricacion_detalle WHERE id = ?", programa.detalles().getFirst()));
        assertEquals(1, contar("orden_fabricacion"));
        assertEquals(1, contar("lote"));
    }

    @Test
    void observacionAtendidaRequiereCierreAntesDeAprobar() {
        Programa programa = preparar(EstadoMpsSemanal.BORRADOR, true);
        var crear = new CrearObservacionRequest();
        crear.setVersion(programa.version());
        crear.setMensaje("Revisar la cantidad de la tanda");
        var abierta = observacionService.crear(WEEK, crear, actor);
        var atender = new AtenderObservacionRequest();
        atender.setVersion(abierta.getVersion());
        atender.setRespuestaCorreccion("Cantidad contrastada con fabricacion");
        var atendida = observacionService.atender(abierta.getObservacionId(), atender, actor);

        assertThrows(ResponseStatusException.class, () -> aprobacionService.aprobar(WEEK, atendida.getVersion(), actor));
        var cerrar = new VersionRequest();
        cerrar.setVersion(atendida.getVersion());
        var cerrada = observacionService.cerrar(atendida.getObservacionId(), cerrar, actor);

        assertEquals(EstadoMpsSemanal.APROBADO, aprobacionService.aprobar(WEEK, cerrada.getVersion(), actor).getEstado());
        assertEquals(0, contar("orden_fabricacion"));
        assertEquals(1, contar("mps_fabricacion_observacion"));
    }

    @Test
    void aprobacionEsperaBloqueoDelPadreAntesDeCambiarEstado() {
        Programa programa = preparar(EstadoMpsSemanal.BORRADOR, true);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            tx.executeWithoutResult(status -> {
                programaRepo.findByWeekForUpdate(WEEK).orElseThrow();
                Future<?> espera = executor.submit(() -> tx.execute(worker -> {
                    em.createNativeQuery("SET LOCAL lock_timeout = '300ms'").executeUpdate();
                    return aprobacionService.aprobar(WEEK, programa.version(), actor);
                }));
                ExecutionException error = assertThrows(ExecutionException.class, () -> espera.get(5, TimeUnit.SECONDS));
                assertTrue(tieneSqlState(error, "55P03"), "El bloqueo debe fallar por lock_timeout de PostgreSQL");
            });
        }
        assertEquals("BORRADOR", jdbc.queryForObject(
                "SELECT estado FROM mps_fabricacion_semanal WHERE id = ?", String.class, programa.id()));
        assertEquals(EstadoMpsSemanal.APROBADO, aprobacionService.aprobar(WEEK, programa.version(), actor).getEstado());
    }

    private Programa preparar(EstadoMpsSemanal estado, boolean... conManufactura) {
        return Objects.requireNonNull(tx.execute(status -> {
            MpsFabricacionSemanal programa = new MpsFabricacionSemanal();
            programa.setWeekStartDate(WEEK);
            programa.setEstado(estado);
            programa.setCreadoEn(LocalDateTime.now(CLOCK));
            programa.setActualizadoEn(LocalDateTime.now(CLOCK));
            programa.setCreadoPor(actor.getUsername());
            programa.setActualizadoPor(actor.getUsername());
            if (estado == EstadoMpsSemanal.APROBADO) {
                programa.setAprobadoPorUsername(actor.getUsername());
                programa.setFechaAprobacion(LocalDateTime.now(CLOCK));
            }
            for (int i = 0; i < conManufactura.length; i++) {
                SemiTerminado semi = new SemiTerminado();
                semi.setProductoId("OF-FIX-" + i);
                semi.setNombre("Granel fixture " + i);
                semi.setTipoUnidades("KG");
                semi.setPrefijoLote("FX" + i);
                semi.setRequiereOrdenFabricacion(true);
                em.persist(semi);
                if (conManufactura[i]) {
                    ManufacturingVersions version = new ManufacturingVersions();
                    version.setProducto(semi);
                    version.setVersionNumber(1);
                    em.persist(version);
                }
                MpsFabricacionDetalle detalle = new MpsFabricacionDetalle();
                detalle.setMps(programa);
                detalle.setSemiTerminado(semi);
                detalle.setCantidad(new BigDecimal("100.0000"));
                detalle.setUnidadMedida("KG");
                detalle.setFechaInicio(WEEK.atTime(8, 0));
                detalle.setFechaFinal(WEEK.atTime(16, 0));
                detalle.setPosicion(i);
                programa.getDetalles().add(detalle);
            }
            em.persist(programa);
            em.flush();
            return new Programa(programa.getId(), programa.getVersion(), programa.getDetalles().stream()
                    .map(MpsFabricacionDetalle::getId).toList());
        }));
    }

    private long contar(String tabla) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class));
    }

    private void assertRestriccion(String nombre, Runnable accion) {
        DataIntegrityViolationException error = assertThrows(DataIntegrityViolationException.class, accion::run);
        assertTrue(Objects.requireNonNull(error.getMostSpecificCause().getMessage()).contains(nombre));
    }

    private static boolean tieneSqlState(Throwable error, String sqlState) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof SQLException sql && sqlState.equals(sql.getSQLState())) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <T> T transaccional(T target) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    private record Programa(Long id, Long version, List<Long> detalles) {}
}
