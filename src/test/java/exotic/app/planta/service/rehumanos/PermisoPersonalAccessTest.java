package exotic.app.planta.service.rehumanos;

import exotic.app.planta.model.organizacion.personal.IntegrantePersonal;
import exotic.app.planta.model.organizacion.personal.dto.PermisoPersonalDTOs.*;
import exotic.app.planta.model.organizacion.personal.permiso.SolicitudPermisoPersonal;
import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.organigrama.CargoOrganigramaRepo;
import exotic.app.planta.repo.personal.*;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.users.FirmaVisualUsuarioService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

class PermisoPersonalAccessTest {
    private SolicitudPermisoPersonalRepo solicitudes;
    private IntegrantePersonalRepo empleados;
    private PermisoPersonalService service;

    @BeforeEach
    void preparar() {
        solicitudes = mock(SolicitudPermisoPersonalRepo.class);
        empleados = mock(IntegrantePersonalRepo.class);
        service = new PermisoPersonalService(solicitudes, mock(FirmaPermisoPersonalRepo.class),
                mock(EventoPermisoPersonalRepo.class), mock(ConfiguracionPermisoPersonalRepo.class), empleados,
                mock(UserRepository.class), mock(CargoOrganigramaRepo.class),
                mock(FirmaVisualUsuarioService.class), mock(EntityManager.class));
    }

    @Test
    void permisosAltosNoHabilitanAprobacionNiAdministracion() {
        User actor = usuario(1, 4, 0);
        Contexto contexto = service.contexto(actor);
        assertTrue(contexto.puedeSolicitar());
        assertFalse(contexto.puedeRevisar());
        assertFalse(contexto.puedeConsultarTodas());
        assertFalse(contexto.puedeConfigurar());
        assertEquals(0, contexto.nivelAprobaciones());
        assertProhibido(() -> service.decidir(10L, new DecidirRequest(0L, Decision.APROBAR, null, true, null, null), actor));
        assertProhibido(() -> service.configuracion(actor));
        assertProhibido(() -> service.listar(Scope.POR_REVISAR, null, null, null, null, 0, 10, actor));
        assertProhibido(() -> service.listar(Scope.TODAS, null, null, null, null, 0, 10, actor));
        verifyNoInteractions(solicitudes);
    }

    @Test
    void aprobadorNoNecesitaIdentidadDeTrabajadorNiPuedeCrearSolicitudes() {
        User actor = usuario(2, 0, 2);
        Contexto contexto = service.contexto(actor);
        assertTrue(contexto.puedeRevisar());
        assertFalse(contexto.puedeSolicitar());
        assertFalse(contexto.habilitado());
        assertTrue(contexto.impedimentos().isEmpty());
        assertNull(contexto.empleado());
        assertProhibido(() -> service.crear(null, actor));
        assertProhibido(() -> service.listar(Scope.MIAS, null, null, null, null, 0, 10, actor));
        verifyNoInteractions(empleados, solicitudes);
    }

    @Test
    void consultaDeAprobacionesNoPermiteDecidir() {
        User actor = usuario(2, 0, 1);
        assertProhibido(() -> service.decidir(10L, new DecidirRequest(0L, Decision.RECHAZAR, "Motivo", null, null, null), actor));
        verifyNoInteractions(solicitudes);
    }

    @Test
    void jefeAsignadoPuedeConsultarYConservarAccesoTrasResolverSinPermisosDeSolicitud() {
        User actor = usuario(2, 0, 2);
        SolicitudPermisoPersonal solicitud = expediente(usuario(1, 1, 0), actor, Estado.SOLICITADA);
        when(solicitudes.findById(10L)).thenReturn(Optional.of(solicitud));
        Detalle pendiente = service.detalle(10L, actor);
        assertTrue(pendiente.puedeDecidir());
        assertFalse(pendiente.puedeEditar());
        assertFalse(pendiente.puedePresentar());
        solicitud.setEstado(Estado.APROBADA);
        assertFalse(service.detalle(10L, actor).puedeDecidir());
    }

    @Test
    void otroJefeNoPuedeLeerNiResolverUnExpedienteAjeno() {
        User actor = usuario(3, 0, 2);
        SolicitudPermisoPersonal solicitud = expediente(usuario(1, 1, 0), usuario(2, 0, 2), Estado.SOLICITADA);
        when(solicitudes.findById(10L)).thenReturn(Optional.of(solicitud));
        when(solicitudes.findByIdForUpdate(10L)).thenReturn(Optional.of(solicitud));
        assertProhibido(() -> service.detalle(10L, actor));
        assertProhibido(() -> service.decidir(10L, new DecidirRequest(0L, Decision.APROBAR, null, true, null, null), actor));
    }

    @Test
    void nadiePuedeResolverSuPropiaSolicitudInclusoConExcepcionAdministrativa() {
        User actor = usuario(1, 1, 4);
        SolicitudPermisoPersonal solicitud = expediente(actor, usuario(2, 0, 2), Estado.SOLICITADA);
        when(solicitudes.findByIdForUpdate(10L)).thenReturn(Optional.of(solicitud));
        for (String nombre : new String[]{"usuario_1", "master", "super_master"}) {
            actor.setUsername(nombre);
            assertProhibido(() -> service.decidir(10L, new DecidirRequest(0L, Decision.APROBAR, null, true, null, null), actor));
            assertProhibido(() -> service.decidir(10L, new DecidirRequest(0L, Decision.RECHAZAR, "Motivo", null, null, null), actor));
        }
    }

    @Test
    void aprobacionesAdministrativasNoSustituyenPermisoDeEditarOFirmarUnBorradorPropio() {
        User actor = usuario(1, 0, 3);
        SolicitudPermisoPersonal solicitud = expediente(actor, usuario(2, 0, 2), Estado.BORRADOR);
        when(solicitudes.findById(10L)).thenReturn(Optional.of(solicitud));
        when(solicitudes.findByIdForUpdate(10L)).thenReturn(Optional.of(solicitud));
        Detalle detalle = service.detalle(10L, actor);
        assertFalse(detalle.puedeEditar());
        assertFalse(detalle.puedePresentar());
        assertFalse(detalle.puedeAnular());
        assertProhibido(() -> service.guardar(10L, new Borrador(0L, null, null, null, null, null, null, null, null, null), actor));
        assertProhibido(() -> service.presentar(10L, new PresentarRequest(0L, true, null, null), actor));
        assertProhibido(() -> service.anular(10L, new AnularRequest(0L, "Retiro"), actor));
        assertProhibido(() -> service.guardarSoporte(10L, 0L, null, actor));
        assertProhibido(() -> service.borrarSoporte(10L, 0L, actor));
    }

    @Test
    void solicitanteNoPuedeLeerNiDescargarExpedientesDeOtroTrabajador() {
        User actor = usuario(3, 4, 0);
        when(solicitudes.findById(10L)).thenReturn(Optional.of(
                expediente(usuario(1, 1, 0), usuario(2, 0, 2), Estado.SOLICITADA)));
        assertProhibido(() -> service.detalle(10L, actor));
        assertProhibido(() -> service.documentoPdf(10L, actor));
        assertProhibido(() -> service.soporte(10L, actor));
    }

    private static void assertProhibido(Runnable accion) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(FORBIDDEN, error.getStatusCode());
    }

    private static User usuario(long id, int nivelPermisos, int nivelAprobaciones) {
        User user = new User();
        user.setId(id); user.setUsername("usuario_" + id); user.setNombreCompleto("Usuario " + id); user.setEstado(1);
        ModuloAcceso modulo = new ModuloAcceso();
        modulo.setUser(user); modulo.setModulo(ModuloSistema.PERSONAL_PLANTA); modulo.setTabs(new HashSet<>());
        for (String tab : new String[]{"PERMISOS", "APROBACIONES"}) {
            int nivel = tab.equals("PERMISOS") ? nivelPermisos : nivelAprobaciones;
            if (nivel == 0) continue;
            TabAcceso acceso = new TabAcceso();
            acceso.setModuloAcceso(modulo); acceso.setTabId(tab); acceso.setNivel(nivel);
            modulo.getTabs().add(acceso);
        }
        user.setModuloAccesos(new HashSet<>()); user.getModuloAccesos().add(modulo);
        return user;
    }

    private static SolicitudPermisoPersonal expediente(User solicitante, User jefe, Estado estado) {
        IntegrantePersonal empleado = new IntegrantePersonal(); empleado.setId(1000);
        SolicitudPermisoPersonal solicitud = new SolicitudPermisoPersonal();
        solicitud.setId(10L); solicitud.setVersion(0L); solicitud.setEstado(estado);
        solicitud.setEmpleado(empleado); solicitud.setEmpleadoNombre("Trabajador de muestra");
        solicitud.setSolicitante(solicitante); solicitud.setJefe(jefe); solicitud.setJefeNombre(jefe.getNombreCompleto());
        solicitud.setJefeUsername(jefe.getUsername()); solicitud.setFechaSolicitud(LocalDate.of(2026, 11, 19));
        return solicitud;
    }
}
