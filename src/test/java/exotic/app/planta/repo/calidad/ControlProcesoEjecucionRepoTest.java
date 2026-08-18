package exotic.app.planta.repo.calidad;

import exotic.app.planta.model.calidad.ControlProcesoEjecucion;
import exotic.app.planta.model.calidad.ControlProcesoPlantilla;
import exotic.app.planta.model.calidad.EstadoControlProcesoPlantilla;
import exotic.app.planta.model.inventarios.Lote;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.OrdenProduccion;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.users.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class ControlProcesoEjecucionRepoTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("control_proceso_ejecucion_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ControlProcesoEjecucionRepo ejecucionRepo;

    @Autowired
    private EntityManager entityManager;

    private Integer areaAlfaId;
    private Long loteAlfaId;
    private Long ejecucionAlfaId;
    private Long ejecucionBetaId;
    private Long ejecucionGammaId;

    @BeforeEach
    void persistirEscenario() {
        User usuario = persistirUsuario();
        AreaOperativa areaAlfa = persistirArea("Area alfa");
        AreaOperativa areaBeta = persistirArea("Area beta");

        Terminado productoAlfa = persistirProducto("PT-ALFA", "Producto Alfa");
        Terminado productoBeta = persistirProducto("PT-BETA", "Producto Beta");
        Terminado productoGamma = persistirProducto("PT-GAMMA", "Producto Gamma");

        Lote loteAlfa = persistirLote("LOTE-ALFA", productoAlfa);
        Lote loteBeta = persistirLote("LOTE-BETA", productoBeta);
        Lote loteGamma = persistirLote("LOTE-GAMMA", productoGamma);

        ControlProcesoPlantilla plantillaAlfa = persistirPlantilla(areaAlfa);
        ControlProcesoPlantilla plantillaBeta = persistirPlantilla(areaBeta);

        ControlProcesoEjecucion ejecucionAlfa = persistirEjecucion(
                plantillaAlfa,
                loteAlfa,
                usuario,
                LocalDateTime.of(2026, 8, 18, 8, 15));
        ControlProcesoEjecucion ejecucionBeta = persistirEjecucion(
                plantillaBeta,
                loteBeta,
                usuario,
                LocalDateTime.of(2026, 8, 19, 23, 59, 59, 999_999_000));
        ControlProcesoEjecucion ejecucionGamma = persistirEjecucion(
                plantillaAlfa,
                loteGamma,
                usuario,
                LocalDateTime.of(2026, 8, 20, 12, 30));

        areaAlfaId = areaAlfa.getAreaId();
        loteAlfaId = loteAlfa.getId();
        ejecucionAlfaId = ejecucionAlfa.getId();
        ejecucionBetaId = ejecucionBeta.getId();
        ejecucionGammaId = ejecucionGamma.getId();
        entityManager.clear();
    }

    @Test
    void buscaSinFiltrosYOrdenaPorFechaDescendente() {
        Page<ControlProcesoEjecucion> resultado = buscar(null, null, null, null, null, 0, 20);

        assertThat(ids(resultado)).containsExactly(
                ejecucionGammaId,
                ejecucionBetaId,
                ejecucionAlfaId);
        assertThat(resultado.getTotalElements()).isEqualTo(3);
    }

    @Test
    void filtraProductoPorCodigoONombreSinDistinguirMayusculas() {
        Page<ControlProcesoEjecucion> porCodigo = buscar(
                null, null, "pt-ALFA", null, null, 0, 20);
        Page<ControlProcesoEjecucion> porNombre = buscar(
                null, null, "PRODUCTO beta", null, null, 0, 20);

        assertThat(ids(porCodigo)).containsExactly(ejecucionAlfaId);
        assertThat(ids(porNombre)).containsExactly(ejecucionBetaId);
    }

    @Test
    void filtraIndependientementePorAreaYLote() {
        Page<ControlProcesoEjecucion> porArea = buscar(
                areaAlfaId, null, null, null, null, 0, 20);
        Page<ControlProcesoEjecucion> porLote = buscar(
                null, loteAlfaId, null, null, null, 0, 20);

        assertThat(ids(porArea)).containsExactly(ejecucionGammaId, ejecucionAlfaId);
        assertThat(ids(porLote)).containsExactly(ejecucionAlfaId);
    }

    @Test
    void filtraCadaLimiteDeFechaDeFormaIndependiente() {
        Page<ControlProcesoEjecucion> desde = buscar(
                null,
                null,
                null,
                LocalDate.of(2026, 8, 19).atStartOfDay(),
                null,
                0,
                20);
        Page<ControlProcesoEjecucion> hasta = buscar(
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 8, 19).atTime(LocalTime.MAX),
                0,
                20);

        assertThat(ids(desde)).containsExactly(ejecucionGammaId, ejecucionBetaId);
        assertThat(ids(hasta)).containsExactly(ejecucionBetaId, ejecucionAlfaId);
    }

    @Test
    void filtraRangoEIncluyeElUltimoInstanteDelDia() {
        Page<ControlProcesoEjecucion> resultado = buscar(
                null,
                null,
                null,
                LocalDate.of(2026, 8, 19).atStartOfDay(),
                LocalDate.of(2026, 8, 19).atTime(LocalTime.MAX),
                0,
                20);

        assertThat(ids(resultado)).containsExactly(ejecucionBetaId);
    }

    @Test
    void combinaFiltrosSinCoincidencias() {
        Page<ControlProcesoEjecucion> resultado = buscar(
                areaAlfaId, null, "producto beta", null, null, 0, 20);

        assertThat(resultado).isEmpty();
    }

    @Test
    void paginaResultadosConElOrdenSolicitado() {
        Page<ControlProcesoEjecucion> primeraPagina = buscar(
                null, null, null, null, null, 0, 2);
        Page<ControlProcesoEjecucion> segundaPagina = buscar(
                null, null, null, null, null, 1, 2);

        assertThat(ids(primeraPagina)).containsExactly(ejecucionGammaId, ejecucionBetaId);
        assertThat(ids(segundaPagina)).containsExactly(ejecucionAlfaId);
        assertThat(primeraPagina.getTotalPages()).isEqualTo(2);
    }

    private Page<ControlProcesoEjecucion> buscar(
            Integer areaId,
            Long loteId,
            String producto,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            int page,
            int size
    ) {
        return ejecucionRepo.findAll(
                ControlProcesoEjecucionSpecifications.conFiltros(
                        areaId, loteId, producto, fechaDesde, fechaHasta),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fechaRegistro")));
    }

    private List<Long> ids(Page<ControlProcesoEjecucion> resultado) {
        return resultado.getContent().stream()
                .map(ControlProcesoEjecucion::getId)
                .toList();
    }

    private User persistirUsuario() {
        User usuario = User.builder()
                .cedula(1_001L)
                .username("calidad-test")
                .nombreCompleto("Usuario Calidad")
                .password("test")
                .email("calidad-test@example.com")
                .estado(1)
                .build();
        entityManager.persist(usuario);
        return usuario;
    }

    private AreaOperativa persistirArea(String nombre) {
        AreaOperativa area = new AreaOperativa();
        area.setNombre(nombre);
        entityManager.persist(area);
        return area;
    }

    private Terminado persistirProducto(String productoId, String nombre) {
        Terminado producto = new Terminado();
        producto.setProductoId(productoId);
        producto.setNombre(nombre);
        producto.setTipoUnidades("U");
        entityManager.persist(producto);
        return producto;
    }

    private Lote persistirLote(String batchNumber, Terminado producto) {
        OrdenProduccion orden = new OrdenProduccion();
        orden.setLoteAsignado(batchNumber);
        orden.setProducto(producto);
        entityManager.persist(orden);

        Lote lote = new Lote();
        lote.setBatchNumber(batchNumber);
        lote.setProductionDate(LocalDate.of(2026, 8, 18));
        lote.setProducto(producto);
        lote.setOrdenProduccion(orden);
        entityManager.persist(lote);
        return lote;
    }

    private ControlProcesoPlantilla persistirPlantilla(AreaOperativa area) {
        ControlProcesoPlantilla plantilla = new ControlProcesoPlantilla();
        plantilla.setAreaOperativa(area);
        plantilla.setVersion(1);
        plantilla.setEstado(EstadoControlProcesoPlantilla.VIGENTE);
        entityManager.persist(plantilla);
        return plantilla;
    }

    private ControlProcesoEjecucion persistirEjecucion(
            ControlProcesoPlantilla plantilla,
            Lote lote,
            User usuario,
            LocalDateTime fechaRegistro
    ) {
        ControlProcesoEjecucion ejecucion = new ControlProcesoEjecucion();
        ejecucion.setPlantilla(plantilla);
        ejecucion.setLote(lote);
        ejecucion.setUsuario(usuario);
        ejecucion.setFechaRegistro(fechaRegistro);
        entityManager.persist(ejecucion);
        entityManager.flush();
        return ejecucion;
    }
}
