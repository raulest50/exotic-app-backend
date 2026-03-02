package exotic.app.planta.model.inventarios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO para respuesta de TransaccionAlmacen sin relaciones circulares.
 * Usado para evitar problemas de serialización JSON con relaciones bidireccionales.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionAlmacenResponseDTO {
    private int transaccionId;
    private LocalDateTime fechaTransaccion;
    private int idEntidadCausante;
    private String tipoEntidadCausante;
    private String observaciones;
    private String estadoContable;
    private String loteAsignado;
    private UsuarioAprobadorDTO usuarioAprobador;

    /**
     * DTO simplificado para usuario aprobador (solo ID y nombre)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsuarioAprobadorDTO {
        private Long userId;
        private String nombre;
    }
}


