package exotic.app.planta.model.inventarios;

/**
 * Disposición de calidad vigente de un lote. SIN_CLASIFICAR permite migrar el
 * histórico sin atribuir retrospectivamente una aprobación que no fue
 * registrada en el sistema.
 */
public enum EstadoCalidadLote {
    SIN_CLASIFICAR,
    CUARENTENA,
    APROBADO,
    LIBERADO,
    RECHAZADO,
    BLOQUEADO,
    /** Lote intermedio cuya liberacion formal no aplica por decision de negocio. */
    NO_APLICA_CALIDAD
}
