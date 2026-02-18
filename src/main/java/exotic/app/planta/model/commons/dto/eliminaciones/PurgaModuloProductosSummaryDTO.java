package exotic.app.planta.model.commons.dto.eliminaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurgaModuloProductosSummaryDTO {
    private long transaccionesAlmacen;
    private long movimientos;
    private long lotes;
    private long ordenesCompra;
    private long ordenesProduccion;
    private long insumos;
    private long procesosProduccionCompleto;
    private long casePacks;
    private long insumosEmpaque;
    private long manufacturingVersions;
    private long productos;
    /** false si el perfil activo es prod (producción). */
    private boolean permitido;
    private String mensajeEntorno;
}
