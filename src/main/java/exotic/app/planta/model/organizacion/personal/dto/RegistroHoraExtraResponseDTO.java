package exotic.app.planta.model.organizacion.personal.dto;

import exotic.app.planta.model.organizacion.personal.RegistroHoraExtra;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class RegistroHoraExtraResponseDTO {
    private Long id;
    private Long integranteId;
    private String integranteNombre;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private Integer minutos;
    private String motivo;
    private String observaciones;
    private RegistroHoraExtra.Estado estado;
    private Long registradoPorId;
    private String registradoPorUsername;
    private String registradoPorNombre;
    private Long aprobadoPorId;
    private String aprobadoPorUsername;
    private String aprobadoPorNombre;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaDecision;
    private String motivoRechazoOAnulacion;

    public static RegistroHoraExtraResponseDTO fromEntity(RegistroHoraExtra registro) {
        String nombres = registro.getIntegrante().getNombres() == null ? "" : registro.getIntegrante().getNombres();
        String apellidos = registro.getIntegrante().getApellidos() == null ? "" : registro.getIntegrante().getApellidos();
        String integranteNombre = (nombres + " " + apellidos).trim();

        return RegistroHoraExtraResponseDTO.builder()
                .id(registro.getId())
                .integranteId(registro.getIntegrante().getId())
                .integranteNombre(integranteNombre)
                .fecha(registro.getFecha())
                .horaInicio(registro.getHoraInicio())
                .horaFin(registro.getHoraFin())
                .minutos(registro.getMinutos())
                .motivo(registro.getMotivo())
                .observaciones(registro.getObservaciones())
                .estado(registro.getEstado())
                .registradoPorId(registro.getRegistradoPor().getId())
                .registradoPorUsername(registro.getRegistradoPor().getUsername())
                .registradoPorNombre(registro.getRegistradoPor().getNombreCompleto())
                .aprobadoPorId(registro.getAprobadoPor() != null ? registro.getAprobadoPor().getId() : null)
                .aprobadoPorUsername(registro.getAprobadoPor() != null ? registro.getAprobadoPor().getUsername() : null)
                .aprobadoPorNombre(registro.getAprobadoPor() != null ? registro.getAprobadoPor().getNombreCompleto() : null)
                .fechaRegistro(registro.getFechaRegistro())
                .fechaDecision(registro.getFechaDecision())
                .motivoRechazoOAnulacion(registro.getMotivoRechazoOAnulacion())
                .build();
    }
}
