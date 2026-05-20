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

    private final MasterDirectiveRepo masterDirectiveRepo;

    public void initializeMasterDirectives() {
        ensureLimiteRecepcionesParcialesOcm();
    }

    private void ensureLimiteRecepcionesParcialesOcm() {
        masterDirectiveRepo.findByNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM)
                .orElseGet(() -> {
                    MasterDirective directive = new MasterDirective();
                    directive.setNombre(MasterDirectiveKeys.LIMITE_RECEPCIONES_PARCIALES_OCM);
                    directive.setResumen("Limite de recepciones parciales permitidas por OCM");
                    directive.setValor(String.valueOf(MasterDirectiveKeys.DEFAULT_LIMITE_RECEPCIONES_PARCIALES_OCM));
                    directive.setTipoDato(MasterDirective.TipoDato.NUMERO);
                    directive.setGrupo(MasterDirective.GRUPO.COMPRAS_ALMACEN);
                    directive.setAyuda("Define cuantas transacciones de ingreso a almacen pueden registrarse para una misma orden de compra de materiales.");
                    log.info("Creando directiva maestra por defecto: {}", directive.getNombre());
                    return masterDirectiveRepo.save(directive);
                });
    }
}
