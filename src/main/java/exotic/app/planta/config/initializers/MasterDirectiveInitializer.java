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

    private final MasterDirectiveRepo masterDirectiveRepo;

    public void initializeMasterDirectives() {
        ensureLimiteRecepcionesParcialesOcm();
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
}
