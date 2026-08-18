package exotic.app.planta.service.master.configs;

import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.model.master.configs.dto.DTO_All_MasterDirectives;
import exotic.app.planta.model.master.configs.dto.DTO_MasterD_Update;
import exotic.app.planta.model.produccion.batchrecord.EstadoBatchRecord;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import exotic.app.planta.repo.produccion.batchrecord.BatchRecordRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Servicio para gestionar las directivas maestras de configuración
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDirectiveService {

    private static final EnumSet<EstadoBatchRecord> ESTADOS_BATCH_RECORD_TERMINALES =
            EnumSet.of(EstadoBatchRecord.CERRADO, EstadoBatchRecord.ANULADO);

    private final MasterDirectiveRepo masterDirectiveRepo;
    private final BatchRecordRepo batchRecordRepo;

    /**
     * Obtiene todas las directivas maestras
     * @return DTO con la lista de todas las directivas maestras
     */
    public DTO_All_MasterDirectives getAllMasterDirectives() {
        List<MasterDirective> masterDirectives = masterDirectiveRepo.findAll();
        return new DTO_All_MasterDirectives(masterDirectives);
    }

    public Optional<MasterDirective> getByNombre(String nombre) {
        return masterDirectiveRepo.findByNombre(nombre);
    }

    public int getLimiteRecepcionesParcialesOcm() {
        return getPositiveIntegerDirectiveValue(
                MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM,
                MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM
        );
    }

    public boolean isDispensacionNoBloqueaInicioProduccion() {
        return getBooleanDirectiveValue(
                MasterDirectiveKeys.DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION,
                MasterDirectiveKeys.DEFAULT_DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION
        );
    }

    public int getMpsSemanalDiasBloqueoEdicion() {
        return getIntegerDirectiveValueInRange(
                MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION,
                MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_DIAS_BLOQUEO_EDICION,
                0,
                7
        );
    }

    public boolean isMpsSemanalPermitirAgregarTerminadosAprobado() {
        return getBooleanDirectiveValue(
                MasterDirectiveKeys.MPS_SEMANAL_PERMITIR_AGREGAR_TERMINADOS_APROBADO,
                MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_PERMITIR_AGREGAR_TERMINADOS_APROBADO
        );
    }

    public boolean isAreaOperativaNoiseEnabled() {
        return getBooleanDirectiveValue(
                MasterDirectiveKeys.AREA_OPERATIVA_NOISE_ENABLED,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_NOISE_ENABLED
        );
    }

    public int getAreaOperativaNoiseIntervalMinutes() {
        return getIntegerDirectiveValueInRange(
                MasterDirectiveKeys.AREA_OPERATIVA_NOISE_INTERVAL_MINUTES,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_NOISE_INTERVAL_MINUTES,
                10,
                60
        );
    }

    public int getAreaOperativaNoiseSampleSeconds() {
        return getIntegerDirectiveValueInRange(
                MasterDirectiveKeys.AREA_OPERATIVA_NOISE_SAMPLE_SECONDS,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_NOISE_SAMPLE_SECONDS,
                1,
                5
        );
    }

    public boolean isAreaOperativaInactivityAlertEnabled() {
        return getBooleanDirectiveValue(
                MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_ALERT_ENABLED,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_INACTIVITY_ALERT_ENABLED
        );
    }

    public int getAreaOperativaInactivityThresholdMinutes() {
        return getIntegerDirectiveValueInRange(
                MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_THRESHOLD_MINUTES,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_INACTIVITY_THRESHOLD_MINUTES,
                5,
                480
        );
    }

    public int getAreaOperativaInactivityCheckIntervalMinutes() {
        return getIntegerDirectiveValueInRange(
                MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_CHECK_INTERVAL_MINUTES,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_INACTIVITY_CHECK_INTERVAL_MINUTES,
                5,
                20
        );
    }

    public boolean isAreaOperativaPanelHistoricoToggleEnabled() {
        return getBooleanDirectiveValue(
                MasterDirectiveKeys.AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_ENABLED,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_ENABLED
        );
    }

    public boolean isAreaOperativaAdminCorrectionEnabled() {
        return getBooleanDirectiveValue(
                MasterDirectiveKeys.AREA_OPERATIVA_ADMIN_CORRECTION_ENABLED,
                MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_ADMIN_CORRECTION_ENABLED
        );
    }

    public boolean isBatchRecordWorkflowEnabled() {
        return getBooleanDirectiveValue(
                MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED,
                MasterDirectiveKeys.DEFAULT_BATCH_RECORD_WORKFLOW_ENABLED
        );
    }

    /**
     * Obtiene el modo que debe congelarse para una orden nueva y conserva un
     * bloqueo compartido hasta que termine la transaccion creadora. De esta
     * manera, una desactivacion no puede adelantarse a una OP u OF en curso.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockBatchRecordWorkflowForNewOrder() {
        return masterDirectiveRepo.findByNombreForShare(
                        MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED)
                .map(directive -> parseBooleanOrFallback(
                        directive.getValor(),
                        MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED,
                        MasterDirectiveKeys.DEFAULT_BATCH_RECORD_WORKFLOW_ENABLED))
                .orElseGet(() -> {
                    log.warn("Directiva maestra {} no encontrada. Usando fallback {}",
                            MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED,
                            MasterDirectiveKeys.DEFAULT_BATCH_RECORD_WORKFLOW_ENABLED);
                    return MasterDirectiveKeys.DEFAULT_BATCH_RECORD_WORKFLOW_ENABLED;
                });
    }

    public int getPositiveIntegerDirectiveValue(String nombre, int fallback) {
        Optional<MasterDirective> directiveOpt = masterDirectiveRepo.findByNombre(nombre);
        if (directiveOpt.isEmpty()) {
            log.warn("Directiva maestra {} no encontrada. Usando fallback {}", nombre, fallback);
            return fallback;
        }

        try {
            return parsePositiveInteger(directiveOpt.get().getValor(), nombre);
        } catch (IllegalArgumentException e) {
            log.warn("Valor invalido para directiva maestra {}. Usando fallback {}. Causa: {}",
                    nombre, fallback, e.getMessage());
            return fallback;
        }
    }

    public boolean getBooleanDirectiveValue(String nombre, boolean fallback) {
        Optional<MasterDirective> directiveOpt = masterDirectiveRepo.findByNombre(nombre);
        if (directiveOpt.isEmpty()) {
            log.warn("Directiva maestra {} no encontrada. Usando fallback {}", nombre, fallback);
            return fallback;
        }

        try {
            return parseBoolean(directiveOpt.get().getValor(), nombre);
        } catch (IllegalArgumentException e) {
            log.warn("Valor invalido para directiva maestra {}. Usando fallback {}. Causa: {}",
                    nombre, fallback, e.getMessage());
            return fallback;
        }
    }

    public int getIntegerDirectiveValueInRange(String nombre, int fallback, int min, int max) {
        Optional<MasterDirective> directiveOpt = masterDirectiveRepo.findByNombre(nombre);
        if (directiveOpt.isEmpty()) {
            log.warn("Directiva maestra {} no encontrada. Usando fallback {}", nombre, fallback);
            return fallback;
        }

        try {
            return parseIntegerInRange(directiveOpt.get().getValor(), nombre, min, max);
        } catch (IllegalArgumentException e) {
            log.warn("Valor invalido para directiva maestra {}. Usando fallback {}. Causa: {}",
                    nombre, fallback, e.getMessage());
            return fallback;
        }
    }

    /**
     * Actualiza una directiva maestra
     * @param updateDTO DTO con la directiva original y la nueva directiva
     * @return La directiva actualizada
     * @throws RuntimeException si la directiva no existe o si se intenta cambiar el nombre
     */
    @Transactional
    public MasterDirective updateMasterDirective(DTO_MasterD_Update updateDTO) {
        if (updateDTO == null || updateDTO.getOldMasterDirective() == null || updateDTO.getNewMasterDirective() == null) {
            throw new RuntimeException("La solicitud de actualizacion de directiva maestra es invalida");
        }

        MasterDirective oldDirective = updateDTO.getOldMasterDirective();
        MasterDirective newDirective = updateDTO.getNewMasterDirective();
        
        // Verificar que la directiva existe
        Optional<MasterDirective> existingDirectiveOpt = masterDirectiveRepo.findByIdForUpdate(oldDirective.getId());
        if (existingDirectiveOpt.isEmpty()) {
            throw new RuntimeException("La directiva maestra con ID " + oldDirective.getId() + " no existe");
        }
        
        MasterDirective existingDirective = existingDirectiveOpt.get();
        
        // Verificar que no se está cambiando el nombre (que es único)
        if (!existingDirective.getNombre().equals(newDirective.getNombre())) {
            throw new RuntimeException("No se permite cambiar el nombre de una directiva maestra");
        }

        validateValorByTipo(existingDirective, newDirective.getValor());
        validarDesactivacionBatchRecord(existingDirective, newDirective.getValor());
        
        // Actualizar solo los campos permitidos
        existingDirective.setValor(normalizeValorByTipo(existingDirective, newDirective.getValor()));
        existingDirective.setResumen(newDirective.getResumen());
        existingDirective.setAyuda(newDirective.getAyuda());
        
        // Guardar y retornar la directiva actualizada
        return masterDirectiveRepo.save(existingDirective);
    }

    private void validarDesactivacionBatchRecord(
            MasterDirective existingDirective,
            String nuevoValor
    ) {
        if (!MasterDirectiveKeys.BATCH_RECORD_WORKFLOW_ENABLED.equals(
                existingDirective.getNombre())) {
            return;
        }

        boolean valorActual = parseBooleanOrFallback(
                existingDirective.getValor(),
                existingDirective.getNombre(),
                MasterDirectiveKeys.DEFAULT_BATCH_RECORD_WORKFLOW_ENABLED);
        boolean valorNuevo = parseBoolean(nuevoValor, existingDirective.getNombre());
        if (!valorActual || valorNuevo) {
            return;
        }

        long expedientesActivos = batchRecordRepo.countByEstadoNotIn(
                ESTADOS_BATCH_RECORD_TERMINALES);
        if (expedientesActivos > 0) {
            throw new BatchRecordWorkflowTransitionException(expedientesActivos);
        }
    }

    private void validateValorByTipo(MasterDirective directive, String valor) {
        if (directive.getTipoDato() == MasterDirective.TipoDato.NUMERO) {
            if (MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION.equals(directive.getNombre())) {
                parseIntegerInRange(valor, directive.getNombre(), 0, 7);
                return;
            }
            if (MasterDirectiveKeys.AREA_OPERATIVA_NOISE_INTERVAL_MINUTES.equals(directive.getNombre())) {
                parseIntegerInRange(valor, directive.getNombre(), 10, 60);
                return;
            }
            if (MasterDirectiveKeys.AREA_OPERATIVA_NOISE_SAMPLE_SECONDS.equals(directive.getNombre())) {
                parseIntegerInRange(valor, directive.getNombre(), 1, 5);
                return;
            }
            if (MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_THRESHOLD_MINUTES.equals(directive.getNombre())) {
                parseIntegerInRange(valor, directive.getNombre(), 5, 480);
                return;
            }
            if (MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_CHECK_INTERVAL_MINUTES.equals(directive.getNombre())) {
                parseIntegerInRange(valor, directive.getNombre(), 5, 20);
                return;
            }
            parsePositiveInteger(valor, directive.getNombre());
        } else if (directive.getTipoDato() == MasterDirective.TipoDato.BOOLEANO) {
            parseBoolean(valor, directive.getNombre());
        }
    }

    private String normalizeValorByTipo(MasterDirective directive, String valor) {
        if (directive.getTipoDato() == MasterDirective.TipoDato.BOOLEANO) {
            return String.valueOf(parseBoolean(valor, directive.getNombre()));
        }
        return valor != null ? valor.trim() : null;
    }

    private int parsePositiveInteger(String valor, String nombre) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("La directiva " + nombre + " requiere un valor numerico entero positivo");
        }

        String normalized = valor.trim();
        if (!normalized.matches("\\d+")) {
            throw new IllegalArgumentException("La directiva " + nombre + " solo acepta enteros positivos");
        }

        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed < 1) {
                throw new IllegalArgumentException("La directiva " + nombre + " debe ser mayor o igual a 1");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La directiva " + nombre + " excede el rango entero permitido", e);
        }
    }

    private int parseIntegerInRange(String valor, String nombre, int min, int max) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("La directiva " + nombre + " requiere un valor numerico entero");
        }

        String normalized = valor.trim();
        if (!normalized.matches("\\d+")) {
            throw new IllegalArgumentException("La directiva " + nombre + " solo acepta enteros");
        }

        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("La directiva " + nombre + " debe estar entre " + min + " y " + max);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La directiva " + nombre + " excede el rango entero permitido", e);
        }
    }

    private boolean parseBoolean(String valor, String nombre) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("La directiva " + nombre + " requiere un valor booleano");
        }

        String normalized = valor.trim();
        if (!normalized.equalsIgnoreCase("true") && !normalized.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("La directiva " + nombre + " solo acepta true o false");
        }

        return Boolean.parseBoolean(normalized);
    }

    private boolean parseBooleanOrFallback(
            String valor,
            String nombre,
            boolean fallback
    ) {
        try {
            return parseBoolean(valor, nombre);
        } catch (IllegalArgumentException exception) {
            log.warn("Valor invalido para directiva maestra {}. Usando fallback {}. Causa: {}",
                    nombre, fallback, exception.getMessage());
            return fallback;
        }
    }

    public static final class BatchRecordWorkflowTransitionException
            extends IllegalStateException {

        private final long expedientesActivos;

        public BatchRecordWorkflowTransitionException(long expedientesActivos) {
            super("No se puede desactivar Batch Record mientras existan "
                    + expedientesActivos + " expediente(s) sin cerrar o anular.");
            this.expedientesActivos = expedientesActivos;
        }

        public long getExpedientesActivos() {
            return expedientesActivos;
        }
    }
}
