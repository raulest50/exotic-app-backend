package exotic.app.planta.model.bi.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class HorasExtraBiSeriePuntoDTO {
    private String bucket;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private long registrosRegistrada;
    private long registrosAprobada;
    private long registrosRechazada;
    private long registrosAnulada;
    private int minutosRegistrada;
    private int minutosAprobada;
    private int minutosRechazada;
    private int minutosAnulada;
    private double horasRegistrada;
    private double horasAprobada;
    private double horasRechazada;
    private double horasAnulada;
}
