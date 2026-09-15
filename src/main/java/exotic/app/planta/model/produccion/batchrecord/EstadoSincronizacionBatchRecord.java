package exotic.app.planta.model.produccion.batchrecord;

/** Estado técnico de la proyección documental; nunca gobierna el lote. */
public enum EstadoSincronizacionBatchRecord {
    PENDIENTE,
    SINCRONIZANDO,
    ACTUALIZADO,
    INCOMPLETO,
    ERROR
}
