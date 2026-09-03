package exotic.app.planta.service.controles;

import exotic.app.planta.model.calidad.*;
import exotic.app.planta.model.controles.*;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.calidad.ControlProcesoEjecucionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Writes the V077 mirror for an execution governed by a migrated plan version. */
@Service
@RequiredArgsConstructor
public class LegacyControlExecutionProjection {

    private final ControlProcesoEjecucionRepo legacyExecutionRepo;

    @Transactional
    public ControlProcesoEjecucion createMirror(
            ControlRequerido required,
            User actor,
            LocalDateTime registeredAt,
            ResultadoEjecucionControl result,
            String observations,
            List<MuestraEjecucionControl> samples) {
        if (required.getVersionPlan().getLegacyPlantilla() == null) {
            throw new IllegalArgumentException("Solo una version migrada admite proyeccion legada.");
        }
        if (required.getBatchRecordEtapa() != null
                && (required.getBatchRecordEtapa().getControlProcesoPlantilla() == null
                || !required.getVersionPlan().getLegacyPlantilla().getId().equals(
                required.getBatchRecordEtapa().getControlProcesoPlantilla().getId()))) {
            throw new IllegalStateException(
                    "La etapa no tiene congelada la misma plantilla legada; no se puede crear un espejo seguro.");
        }

        ControlProcesoEjecucion mirror = new ControlProcesoEjecucion();
        mirror.setPlantilla(required.getVersionPlan().getLegacyPlantilla());
        mirror.setLote(required.getLote());
        mirror.setBatchRecord(required.getBatchRecord());
        mirror.setBatchRecordEtapa(required.getBatchRecordEtapa());
        mirror.setUsuario(actor);
        mirror.setFechaRegistro(registeredAt);
        mirror.setResultado(ResultadoControlProceso.valueOf(result.name()));
        mirror.setObservaciones(observations);

        for (MuestraEjecucionControl neutralSample : samples) {
            ControlProcesoCaracteristica legacyCharacteristic =
                    neutralSample.getCaracteristica().getLegacyCaracteristica();
            if (legacyCharacteristic == null) {
                throw new IllegalStateException(
                        "Una caracteristica de la version migrada no tiene contraparte legada.");
            }
            ControlProcesoMuestra legacySample = new ControlProcesoMuestra();
            legacySample.setEjecucion(mirror);
            legacySample.setCaracteristica(legacyCharacteristic);
            legacySample.setNumeroMuestra(neutralSample.getNumeroMuestra());
            for (LecturaEjecucionControl neutralReading : neutralSample.getLecturas()) {
                ControlProcesoLectura legacyReading = new ControlProcesoLectura();
                legacyReading.setMuestra(legacySample);
                legacyReading.setIndiceUnidad(neutralReading.getIndiceUnidad());
                legacyReading.setValorBooleano(neutralReading.getValorBooleano());
                legacyReading.setValorNumerico(toExactLegacyDouble(neutralReading.getValorNumerico()));
                legacySample.getLecturas().add(legacyReading);
            }
            mirror.getMuestras().add(legacySample);
        }
        return legacyExecutionRepo.saveAndFlush(mirror);
    }

    private Double toExactLegacyDouble(BigDecimal value) {
        if (value == null) return null;
        double candidate = value.doubleValue();
        if (!Double.isFinite(candidate)
                || BigDecimal.valueOf(candidate).compareTo(value.stripTrailingZeros()) != 0) {
            throw new IllegalArgumentException(
                    "El valor " + value.toPlainString()
                            + " no puede representarse sin perdida en el modelo legado. "
                            + "Retire la version legada y publique una version nativa antes de registrarlo.");
        }
        return candidate;
    }
}
