package exotic.app.planta.model.compras.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateEstadoOrdenCompraRequest {

    /**
     * -1: cancelada
     * 0: pendiente liberacion
     * 1: pendiente envio proveedor
     * 2: pendiente recepcion almacen
     * 3: cerrada exitosamente
     */
    private int newEstado;

    /**
     * solo se usa si se la orden esta en estado 1. de lo contrario
     * este atributo nunca se usa
     */
    private TipoEnvio tipoEnvio;

    /**
     * Version de identidad legal usada para generar y fijar el cabezote del PDF OCM.
     * Si no se informa durante la transicion 1 -> 2, backend usa la version vigente.
     */
    private Long empresaIdentidadLegalVersionId;

    /**
     * Version de logo documental usada para generar y fijar el PDF OCM.
     * Si no se informa durante la transicion 1 -> 2, backend usa la version vigente.
     */
    private Long empresaLogoDocumentalVersionId;

    /**
     * solo se usa si se la orden esta en estado 1. de lo contrario
     * este atributo nunca se usa, al igaul que sucede con el atributo anterior.
     */
    private MultipartFile OCMpdf;

    /**
     * la orden de compra se envia
     */
    public enum TipoEnvio{
        MANUAL,
        EMAIL,
        WHATSAPP,
    }

}
