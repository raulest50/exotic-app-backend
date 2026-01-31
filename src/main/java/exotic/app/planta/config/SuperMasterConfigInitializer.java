package exotic.app.planta.config;

import exotic.app.planta.model.master.configs.SuperMasterConfig;
import exotic.app.planta.repo.master.configs.SuperMasterConfigRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperMasterConfigInitializer {

    private final SuperMasterConfigRepo superMasterConfigRepo;

    /**
     * Creates the singleton SuperMasterConfig row if none exists (all flags enabled by default).
     */
    public void initializeSuperMasterConfig() {
        if (superMasterConfigRepo.count() == 0) {
            SuperMasterConfig config = SuperMasterConfig.builder()
                    .habilitarEliminacionForzada(true)
                    .habilitarCargaMasiva(true)
                    .habilitarAjustesInventario(true)
                    .build();
            superMasterConfigRepo.save(config);
        }
    }
}
