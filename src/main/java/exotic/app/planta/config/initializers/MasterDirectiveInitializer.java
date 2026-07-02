package exotic.app.planta.config.initializers;

import exotic.app.planta.model.master.configs.MasterDirective;
import exotic.app.planta.model.master.configs.MasterDirectiveKeys;
import exotic.app.planta.repo.master.configs.MasterDirectiveRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MasterDirectiveInitializer {

    private static final String LIMITE_OCM_RESUMEN = "Tope global para configurar limites de recepciones OCM por proveedor";
    private static final String LIMITE_OCM_AYUDA = "Define el maximo permitido al crear o editar el limite de recepciones parciales OCM de un proveedor. No afecta retroactivamente limites ya configurados y no se usa directamente para validar ingresos OCM.";
    private static final String DISPENSACION_NO_BLOQUEA_RESUMEN = "Permite iniciar el proceso productivo sin esperar la dispensacion de materiales";
    private static final String DISPENSACION_NO_BLOQUEA_AYUDA = "Cuando esta activa, Almacen General se marca automaticamente como completado solo a nivel de seguimiento de proceso al crear ordenes de produccion nuevas. No crea transacciones de almacen, no descuenta inventario y no acredita la dispensacion real. Para ordenes existentes use la accion retroactiva de esta pantalla.";
    private static final String MASTER_SUPERMASTER_DIRECTIVES_ACCESS_RESUMEN = "Permite que master vea y entre al modulo de Directivas Super Master";
    private static final String MASTER_SUPERMASTER_DIRECTIVES_ACCESS_AYUDA = "Cuando esta activa, el usuario master puede ver el acceso en Inicio y entrar a la ruta de Directivas Super Master. No agrega proteccion adicional sobre los endpoints API.";
    private static final String MPS_SEMANAL_DIAS_BLOQUEO_RESUMEN = "Cantidad de dias bloqueados para editar MPS semanal";
    private static final String MPS_SEMANAL_DIAS_BLOQUEO_AYUDA = "Define cuantos dias desde la fecha actual quedan bloqueados para editar el MPS semanal. Acepta valores de 0 a 7: 0 permite editar desde hoy, 1 bloquea hoy, 2 bloquea hoy y manana, y asi sucesivamente.";
    private static final String MPS_SEMANAL_AGREGAR_TERMINADOS_APROBADO_RESUMEN = "Permite agregar terminados nuevos en MPS semanal aprobada";
    private static final String MPS_SEMANAL_AGREGAR_TERMINADOS_APROBADO_AYUDA = "Controla si una MPS aprobada o cerrada permite agregar terminados nuevos.";
    private static final String AREA_OPERATIVA_NOISE_ENABLED_RESUMEN = "Habilita la medicion de ruido en tablets del Area Operativa";
    private static final String AREA_OPERATIVA_NOISE_ENABLED_AYUDA = "Cuando esta activa, el panel de Area Operativa puede tomar muestras cortas de audio desde el navegador de la tablet, convertirlas a dB relativo y enviarlas al backend. No almacena audio crudo.";
    private static final String AREA_OPERATIVA_NOISE_INTERVAL_RESUMEN = "Intervalo de muestreo de ruido en minutos";
    private static final String AREA_OPERATIVA_NOISE_INTERVAL_AYUDA = "Define cada cuantos minutos la tablet intentara tomar una muestra de ruido mientras el panel de Area Operativa este activo. Acepta valores entre 10 y 60.";
    private static final String AREA_OPERATIVA_NOISE_SAMPLE_RESUMEN = "Tamano de muestra de ruido en segundos";
    private static final String AREA_OPERATIVA_NOISE_SAMPLE_AYUDA = "Define cuantos segundos de audio se procesan localmente para calcular un unico valor de ruido relativo en dB. Acepta valores entre 1 y 5.";
    private static final String AREA_OPERATIVA_INACTIVITY_ENABLED_RESUMEN = "Habilita alertas de inactividad por terminaciones";
    private static final String AREA_OPERATIVA_INACTIVITY_ENABLED_AYUDA = "Cuando esta activa, el monitoreo de Area Operativa puede marcar areas con carga activa que llevan demasiado tiempo sin terminaciones reportadas por el lider.";
    private static final String AREA_OPERATIVA_INACTIVITY_THRESHOLD_RESUMEN = "Umbral sin terminaciones para alertar inactividad";
    private static final String AREA_OPERATIVA_INACTIVITY_THRESHOLD_AYUDA = "Define cuantos minutos puede pasar un area con carga activa sin terminaciones reportadas antes de marcar alerta. Acepta valores entre 5 y 480.";
    private static final String AREA_OPERATIVA_INACTIVITY_INTERVAL_RESUMEN = "Intervalo de chequeo de alertas en monitoreo";
    private static final String AREA_OPERATIVA_INACTIVITY_INTERVAL_AYUDA = "Define cada cuantos minutos el tab de monitoreo consulta las alertas mientras esta abierto y visible. Acepta valores entre 5 y 20.";
    private static final String AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_RESUMEN = "Habilita alternar entre semana actual e historico en Area Operativa";
    private static final String AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_AYUDA = "Cuando esta activa, el panel de Area Operativa muestra un control para que el operario alterne entre ordenes con fecha planificada de entrega en la semana actual e historico completo. Cuando esta apagada, el panel conserva la vista historica actual.";

    private final MasterDirectiveRepo masterDirectiveRepo;

    public void initializeMasterDirectives() {
        ensureLimiteRecepcionesParcialesOcm();
        ensureDispensacionNoBloqueaInicioProduccion();
        ensureEnableMasterSupermasterDirectivesAccess();
        ensureMpsSemanalDiasBloqueoEdicion();
        ensureMpsSemanalPermitirAgregarTerminadosAprobado();
        ensureAreaOperativaNoiseEnabled();
        ensureAreaOperativaNoiseIntervalMinutes();
        ensureAreaOperativaNoiseSampleSeconds();
        ensureAreaOperativaInactivityAlertEnabled();
        ensureAreaOperativaInactivityThresholdMinutes();
        ensureAreaOperativaInactivityCheckIntervalMinutes();
        ensureAreaOperativaPanelHistoricoToggleEnabled();
    }

    private void ensureLimiteRecepcionesParcialesOcm() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM)
                .map(this::actualizarMetadataLimiteRecepcionesParcialesOcm)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM));
                    aplicarMetadataLimiteRecepcionesParcialesOcm(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataLimiteRecepcionesParcialesOcm(MasterDirective directive) {
        aplicarMetadataLimiteRecepcionesParcialesOcm(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataLimiteRecepcionesParcialesOcm(MasterDirective directive) {
        directive.setResumen(LIMITE_OCM_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setGrupo(MasterDirective.GRUPO.COMPRAS_ALMACEN);
        directive.setAyuda(LIMITE_OCM_AYUDA);
    }

    private void ensureDispensacionNoBloqueaInicioProduccion() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION)
                .map(this::actualizarMetadataDispensacionNoBloqueaInicioProduccion)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_DISPENSACION_NO_BLOQUEA_INICIO_PRODUCCION));
                    aplicarMetadataDispensacionNoBloqueaInicioProduccion(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataDispensacionNoBloqueaInicioProduccion(MasterDirective directive) {
        aplicarMetadataDispensacionNoBloqueaInicioProduccion(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataDispensacionNoBloqueaInicioProduccion(MasterDirective directive) {
        directive.setResumen(DISPENSACION_NO_BLOQUEA_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.BOOLEANO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(DISPENSACION_NO_BLOQUEA_AYUDA);
    }

    private void ensureEnableMasterSupermasterDirectivesAccess() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.ENABLE_MASTER_SUPERMASTER_DIRECTIVES_ACCESS)
                .map(this::actualizarMetadataEnableMasterSupermasterDirectivesAccess)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.ENABLE_MASTER_SUPERMASTER_DIRECTIVES_ACCESS);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_ENABLE_MASTER_SUPERMASTER_DIRECTIVES_ACCESS));
                    aplicarMetadataEnableMasterSupermasterDirectivesAccess(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataEnableMasterSupermasterDirectivesAccess(MasterDirective directive) {
        aplicarMetadataEnableMasterSupermasterDirectivesAccess(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataEnableMasterSupermasterDirectivesAccess(MasterDirective directive) {
        directive.setResumen(MASTER_SUPERMASTER_DIRECTIVES_ACCESS_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.BOOLEANO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(MASTER_SUPERMASTER_DIRECTIVES_ACCESS_AYUDA);
    }

    private void ensureMpsSemanalDiasBloqueoEdicion() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION)
                .map(this::actualizarMetadataMpsSemanalDiasBloqueoEdicion)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.MPS_SEMANAL_DIAS_BLOQUEO_EDICION);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_DIAS_BLOQUEO_EDICION));
                    aplicarMetadataMpsSemanalDiasBloqueoEdicion(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataMpsSemanalDiasBloqueoEdicion(MasterDirective directive) {
        aplicarMetadataMpsSemanalDiasBloqueoEdicion(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataMpsSemanalDiasBloqueoEdicion(MasterDirective directive) {
        directive.setResumen(MPS_SEMANAL_DIAS_BLOQUEO_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(MPS_SEMANAL_DIAS_BLOQUEO_AYUDA);
    }

    private void ensureMpsSemanalPermitirAgregarTerminadosAprobado() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.MPS_SEMANAL_PERMITIR_AGREGAR_TERMINADOS_APROBADO)
                .map(this::actualizarMetadataMpsSemanalPermitirAgregarTerminadosAprobado)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.MPS_SEMANAL_PERMITIR_AGREGAR_TERMINADOS_APROBADO);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_MPS_SEMANAL_PERMITIR_AGREGAR_TERMINADOS_APROBADO));
                    aplicarMetadataMpsSemanalPermitirAgregarTerminadosAprobado(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataMpsSemanalPermitirAgregarTerminadosAprobado(MasterDirective directive) {
        aplicarMetadataMpsSemanalPermitirAgregarTerminadosAprobado(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataMpsSemanalPermitirAgregarTerminadosAprobado(MasterDirective directive) {
        directive.setResumen(MPS_SEMANAL_AGREGAR_TERMINADOS_APROBADO_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.BOOLEANO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(MPS_SEMANAL_AGREGAR_TERMINADOS_APROBADO_AYUDA);
    }

    private void ensureAreaOperativaNoiseEnabled() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.AREA_OPERATIVA_NOISE_ENABLED)
                .map(this::actualizarMetadataAreaOperativaNoiseEnabled)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.AREA_OPERATIVA_NOISE_ENABLED);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_NOISE_ENABLED));
                    aplicarMetadataAreaOperativaNoiseEnabled(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataAreaOperativaNoiseEnabled(MasterDirective directive) {
        aplicarMetadataAreaOperativaNoiseEnabled(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataAreaOperativaNoiseEnabled(MasterDirective directive) {
        directive.setResumen(AREA_OPERATIVA_NOISE_ENABLED_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.BOOLEANO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(AREA_OPERATIVA_NOISE_ENABLED_AYUDA);
    }

    private void ensureAreaOperativaNoiseIntervalMinutes() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.AREA_OPERATIVA_NOISE_INTERVAL_MINUTES)
                .map(this::actualizarMetadataAreaOperativaNoiseIntervalMinutes)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.AREA_OPERATIVA_NOISE_INTERVAL_MINUTES);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_NOISE_INTERVAL_MINUTES));
                    aplicarMetadataAreaOperativaNoiseIntervalMinutes(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataAreaOperativaNoiseIntervalMinutes(MasterDirective directive) {
        aplicarMetadataAreaOperativaNoiseIntervalMinutes(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataAreaOperativaNoiseIntervalMinutes(MasterDirective directive) {
        directive.setResumen(AREA_OPERATIVA_NOISE_INTERVAL_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(AREA_OPERATIVA_NOISE_INTERVAL_AYUDA);
    }

    private void ensureAreaOperativaNoiseSampleSeconds() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.AREA_OPERATIVA_NOISE_SAMPLE_SECONDS)
                .map(this::actualizarMetadataAreaOperativaNoiseSampleSeconds)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.AREA_OPERATIVA_NOISE_SAMPLE_SECONDS);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_NOISE_SAMPLE_SECONDS));
                    aplicarMetadataAreaOperativaNoiseSampleSeconds(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataAreaOperativaNoiseSampleSeconds(MasterDirective directive) {
        aplicarMetadataAreaOperativaNoiseSampleSeconds(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataAreaOperativaNoiseSampleSeconds(MasterDirective directive) {
        directive.setResumen(AREA_OPERATIVA_NOISE_SAMPLE_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(AREA_OPERATIVA_NOISE_SAMPLE_AYUDA);
    }

    private void ensureAreaOperativaInactivityAlertEnabled() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_ALERT_ENABLED)
                .map(this::actualizarMetadataAreaOperativaInactivityAlertEnabled)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_ALERT_ENABLED);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_INACTIVITY_ALERT_ENABLED));
                    aplicarMetadataAreaOperativaInactivityAlertEnabled(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataAreaOperativaInactivityAlertEnabled(MasterDirective directive) {
        aplicarMetadataAreaOperativaInactivityAlertEnabled(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataAreaOperativaInactivityAlertEnabled(MasterDirective directive) {
        directive.setResumen(AREA_OPERATIVA_INACTIVITY_ENABLED_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.BOOLEANO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(AREA_OPERATIVA_INACTIVITY_ENABLED_AYUDA);
    }

    private void ensureAreaOperativaInactivityThresholdMinutes() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_THRESHOLD_MINUTES)
                .map(this::actualizarMetadataAreaOperativaInactivityThresholdMinutes)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_THRESHOLD_MINUTES);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_INACTIVITY_THRESHOLD_MINUTES));
                    aplicarMetadataAreaOperativaInactivityThresholdMinutes(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataAreaOperativaInactivityThresholdMinutes(MasterDirective directive) {
        aplicarMetadataAreaOperativaInactivityThresholdMinutes(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataAreaOperativaInactivityThresholdMinutes(MasterDirective directive) {
        directive.setResumen(AREA_OPERATIVA_INACTIVITY_THRESHOLD_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(AREA_OPERATIVA_INACTIVITY_THRESHOLD_AYUDA);
    }

    private void ensureAreaOperativaInactivityCheckIntervalMinutes() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_CHECK_INTERVAL_MINUTES)
                .map(this::actualizarMetadataAreaOperativaInactivityCheckIntervalMinutes)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.AREA_OPERATIVA_INACTIVITY_CHECK_INTERVAL_MINUTES);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_INACTIVITY_CHECK_INTERVAL_MINUTES));
                    aplicarMetadataAreaOperativaInactivityCheckIntervalMinutes(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataAreaOperativaInactivityCheckIntervalMinutes(MasterDirective directive) {
        aplicarMetadataAreaOperativaInactivityCheckIntervalMinutes(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataAreaOperativaInactivityCheckIntervalMinutes(MasterDirective directive) {
        directive.setResumen(AREA_OPERATIVA_INACTIVITY_INTERVAL_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(AREA_OPERATIVA_INACTIVITY_INTERVAL_AYUDA);
    }

    private void ensureAreaOperativaPanelHistoricoToggleEnabled() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_ENABLED)
                .map(this::actualizarMetadataAreaOperativaPanelHistoricoToggleEnabled)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_ENABLED);
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_ENABLED));
                    aplicarMetadataAreaOperativaPanelHistoricoToggleEnabled(directive);
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }

    private MasterDirective actualizarMetadataAreaOperativaPanelHistoricoToggleEnabled(MasterDirective directive) {
        aplicarMetadataAreaOperativaPanelHistoricoToggleEnabled(directive);
        return masterDirectiveRepo.save(directive);
    }

    private void aplicarMetadataAreaOperativaPanelHistoricoToggleEnabled(MasterDirective directive) {
        directive.setResumen(AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_RESUMEN);
        directive.setTipoDato(MasterDirective.TipoDato.BOOLEANO);
        directive.setGrupo(MasterDirective.GRUPO.FLEXIBILIDAD_CONTROL);
        directive.setAyuda(AREA_OPERATIVA_PANEL_HISTORICO_TOGGLE_AYUDA);
    }
}
