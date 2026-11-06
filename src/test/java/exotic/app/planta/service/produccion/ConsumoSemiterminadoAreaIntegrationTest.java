package exotic.app.planta.service.produccion;

import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.inventarios.Movimiento;
import exotic.app.planta.model.inventarios.TransaccionAlmacen;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.producto.Categoria;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.produccion.SeguimientoOrdenArea;
import exotic.app.planta.model.produccion.dto.ConsumoSemiterminadoAreaDTOs.LoteRequest;
import exotic.app.planta.model.produccion.dto.ConsumoSemiterminadoAreaDTOs.RegistroRequest;
import exotic.app.planta.model.produccion.dto.ConsumoSemiterminadoAreaDTOs.RegistroResponse;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion;
import exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.inventarios.HistorialDispensacionesQuery;
import exotic.app.planta.repo.inventarios.LoteRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenHeaderRepo;
import exotic.app.planta.repo.inventarios.TransaccionAlmacenRepo;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.manufacturing.snapshots.ManufacturingVersionRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.produccion.OrdenProduccionRepo;
import exotic.app.planta.repo.produccion.SeguimientoOrdenAreaRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionOperacionRepo;
import exotic.app.planta.repo.produccion.fabricacion.OrdenFabricacionRepo;
import exotic.app.planta.repo.produccion.ruprocatdesigner.RutaProcesoCatVersionRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.inventarios.SalidaAlmacenService;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fixture aislado PostgreSQL/Flyway/Hibernate, sin arrancar Spring ni consultar
 * configuracion local. V1/V119/V120/V121 solo construyen este esquema efimero;
 * no acreditan la configuracion operativa del perfil schema-v1.
 */
@Testcontainers(disabledWithoutDocker = true)
class ConsumoSemiterminadoAreaIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.1");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
    private static final String[] MIGRATIONS = {
            "db/migration-v1/V001__esquema_actual_consolidado.sql",
            "db/migration/V119__add_despeje_linea.sql",
            "db/migration/V120__mps_of_independiente.sql",
            "db/migration/V121__consumo_semiterminado_por_area.sql"
    };
    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManagerFactory entityManagerFactory;
    private static JpaTransactionManager transactionManager;
    private static JdbcTemplate jdbc;
    private static Path migrationsDirectory;
    private EntityManager em;
    private TransactionTemplate tx;
    private MaterialRequirementSnapshotService snapshotService;
    private ConsumoSemiterminadoAreaService consumoService;
    private BatchRecordProjectionQueueService projectionQueueService;

    @BeforeAll
    static void construirEsquemaYValidarMappings() throws Exception {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrationsDirectory = Files.createTempDirectory("consumo-semi-area-integration-");
        for (String resource : MIGRATIONS) {
            try (InputStream input = Objects.requireNonNull(
                    ConsumoSemiterminadoAreaIntegrationTest.class.getClassLoader().getResourceAsStream(resource), resource)) {
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
    void prepararPersistenciaReal() {
        // El unico destino posible de esta limpieza es el contenedor efimero.
        jdbc.execute("TRUNCATE TABLE public.productos, public.categoria, public.area_operativa, "
                + "public.ordenes_produccion, public.transaccion_almacen, public.users RESTART IDENTITY CASCADE");
        em = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        tx = new TransactionTemplate(transactionManager);
        var repositories = new JpaRepositoryFactory(em);
        snapshotService = new MaterialRequirementSnapshotService(
                repositories.getRepository(ProductoRepo.class),
                repositories.getRepository(ManufacturingVersionRepo.class), new ObjectMapper(),
                repositories.getRepository(RutaProcesoCatVersionRepo.class));
        var ordenRepo = repositories.getRepository(OrdenProduccionRepo.class);
        var seguimientoRepo = repositories.getRepository(SeguimientoOrdenAreaRepo.class);
        var movimientoRepo = repositories.getRepository(TransaccionAlmacenRepo.class);
        var transaccionRepo = repositories.getRepository(TransaccionAlmacenHeaderRepo.class);
        var productoRepo = repositories.getRepository(ProductoRepo.class);
        var loteRepo = repositories.getRepository(LoteRepo.class);
        var salidaService = transaccional(new SalidaAlmacenService(
                ordenRepo, productoRepo, loteRepo, mock(UserRepository.class), transaccionRepo,
                mock(HistorialDispensacionesQuery.class), mock(ProduccionService.class),
                mock(AreaProduccionRepo.class), mock(SeguimientoOrdenAreaService.class),
                mock(OrdenFabricacionRepo.class), mock(OrdenFabricacionOperacionRepo.class),
                movimientoRepo, snapshotService, em, CLOCK));
        projectionQueueService = mock(BatchRecordProjectionQueueService.class);
        consumoService = transaccional(new ConsumoSemiterminadoAreaService(
                ordenRepo, seguimientoRepo, snapshotService, movimientoRepo, transaccionRepo,
                productoRepo, loteRepo, salidaService, projectionQueueService, em, new ObjectMapper()));
    }

    @Test
    void asociacionJpaConservaVersionYProtegeIdentidadDelSemiterminado() {
        RutaFixture ruta = crearRuta();

        tx.executeWithoutResult(status -> {
            RutaProcesoNode nodo = em.find(RutaProcesoNode.class, ruta.nodoId());
            assertEquals(1, nodo.getSemiterminadosConsumidos().size());
            assertEquals(ruta.semiId(), nodo.getSemiterminadosConsumidos().iterator().next().getProductoId());
        });
        assertRestriccion("pk_ruta_node_semiterminado", () -> jdbc.update(
                "INSERT INTO ruta_proceso_node_semiterminado(node_id, producto_id) VALUES (?, ?)",
                ruta.nodoId(), ruta.semiId()));
        assertRestriccion("fk_ruta_node_semiterminado_producto", () -> jdbc.update(
                "DELETE FROM productos WHERE producto_id = ?", ruta.semiId()));

        jdbc.update("DELETE FROM ruta_proceso_node WHERE id = ?", ruta.nodoId());
        assertEquals(0, contar("ruta_proceso_node_semiterminado"));
        assertEquals(1, contar("productos"), "Eliminar el nodo no elimina el semiterminado");
    }

    @Test
    void historicosAdmitenSnapshotYClaveNulosSinBackfillInventado() {
        Integer opId = tx.execute(status -> {
            OrdenProduccion op = new OrdenProduccion();
            em.persist(op);
            return op.getOrdenId();
        });
        int transaccionId = crearTransaccion();

        assertNull(jdbc.queryForObject("SELECT requerimientos_materiales_json FROM ordenes_produccion WHERE orden_id = ?",
                String.class, opId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM transaccion_almacen WHERE transaccion_id = ? "
                + "AND idempotency_key IS NULL AND request_hash IS NULL", Integer.class, transaccionId));
    }

    @Test
    void baseImpideReintentosDuplicadosYHashIncompleto() {
        int primera = crearTransaccion();
        int segunda = crearTransaccion();
        UUID clave = UUID.randomUUID();
        String hash = "a".repeat(64);
        jdbc.update("UPDATE transaccion_almacen SET idempotency_key = ?, request_hash = ? WHERE transaccion_id = ?",
                clave, hash, primera);

        assertRestriccion("uk_transaccion_almacen_idempotency_key", () -> jdbc.update(
                "UPDATE transaccion_almacen SET idempotency_key = ?, request_hash = ? WHERE transaccion_id = ?",
                clave, hash, segunda));
        assertRestriccion("chk_transaccion_almacen_idempotency_pair", () -> jdbc.update(
                "UPDATE transaccion_almacen SET idempotency_key = ? WHERE transaccion_id = ?", UUID.randomUUID(), segunda));
        assertRestriccion("chk_transaccion_almacen_idempotency_pair", () -> jdbc.update(
                "UPDATE transaccion_almacen SET request_hash = ? WHERE transaccion_id = ?", hash, segunda));
        assertRestriccion("chk_transaccion_almacen_request_hash", () -> jdbc.update(
                "UPDATE transaccion_almacen SET idempotency_key = ?, request_hash = ? WHERE transaccion_id = ?",
                UUID.randomUUID(), "hash-invalido", segunda));
    }

    @Test
    void nuevasVersionesDeRecetaYRutaNoReinterpretanLaOpEmitida() {
        RutaFixture ruta = crearRuta();
        Integer opId = tx.execute(status -> {
            Terminado terminado = new Terminado();
            terminado.setProductoId("TERMINADO-AREA-FIX");
            terminado.setNombre("Terminado fixture");
            terminado.setTipoUnidades("U");
            terminado.setCategoria(em.find(Categoria.class, 55));
            em.persist(terminado);
            ManufacturingVersions manufactura = new ManufacturingVersions();
            manufactura.setProducto(terminado);
            manufactura.setVersionNumber(1);
            manufactura.setInsumosJson("[{\"productoId\":\"" + ruta.semiId()
                    + "\",\"cantidadRequerida\":1}]");
            em.persist(manufactura);
            OrdenProduccion op = new OrdenProduccion(terminado, "Fixture", 10);
            op.setManufacturingVersion(manufactura);
            op.setRutaProcesoCatVersion(em.find(RutaProcesoCatVersion.class, ruta.versionId()));
            snapshotService.congelarParaOrdenProduccion(op);
            em.persist(op);
            em.flush();
            return op.getOrdenId();
        });
        String original = jdbc.queryForObject(
                "SELECT requerimientos_materiales_json FROM ordenes_produccion WHERE orden_id = ?", String.class, opId);

        tx.executeWithoutResult(status -> {
            RutaProcesoCatVersion anterior = em.find(RutaProcesoCatVersion.class, ruta.versionId());
            anterior.setEstado(RutaProcesoCatVersion.Estado.RETIRADA);
            anterior.setVigenteHasta(LocalDateTime.of(2026, 11, 7, 0, 0));
            AreaOperativa nuevaArea = new AreaOperativa();
            nuevaArea.setNombre("Otra etapa nueva");
            em.persist(nuevaArea);
            RutaProcesoCatVersion nuevaRuta = new RutaProcesoCatVersion();
            nuevaRuta.setRutaProcesoCat(anterior.getRutaProcesoCat());
            nuevaRuta.setVersionNumber(2);
            nuevaRuta.setEstado(RutaProcesoCatVersion.Estado.VIGENTE);
            nuevaRuta.setVigenteDesde(LocalDateTime.of(2026, 11, 7, 0, 0));
            nuevaRuta.setCreadoEn(LocalDateTime.of(2026, 11, 7, 0, 0));
            em.persist(nuevaRuta);
            RutaProcesoNode nuevoNodo = new RutaProcesoNode();
            nuevoNodo.setRutaProcesoCatVersion(nuevaRuta);
            nuevoNodo.setAreaOperativa(nuevaArea);
            nuevoNodo.setFrontendId("nueva-etapa");
            nuevoNodo.getSemiterminadosConsumidos().add(em.find(SemiTerminado.class, ruta.semiId()));
            em.persist(nuevoNodo);
            ManufacturingVersions nuevaReceta = new ManufacturingVersions();
            nuevaReceta.setProducto(em.find(Terminado.class, "TERMINADO-AREA-FIX"));
            nuevaReceta.setVersionNumber(2);
            nuevaReceta.setInsumosJson("[{\"productoId\":\"" + ruta.semiId()
                    + "\",\"cantidadRequerida\":3}]");
            em.persist(nuevaReceta);
        });

        tx.executeWithoutResult(status -> {
            OrdenProduccion op = em.find(OrdenProduccion.class, opId);
            assertEquals(original, op.getRequerimientosMaterialesJson());
            var requerimientos = snapshotService.requerimientosEtapa(op, ruta.nodoId());
            assertEquals(1, requerimientos.size());
            assertEquals(0, new BigDecimal("10").compareTo(requerimientos.getFirst().cantidad()));
            assertEquals(ruta.areaId(), requerimientos.getFirst().areaOperativaId());
            assertEquals("KG", requerimientos.getFirst().unidadMedida());
            assertTrue(snapshotService.requerimientosAlmacen(op).isEmpty(),
                    "El mismo semiterminado no debe reaparecer en dispensacion general");
            assertThrows(IllegalStateException.class, () -> snapshotService.congelarParaOrdenProduccion(op));
        });
    }

    @Test
    void consumoRegistraUnaSolaSalidaYElReintentoNoDuplicaMovimientos() {
        ConsumoFixture fixture = prepararConsumo(1, 10);
        RegistroRequest solicitud = solicitud(fixture, "4");

        RegistroResponse primera = consumoService.registrar(fixture.ordenes().getFirst(),
                fixture.seguimientos().getFirst(), solicitud, fixture.actor());
        // Cantidades semanticamente iguales producen el mismo hash canonico.
        solicitud.getLotes().getFirst().setCantidad("4.0000");
        RegistroResponse reintento = consumoService.registrar(fixture.ordenes().getFirst(),
                fixture.seguimientos().getFirst(), solicitud, fixture.actor());

        assertFalse(primera.reutilizado());
        assertTrue(reintento.reutilizado());
        assertEquals(primera.transaccionId(), reintento.transaccionId());
        assertEquals("6.0000", reintento.contexto().requerimientos().getFirst().cantidadPendiente());
        assertEquals(1, reintento.contexto().registros().size());
        assertEquals(1, contarDispensaciones());
        assertEquals(2, contar("movimientos"), "Ingreso inicial y una sola salida fisica");
        assertEquals(6.0, saldo(fixture.loteId()), 0.000001);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM movimientos m JOIN transaccion_almacen t "
                + "ON t.transaccion_id=m.transaccion_id WHERE t.transaccion_id = ? AND m.afecta_inventario = true "
                + "AND m.area_operativa_id = ? AND m.lote_id = ? AND m.cantidad = -4", Integer.class,
                primera.transaccionId(), fixture.ruta().areaId(), fixture.loteId()));
        verify(projectionQueueService, times(1)).solicitarActualizacion(any(OrdenProduccion.class));
    }

    @Test
    void mismaClaveConOtroContenidoSeRechazaSinOtraSalida() {
        ConsumoFixture fixture = prepararConsumo(1, 10);
        RegistroRequest solicitud = solicitud(fixture, "4");
        consumoService.registrar(fixture.ordenes().getFirst(), fixture.seguimientos().getFirst(), solicitud, fixture.actor());
        solicitud.getLotes().getFirst().setCantidad("3");

        assertThrows(IllegalStateException.class, () -> consumoService.registrar(
                fixture.ordenes().getFirst(), fixture.seguimientos().getFirst(), solicitud, fixture.actor()));

        assertEquals(1, contarDispensaciones());
        assertEquals(6.0, saldo(fixture.loteId()), 0.000001);
    }

    @Test
    void errorPosteriorAlFlushRevierteSalidaEstadoYClaveDeIdempotencia() {
        ConsumoFixture fixture = prepararConsumo(1, 10);
        RegistroRequest solicitud = solicitud(fixture, "4");
        doThrow(new IllegalStateException("Fallo de proyeccion simulado"))
                .when(projectionQueueService).solicitarActualizacion(any(OrdenProduccion.class));

        assertThrows(IllegalStateException.class, () -> consumoService.registrar(
                fixture.ordenes().getFirst(), fixture.seguimientos().getFirst(), solicitud, fixture.actor()));

        assertEquals(0, contarDispensaciones());
        assertEquals(1, contar("movimientos"));
        assertEquals(10.0, saldo(fixture.loteId()), 0.000001);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM transaccion_almacen WHERE idempotency_key = ?",
                Integer.class, solicitud.getIdempotencyKey()));
        assertEquals(0, jdbc.queryForObject("SELECT estado_orden FROM ordenes_produccion WHERE orden_id = ?",
                Integer.class, fixture.ordenes().getFirst()));
    }

    @Test
    void dosOpConcurrentesNoPuedenSobregirarElMismoLote() throws Exception {
        ConsumoFixture fixture = prepararConsumo(2, 10);
        CyclicBarrier inicio = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<ResultadoRegistro>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int indice = i;
                futuros.add(executor.submit(() -> {
                    inicio.await(5, TimeUnit.SECONDS);
                    try {
                        consumoService.registrar(fixture.ordenes().get(indice), fixture.seguimientos().get(indice),
                                solicitud(fixture, "8"), fixture.actor());
                        return new ResultadoRegistro(true, null);
                    } catch (IllegalStateException error) {
                        return new ResultadoRegistro(false, error.getMessage());
                    }
                }));
            }
            List<ResultadoRegistro> resultados = new ArrayList<>();
            for (var futuro : futuros) resultados.add(futuro.get(15, TimeUnit.SECONDS));
            assertEquals(1, resultados.stream().filter(ResultadoRegistro::exitoso).count());
            assertTrue(resultados.stream().anyMatch(resultado -> !resultado.exitoso()
                    && resultado.error() != null && resultado.error().contains("Saldo insuficiente")));
        }

        assertEquals(1, contarDispensaciones());
        assertEquals(2, contar("movimientos"));
        assertEquals(2.0, saldo(fixture.loteId()), 0.000001);
    }

    private ConsumoFixture prepararConsumo(int numeroOrdenes, double stock) {
        RutaFixture ruta = crearRuta();
        return Objects.requireNonNull(tx.execute(status -> {
            User actor = new User();
            actor.setUsername("responsable-consumo-fixture");
            actor.setNombreCompleto("Responsable fixture");
            actor.setCedula(55L);
            em.persist(actor);
            AreaOperativa area = em.find(AreaOperativa.class, ruta.areaId());
            area.setResponsableArea(actor);
            Terminado terminado = new Terminado();
            terminado.setProductoId("TERMINADO-CONSUMO-FIX");
            terminado.setNombre("Terminado consumo fixture");
            terminado.setTipoUnidades("U");
            terminado.setCategoria(em.find(Categoria.class, 55));
            em.persist(terminado);
            ManufacturingVersions manufactura = new ManufacturingVersions();
            manufactura.setProducto(terminado);
            manufactura.setVersionNumber(1);
            manufactura.setInsumosJson("[{\"productoId\":\"" + ruta.semiId()
                    + "\",\"cantidadRequerida\":1}]");
            em.persist(manufactura);
            SemiTerminado semi = em.find(SemiTerminado.class, ruta.semiId());
            Lote lote = new Lote();
            lote.setProducto(semi);
            lote.setBatchNumber("BASE-AREA-FIX-01");
            em.persist(lote);
            TransaccionAlmacen ingreso = new TransaccionAlmacen();
            ingreso.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.CM);
            ingreso.setIdEntidadCausante(1);
            Movimiento entrada = new Movimiento();
            entrada.setProducto(semi);
            entrada.setLote(lote);
            entrada.setCantidad(stock);
            entrada.setTipoMovimiento(Movimiento.TipoMovimiento.AJUSTE_POSITIVO);
            entrada.setAlmacen(Movimiento.Almacen.GENERAL);
            entrada.setTransaccionAlmacen(ingreso);
            ingreso.setMovimientosTransaccion(List.of(entrada));
            em.persist(ingreso);
            List<Integer> ordenes = new ArrayList<>();
            List<Long> seguimientos = new ArrayList<>();
            for (int i = 0; i < numeroOrdenes; i++) {
                OrdenProduccion op = new OrdenProduccion(terminado, "Fixture " + i, 10);
                op.setManufacturingVersion(manufactura);
                op.setRutaProcesoCatVersion(em.find(RutaProcesoCatVersion.class, ruta.versionId()));
                snapshotService.congelarParaOrdenProduccion(op);
                em.persist(op);
                SeguimientoOrdenArea seguimiento = new SeguimientoOrdenArea();
                seguimiento.setOrdenProduccion(op);
                seguimiento.setRutaProcesoNode(em.find(RutaProcesoNode.class, ruta.nodoId()));
                seguimiento.setAreaOperativa(area);
                seguimiento.setEstado(SeguimientoOrdenArea.ESTADO_EN_PROCESO);
                seguimiento.setFechaEstadoActual(LocalDateTime.now(CLOCK));
                em.persist(seguimiento);
                ordenes.add(op.getOrdenId());
                seguimientos.add(seguimiento.getId());
            }
            em.flush();
            return new ConsumoFixture(ruta, actor, lote.getId(), ordenes, seguimientos);
        }));
    }

    private RegistroRequest solicitud(ConsumoFixture fixture, String cantidad) {
        LoteRequest lote = new LoteRequest();
        lote.setProductoId(fixture.ruta().semiId());
        lote.setLoteId(fixture.loteId());
        lote.setCantidad(cantidad);
        RegistroRequest solicitud = new RegistroRequest();
        solicitud.setIdempotencyKey(UUID.randomUUID());
        solicitud.setLotes(List.of(lote));
        return solicitud;
    }

    private long contarDispensaciones() {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT count(*) FROM transaccion_almacen WHERE tipo_entidad_causante = ?", Long.class,
                TransaccionAlmacen.TipoEntidadCausante.OD.ordinal()));
    }

    private double saldo(Long loteId) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM movimientos "
                + "WHERE lote_id = ? AND afecta_inventario = true AND almacen = ?", Double.class,
                loteId, Movimiento.Almacen.GENERAL.ordinal()));
    }

    private RutaFixture crearRuta() {
        return Objects.requireNonNull(tx.execute(status -> {
            Categoria categoria = new Categoria();
            categoria.setCategoriaId(55);
            categoria.setCategoriaNombre("Categoria fixture");
            em.persist(categoria);
            AreaOperativa area = new AreaOperativa();
            area.setNombre("Mezcla fixture");
            em.persist(area);
            SemiTerminado semi = new SemiTerminado();
            semi.setProductoId("SEMI-AREA-FIX");
            semi.setNombre("Base neutra fixture");
            semi.setTipoUnidades("KG");
            semi.setInventareable(true);
            semi.setRequiereOrdenFabricacion(true);
            em.persist(semi);
            RutaProcesoCat ruta = new RutaProcesoCat();
            ruta.setCategoria(categoria);
            em.persist(ruta);
            RutaProcesoCatVersion version = new RutaProcesoCatVersion();
            version.setRutaProcesoCat(ruta);
            version.setVersionNumber(1);
            version.setEstado(RutaProcesoCatVersion.Estado.VIGENTE);
            version.setVigenteDesde(LocalDateTime.of(2026, 11, 6, 0, 0));
            version.setCreadoEn(LocalDateTime.of(2026, 11, 6, 0, 0));
            em.persist(version);
            RutaProcesoNode nodo = new RutaProcesoNode();
            nodo.setRutaProcesoCatVersion(version);
            nodo.setFrontendId("mezcla");
            nodo.setAreaOperativa(area);
            nodo.getSemiterminadosConsumidos().add(semi);
            em.persist(nodo);
            em.flush();
            return new RutaFixture(nodo.getId(), version.getId(), area.getAreaId(), semi.getProductoId());
        }));
    }

    private int crearTransaccion() {
        return Objects.requireNonNull(tx.execute(status -> {
            TransaccionAlmacen transaccion = new TransaccionAlmacen();
            transaccion.setTipoEntidadCausante(TransaccionAlmacen.TipoEntidadCausante.OD);
            transaccion.setIdEntidadCausante(1);
            em.persist(transaccion);
            em.flush();
            return transaccion.getTransaccionId();
        }));
    }

    private long contar(String tabla) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM " + tabla, Long.class));
    }

    private void assertRestriccion(String nombre, Runnable accion) {
        DataIntegrityViolationException error = assertThrows(DataIntegrityViolationException.class, accion::run);
        assertTrue(Objects.requireNonNull(error.getMostSpecificCause().getMessage()).contains(nombre));
    }

    @SuppressWarnings("unchecked")
    private <T> T transaccional(T target) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    private record RutaFixture(Long nodoId, Long versionId, int areaId, String semiId) {}
    private record ConsumoFixture(RutaFixture ruta, User actor, Long loteId,
                                  List<Integer> ordenes, List<Long> seguimientos) {}
    private record ResultadoRegistro(boolean exitoso, String error) {}
}
