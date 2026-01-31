package exotic.app.planta.repo.master.configs;

import exotic.app.planta.model.master.configs.SuperMasterVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuperMasterVerificationCodeRepo extends JpaRepository<SuperMasterVerificationCode, Long> {

    Optional<SuperMasterVerificationCode> findByEmailAndCode(String email, String code);

    @Modifying
    @Query("DELETE FROM SuperMasterVerificationCode v WHERE v.email = :email")
    void deleteByEmail(@Param("email") String email);
}
