package exotic.app.planta.repo.master.configs;

import exotic.app.planta.model.master.configs.SuperMasterConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuperMasterConfigRepo extends JpaRepository<SuperMasterConfig, Long> {

    java.util.Optional<SuperMasterConfig> findFirstByOrderByIdAsc();
}
