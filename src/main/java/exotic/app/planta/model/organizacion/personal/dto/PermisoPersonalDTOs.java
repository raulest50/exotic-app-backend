package exotic.app.planta.model.organizacion.personal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** HTTP contract for a personal leave file. Identities and permissions are always resolved by the server. */
public final class PermisoPersonalDTOs {
    private PermisoPersonalDTOs() { }

    public enum Estado { BORRADOR, SOLICITADA, APROBADA, RECHAZADA, ANULADA }
    public enum Motivo {
        CITA_TRATAMIENTO_MEDICO, CALAMIDAD_DOMESTICA, LICENCIA_REMUNERADA, LICENCIA_NO_REMUNERADA,
        ASUNTOS_PERSONALES, DILIGENCIAS_EMPRESA, ASUNTOS_ACADEMICOS, MOVILIDAD, DIA_FAMILIA, OTROS
    }
    public enum Unidad { HORAS, DIAS, MESES }
    public enum Scope { MIAS, POR_REVISAR, TODAS }
    public enum Decision { APROBAR, RECHAZAR }

    public record Usuario(Long id, String username, String nombre) { }
    public record Empleado(Long id, String nombre, String cargo, String proceso, Long usuarioId) { }
    public record Contexto(Long usuarioId, String usuarioNombre, int nivel, Empleado empleado, Usuario jefe,
                           boolean habilitado, List<String> impedimentos, boolean puedeRevisar,
                           boolean puedeConsultarTodas, boolean puedeConfigurar,
                           int nivelAprobaciones, boolean puedeSolicitar) { }
    public record ConfiguracionEmpleado(Empleado empleado, Usuario jefeConfigurado, Usuario jefeOrganigrama,
                                        Long version) { }
    public record Configuracion(List<ConfiguracionEmpleado> empleados, List<Usuario> usuarios) { }
    public record ConfigurarRequest(Long version, Long usuarioId, Long jefeUsuarioId, String motivo) { }
    public record Borrador(Long version, String proceso, Motivo motivo, String descripcion, String documentoSoporte,
                           String mecanismoRecuperacion, LocalDate fechaDesde, LocalDate fechaHasta,
                           BigDecimal cantidad, Unidad unidad) { }
    public record PresentarRequest(Long version, Boolean confirmacion, String imagenBase64, Long firmaVisualVersionId) { }
    public record DecidirRequest(Long version, Decision decision, String motivo, Boolean confirmacion,
                                 String imagenBase64, Long firmaVisualVersionId) { }
    public record AnularRequest(Long version, String motivo) { }
    public record FirmaVigente(Long id, String imagenBase64, String sha256) { }
    public record Soporte(String nombre, String contentType, long tamanoBytes, String sha256) { }
    public record Archivo(String nombre, String contentType, byte[] contenido) { }
    public record Firma(String rol, Long usuarioId, String username, String nombre, String cedula,
                         LocalDateTime firmadoEn, String hashContenido, String sha256, String imagenBase64) { }
    public record Evento(Long id, String tipo, Long actorId, String actorNombre, LocalDateTime creadoEn, String detalle) { }
    public record Resumen(Long id, long version, String folio, Estado estado, LocalDate fechaSolicitud,
                           String empleadoNombre, Long empleadoId, String jefeNombre, LocalDate fechaDesde,
                           LocalDate fechaHasta, BigDecimal cantidad, Unidad unidad) { }
    public record Detalle(Long id, long version, String folio, Estado estado, LocalDate fechaSolicitud,
                           Empleado empleado, Usuario jefe, String proceso, Motivo motivo, String descripcion,
                           String documentoSoporte, String mecanismoRecuperacion, LocalDate fechaDesde,
                           LocalDate fechaHasta, BigDecimal cantidad, Unidad unidad, Soporte soporte,
                           String hashContenido, LocalDateTime creadoEn, LocalDateTime presentadoEn,
                           LocalDateTime resueltoEn, String motivoDecision, String motivoAnulacion,
                           List<Firma> firmas, List<Evento> eventos, boolean puedeEditar, boolean puedePresentar,
                           boolean puedeDecidir, boolean puedeAnular) { }
    public record DocumentoPdf(Detalle detalle, byte[] firmaSolicitante, byte[] firmaJefe) { }
}
