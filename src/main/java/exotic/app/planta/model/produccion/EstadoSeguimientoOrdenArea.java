package exotic.app.planta.model.produccion;

public enum EstadoSeguimientoOrdenArea {
    COLA(SeguimientoOrdenArea.ESTADO_COLA, "Cola"),
    ESPERA(SeguimientoOrdenArea.ESTADO_ESPERA, "Espera"),
    COMPLETADO(SeguimientoOrdenArea.ESTADO_COMPLETADO, "Completado"),
    OMITIDO(SeguimientoOrdenArea.ESTADO_OMITIDO, "Omitido"),
    EN_PROCESO(SeguimientoOrdenArea.ESTADO_EN_PROCESO, "En proceso");

    private final int code;
    private final String descripcion;

    EstadoSeguimientoOrdenArea(int code, String descripcion) {
        this.code = code;
        this.descripcion = descripcion;
    }

    public int getCode() {
        return code;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static EstadoSeguimientoOrdenArea fromCode(int code) {
        for (EstadoSeguimientoOrdenArea value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Estado de seguimiento desconocido: " + code);
    }
}
