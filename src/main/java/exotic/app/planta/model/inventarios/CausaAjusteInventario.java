package exotic.app.planta.model.inventarios;

public enum CausaAjusteInventario {
    PRODUCCION_CONTINGENCIA(
            "Salida de producción por contingencia",
            true,
            true,
            true),
    DIFERENCIA_CONTEO(
            "Diferencia de conteo físico",
            false,
            false,
            false),
    MERMA_DANO_PERDIDA(
            "Merma, daño o pérdida",
            true,
            false,
            false),
    CORRECCION_REGISTRO(
            "Corrección de registro",
            false,
            false,
            false),
    OTRA_REGULARIZACION(
            "Otra regularización excepcional",
            false,
            true,
            false);

    private final String etiqueta;
    private final boolean soloSalida;
    private final boolean requiereObservaciones;
    private final boolean elegibleComoDemanda;

    CausaAjusteInventario(
            String etiqueta,
            boolean soloSalida,
            boolean requiereObservaciones,
            boolean elegibleComoDemanda
    ) {
        this.etiqueta = etiqueta;
        this.soloSalida = soloSalida;
        this.requiereObservaciones = requiereObservaciones;
        this.elegibleComoDemanda = elegibleComoDemanda;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean isSoloSalida() {
        return soloSalida;
    }

    public boolean isRequiereObservaciones() {
        return requiereObservaciones;
    }

    public boolean isElegibleComoDemanda() {
        return elegibleComoDemanda;
    }
}
