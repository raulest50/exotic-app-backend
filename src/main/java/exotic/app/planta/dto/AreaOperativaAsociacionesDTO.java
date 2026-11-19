package exotic.app.planta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Asociaciones del area con catalogos; no representa produccion, stock ni capacidad. */
public final class AreaOperativaAsociacionesDTO {
    private AreaOperativaAsociacionesDTO() {}

    public record Item(String id, String nombre, String detalle) {}

    public record SemiterminadosRequest(
            @NotNull(message = "La lista de semiterminados es obligatoria.")
            @Size(max = 1000, message = "Puede asociar hasta 1000 semiterminados.")
            List<@NotBlank(message = "El codigo del semiterminado es obligatorio.")
                    @Size(max = 255, message = "El codigo del semiterminado es demasiado largo.") String> productoIds) {}

    public record RecursosRequest(
            @NotNull(message = "La lista de recursos es obligatoria.")
            @Size(max = 1000, message = "Puede asociar hasta 1000 recursos.")
            List<@NotNull(message = "El ID del recurso es obligatorio.")
                    @Positive(message = "El ID del recurso debe ser positivo.") Long> recursoIds) {}
}
