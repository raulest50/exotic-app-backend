package exotic.app.planta.model.bi.dto;

import exotic.app.planta.model.inventarios.Movimiento;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InformeDiarioComprasRowDTO {

    private LocalDateTime fechaIngreso;
    private Integer transaccionId;
    private Integer ordenCompraId;
    private Integer facturaCompraId;
    private String proveedorNit;
    private String proveedorNombre;
    private String materialId;
    private String materialNombre;
    private String tipoMaterial;
    private Double cantidadIngresada;
    private String unidad;
    private String batchNumber;
    private LocalDate fechaVencimientoLote;
    private Movimiento.Almacen almacen;
    private String usuarioAprobadorNombre;
    private String observaciones;
}
