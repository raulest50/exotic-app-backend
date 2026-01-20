package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KardexMovimientosPageDTO {
    private String productoId;
    private String productoNombre;
    private String tipoUnidades;

    /** Saldo acumulado antes de startDate (inicio del rango) */
    private double saldoInicial;

    private List<KardexMovimientoRowDTO> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
}

