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
    private static final String DISPENSACION_NO_BLOQUEA_AYUDA = "Cuando esta activa, Almacen General se marca automaticamente como completado solo a nivel de seguimiento de proceso al crear la orden de produccion. No crea transacciones de almacen, no descuenta inventario y no acredita la dispensacion real.";
    private static final String MASTER_SUPERMASTER_DIRECTIVES_ACCESS_RESUMEN = "Permite que master vea y entre al modulo de Directivas Super Master";
    private static final String MASTER_SUPERMASTER_DIRECTIVES_ACCESS_AYUDA = "Cuando esta activa, el usuario master puede ver el acceso en Inicio y entrar a la ruta de Directivas Super Master. No agrega proteccion adicional sobre los endpoints API.";

    private final MasterDirectiveRepo masterDirectiveRepo;

    public void initializeMasterDirectives() {
        ensureLimiteRecepcionesParcialesOcm();
        ensureDispensacionNoBloqueaInicioProduccion();
        ensureEnableMasterSupermasterDirectivesAccess();
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
}
