package exotic.app.planta.model.inventarios.dto;

import exotic.app.planta.model.produccion.dto.OrdenProduccionDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de respuesta para la consulta de una OrdenProduccion por lote asignado.
 * Usa DTOs seguros (sin entidades JPA) para evitar problemas de serialización
 * con proxies lazy de Hibernate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IngresoTerminadoConsultaResponseDTO {

    private OrdenProduccionDTO ordenProduccion;

    private TerminadoInfoDTO terminado;

    /** loteSize de la Categoria del Terminado; 0 si la categoria no tiene loteSize definido. */
    private int loteSizeEsperado;
}
