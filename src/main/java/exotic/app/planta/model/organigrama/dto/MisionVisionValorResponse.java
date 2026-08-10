package exotic.app.planta.model.organigrama.dto;

import exotic.app.planta.model.organigrama.MisionVisionValor;

public record MisionVisionValorResponse(
        Long id,
        Integer orden,
        String titulo,
        String descripcionHtml
) {
    public static MisionVisionValorResponse fromEntity(MisionVisionValor valor) {
        return new MisionVisionValorResponse(
                valor.getId(),
                valor.getOrden(),
                valor.getTitulo(),
                valor.getDescripcionHtml()
        );
    }
}
