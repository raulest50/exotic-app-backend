package exotic.app.planta.model.activos.fijos.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateEstadoOrdenCompraAFRequest {

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
     * Pareja de versiones documentales usada para generar y fijar el PDF OCAF.
     * Los dos ids deben informarse juntos; si ambos faltan, backend usa la pareja
     * vigente para mantener compatibilidad con clientes anteriores.
     */
    private Long empresaIdentidadLegalVersionId;

    private Long empresaLogoDocumentalVersionId;

    /**
     * solo se usa si se la orden esta en estado 1. de lo contrario
     * este atributo nunca se usa, al igaul que sucede con el atributo anterior.
     */
    private MultipartFile OCAFpdf;

    /**
     * la orden de compra se envia
     */
    public enum TipoEnvio{
        MANUAL,
        EMAIL,
        WHATSAPP,
    }

}
