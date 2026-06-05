package exotic.app.planta.model.produccion.dto;

import exotic.app.planta.model.produccion.EstadoMpsSemanalObservacion;
import exotic.app.planta.model.produccion.MasterProductionScheduleSemanal;
import exotic.app.planta.model.produccion.MpsSemanalObservacion;
import exotic.app.planta.model.produccion.SemanaMPS;
import exotic.app.planta.model.produccion.TipoMpsSemanalObservacion;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MpsSemanalObservacionDTO {
    private Long observacionId;
    private Integer mpsId;
    private LocalDate weekStartDate;
    private String semanaMpsCodigo;
    private Integer revisionMps;
    private String autorUsername;
    private String mensaje;
    private TipoMpsSemanalObservacion tipo;
    private EstadoMpsSemanalObservacion estado;
    private String respuestaCorreccion;
    private String atendidaPorUsername;
    private LocalDateTime fechaAtencion;
    private String cerradaPorUsername;
    private LocalDateTime fechaCierre;
    private LocalDateTime fechaCreacion;

    public static MpsSemanalObservacionDTO fromEntity(MpsSemanalObservacion entity) {
        MpsSemanalObservacionDTO dto = new MpsSemanalObservacionDTO();
        dto.setObservacionId(entity.getObservacionId());
        dto.setRevisionMps(entity.getRevisionMps());
        dto.setAutorUsername(entity.getAutorUsername());
        dto.setMensaje(entity.getMensaje());
        dto.setTipo(entity.getTipo());
        dto.setEstado(entity.getEstado());
        dto.setRespuestaCorreccion(entity.getRespuestaCorreccion());
        dto.setAtendidaPorUsername(entity.getAtendidaPorUsername());
        dto.setFechaAtencion(entity.getFechaAtencion());
        dto.setCerradaPorUsername(entity.getCerradaPorUsername());
        dto.setFechaCierre(entity.getFechaCierre());
        dto.setFechaCreacion(entity.getFechaCreacion());

        MasterProductionScheduleSemanal mps = entity.getMpsSemanal();
        if (mps != null) {
            dto.setMpsId(mps.getMpsId());
            dto.setWeekStartDate(mps.getWeekStartDate());
            SemanaMPS semanaMPS = mps.getSemanaMps();
            if (semanaMPS != null) {
                dto.setSemanaMpsCodigo(semanaMPS.getCodigo());
            }
        }
        return dto;
    }
}
