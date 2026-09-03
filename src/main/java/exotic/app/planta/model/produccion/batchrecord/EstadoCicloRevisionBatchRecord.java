package exotic.app.planta.model.produccion.batchrecord;

public enum EstadoCicloRevisionBatchRecord {
    EN_REVISION,
    DEVUELTO_PRODUCCION,
    LIBERADO,
    RECHAZADO,
    /** Evidencia histórica preservada cuyo cierre no puede inferirse sin inventar datos. */
    MIGRADO_INCOMPLETO
}
