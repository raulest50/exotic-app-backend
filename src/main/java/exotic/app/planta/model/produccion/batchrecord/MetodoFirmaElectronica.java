package exotic.app.planta.model.produccion.batchrecord;

/**
 * Método utilizado para autenticar al firmante. SESION_AUTENTICADA permite
 * representar el cierre operativo actual; las decisiones de revisión o
 * liberación podrán exigir reautenticación o segundo factor en el servicio.
 */
public enum MetodoFirmaElectronica {
    SESION_AUTENTICADA,
    REAUTENTICACION_CREDENCIALES,
    SEGUNDO_FACTOR
}
