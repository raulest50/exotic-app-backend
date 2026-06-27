package exotic.app.planta.service.produccion;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.produccion.AreaOperativaRuidoMuestra;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.produccion.AreaOperativaRuidoMuestraRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.service.master.configs.MasterDirectiveService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaOperativaRuidoMuestraService {

    private static final int MIN_DURACION_MS = 500;
    private static final int MAX_DURACION_MS = 5_000;
    private static final int MIN_SAMPLE_RATE = 8_000;
    private static final int MAX_SAMPLE_RATE = 192_000;
    private static final double MIN_RUIDO_DB = -160.0;
    private static final double MAX_RUIDO_DB = 20.0;
    private static final double MIN_RMS = 0.0;
    private static final double MAX_RMS = 1.0;

    private final AreaOperativaRuidoMuestraRepo areaOperativaRuidoMuestraRepo;
    private final AreaProduccionRepo areaProduccionRepo;
    private final MasterDirectiveService masterDirectiveService;

    @Transactional
    public AreaOperativaRuidoMuestraResponse registrarMuestra(User user, AreaOperativaRuidoMuestraRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La muestra de ruido es requerida.");
        }

        if (!masterDirectiveService.isAreaOperativaNoiseEnabled()) {
            throw new AreaOperativaRuidoDeshabilitadaException("La medicion de ruido del area operativa esta deshabilitada por directiva.");
        }

        AreaOperativa areaOperativa = resolveAreaResponsable(user);
        validateRequest(request);

        LocalDateTime now = AppTime.now();
        LocalDateTime rateLimitThreshold = now.minusMinutes(masterDirectiveService.getAreaOperativaNoiseIntervalMinutes());
        boolean hasRecentSample = areaOperativaRuidoMuestraRepo.existsByAreaOperativa_AreaIdAndUsuario_IdAndCreatedAtAfter(
                areaOperativa.getAreaId(),
                user.getId(),
                rateLimitThreshold
        );
        if (hasRecentSample) {
            throw new IllegalStateException("Ya existe una muestra reciente para esta area operativa.");
        }

        AreaOperativaRuidoMuestra muestra = new AreaOperativaRuidoMuestra();
        muestra.setAreaOperativa(areaOperativa);
        muestra.setUsuario(user);
        muestra.setFechaMuestra(toAppLocalDateTime(request.getFechaMuestra(), now));
        muestra.setRuidoDb(request.getRuidoDb());
        muestra.setRms(request.getRms());
        muestra.setDuracionMs(request.getDuracionMs());
        muestra.setSampleRate(request.getSampleRate());
        muestra.setCreatedAt(now);

        AreaOperativaRuidoMuestra saved = areaOperativaRuidoMuestraRepo.save(muestra);

        return AreaOperativaRuidoMuestraResponse.builder()
                .id(saved.getId())
                .areaOperativaId(areaOperativa.getAreaId())
                .fechaMuestra(saved.getFechaMuestra())
                .ruidoDb(saved.getRuidoDb())
                .rms(saved.getRms())
                .duracionMs(saved.getDuracionMs())
                .sampleRate(saved.getSampleRate())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private AreaOperativa resolveAreaResponsable(User user) {
        List<AreaOperativa> areasResponsables = areaProduccionRepo.findAllByResponsableArea_Id(user.getId());
        if (areasResponsables.isEmpty()) {
            throw new AccessDeniedException("El usuario no es responsable de un area operativa.");
        }
        return areasResponsables.get(0);
    }

    private void validateRequest(AreaOperativaRuidoMuestraRequest request) {
        validateFiniteRange(request.getRuidoDb(), MIN_RUIDO_DB, MAX_RUIDO_DB, "ruidoDb");
        validateFiniteRange(request.getRms(), MIN_RMS, MAX_RMS, "rms");

        if (request.getDuracionMs() == null
                || request.getDuracionMs() < MIN_DURACION_MS
                || request.getDuracionMs() > MAX_DURACION_MS) {
            throw new IllegalArgumentException("duracionMs debe estar entre 500 y 5000.");
        }

        if (request.getSampleRate() == null
                || request.getSampleRate() < MIN_SAMPLE_RATE
                || request.getSampleRate() > MAX_SAMPLE_RATE) {
            throw new IllegalArgumentException("sampleRate debe estar entre 8000 y 192000.");
        }
    }

    private void validateFiniteRange(Double value, double min, double max, String fieldName) {
        if (value == null || value.isNaN() || value.isInfinite() || value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " esta fuera del rango permitido.");
        }
    }

    private LocalDateTime toAppLocalDateTime(OffsetDateTime fechaMuestra, LocalDateTime fallback) {
        if (fechaMuestra == null) {
            return fallback;
        }
        return fechaMuestra.atZoneSameInstant(AppTime.zoneId()).toLocalDateTime();
    }

    @Getter
    @Setter
    public static class AreaOperativaRuidoMuestraRequest {
        private Double ruidoDb;
        private Double rms;
        private OffsetDateTime fechaMuestra;
        private Integer duracionMs;
        private Integer sampleRate;
    }

    public static class AreaOperativaRuidoDeshabilitadaException extends RuntimeException {
        public AreaOperativaRuidoDeshabilitadaException(String message) {
            super(message);
        }
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AreaOperativaRuidoMuestraResponse {
        private Long id;
        private Integer areaOperativaId;
        private LocalDateTime fechaMuestra;
        private Double ruidoDb;
        private Double rms;
        private Integer duracionMs;
        private Integer sampleRate;
        private LocalDateTime createdAt;
    }
}
