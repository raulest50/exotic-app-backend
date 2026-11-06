package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.produccion.dto.MpsFabricacionDTOs.ProgramaResponse;
import exotic.app.planta.model.users.*;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.AreaMpsConsultaService;
import exotic.app.planta.service.produccion.MpsFabricacionService;
import exotic.app.planta.service.produccion.MpsFabricacionAprobacionService;
import exotic.app.planta.service.produccion.MpsFabricacionObservacionService;
import exotic.app.planta.service.produccion.MpsFabricacionEmisionService;
import exotic.app.planta.service.produccion.MpsFabricacionAprobadoEditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MpsFabricacionResourceTest {
    @Mock private MpsFabricacionService service;
    @Mock private AreaMpsConsultaService areaService;
    @Mock private UserRepository userRepo;
    @Mock private MpsFabricacionAprobacionService aprobacionService;
    @Mock private MpsFabricacionObservacionService observacionService;
    @Mock private MpsFabricacionEmisionService emisionService;
    @Mock private MpsFabricacionAprobadoEditService editService;
    @InjectMocks private MpsFabricacionResource resource;
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("planificador", "n/a", List.of());

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(resource)
                .setControllerAdvice(new MpsFabricacionExceptionHandler()).build();
    }

    @Test
    void permisoProgramacionNivelUnoConsultaYGuardaComoMpsOp() throws Exception {
        planner("PROGRAMACION_MPS_OF", 1);
        when(service.consultar(LocalDate.of(2026, 9, 28), null)).thenReturn(new ProgramaResponse());
        mvc.perform(get("/api/produccion/mps-of").param("weekStartDate", "2026-09-28").principal(auth))
                .andExpect(status().isOk());
        mvc.perform(put("/api/produccion/mps-of/2026-09-28").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":null,\"propuestas\":[]}"))
                .andExpect(status().isOk());
        verify(service).guardar(any(), any(), any());
    }

    @Test
    void permisoMainNoConcedeAccesoImplicitoAOf() throws Exception {
        planner("MAIN", 3);
        mvc.perform(get("/api/produccion/mps-of").param("weekStartDate", "2026-09-28").principal(auth))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void permisoOfManualNoConcedeProgramacionOAprobacion() throws Exception {
        planner("CREAR_ORDEN_FABRICACION", 3);
        mvc.perform(get("/api/produccion/mps-of").param("weekStartDate", "2026-09-28").principal(auth))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void programadorNoPuedeAprobarNiEmitir() throws Exception {
        planner("PROGRAMACION_MPS_OF", 3);
        for (String accion : List.of("aprobar", "emitir")) {
            mvc.perform(post("/api/produccion/mps-of/2026-09-28/" + accion).principal(auth)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(aprobacionService, emisionService);
    }

    @Test
    void aprobadorNivelUnoPuedeEmitirSinPermisoManualNiProgramacion() throws Exception {
        planner("APROBACION_MPS_OF", 1);
        when(emisionService.emitir(eq(LocalDate.of(2026, 9, 28)), eq(3L), any())).thenReturn(new ProgramaResponse());
        mvc.perform(post("/api/produccion/mps-of/2026-09-28/emitir").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}"))
                .andExpect(status().isOk());
        verify(emisionService).emitir(eq(LocalDate.of(2026, 9, 28)), eq(3L), any());
    }

    @Test
    void aprobacionRequiereVersionEsperada() throws Exception {
        mvc.perform(post("/api/produccion/mps-of/2026-09-28/aprobar").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(aprobacionService);
    }

    @Test
    void nivelDosGuardaYConflictoDePrimeraCreacionDevuelve409() throws Exception {
        planner("PROGRAMACION_MPS_OF", 2);
        when(service.guardar(any(), any(), any())).thenThrow(new DataIntegrityViolationException("week unique"));
        mvc.perform(put("/api/produccion/mps-of/2026-09-28").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":null,\"propuestas\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "12.34567", "100000000000000"})
    void cantidadInvalidaSeRechazaAntesDelServicio(String cantidad) throws Exception {
        mvc.perform(put("/api/produccion/mps-of/2026-09-28").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"propuestas":[{"semiTerminadoId":"S1","cantidad":"%s",
                                 "fechaInicio":"2026-09-28T08:00:00","fechaFinal":"2026-09-28T16:00:00"}]}
                                """.formatted(cantidad)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void cantidadDecimalViaStringLlegaSinPerdidaAlServicio() throws Exception {
        planner("PROGRAMACION_MPS_OF", 2);
        mvc.perform(put("/api/produccion/mps-of/2026-09-28").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"propuestas":[{"semiTerminadoId":"S1","cantidad":"99999999999999.1234",
                                 "fechaInicio":"2026-09-28T08:00:00","fechaFinal":"2026-09-28T16:00:00"}]}
                                """))
                .andExpect(status().isOk());
        verify(service).guardar(any(), argThat(r -> r.getPropuestas().get(0).getCantidad().toPlainString()
                .equals("99999999999999.1234")), any());
    }

    @Test
    void rutaOperativaNoAdmiteGuardado() throws Exception {
        mvc.perform(put("/api/area-operativa-panel/mps-of").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"propuestas\":[]}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    private void planner(String tabId, int nivel) {
        var user = new User();
        user.setUsername("planificador");
        var modulo = new ModuloAcceso();
        modulo.setModulo(ModuloSistema.PRODUCCION);
        var tab = new TabAcceso();
        tab.setTabId(tabId);
        tab.setNivel(nivel);
        modulo.setTabs(Set.of(tab));
        user.setModuloAccesos(Set.of(modulo));
        when(userRepo.findByUsername("planificador")).thenReturn(Optional.of(user));
    }
}
