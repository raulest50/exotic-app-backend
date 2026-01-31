package exotic.app.planta.service.master.configs;

import exotic.app.planta.config.PasswordConfig;
import exotic.app.planta.model.master.configs.SuperMasterConfig;
import exotic.app.planta.model.master.configs.SuperMasterVerificationCode;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.master.configs.SuperMasterConfigRepo;
import exotic.app.planta.repo.master.configs.SuperMasterVerificationCodeRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.EmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperMasterOpsService {

    private static final int CODE_EXPIRY_MINUTES = 10;

    private final SuperMasterConfigRepo superMasterConfigRepo;
    private final SuperMasterVerificationCodeRepo verificationCodeRepo;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public Optional<SuperMasterConfig> getConfig() {
        return superMasterConfigRepo.findFirstByOrderByIdAsc();
    }

    @Transactional
    public SuperMasterConfig updateConfig(SuperMasterConfig config) {
        SuperMasterConfig existing = superMasterConfigRepo.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new RuntimeException("SuperMasterConfig not initialized"));
        existing.setHabilitarEliminacionForzada(config.isHabilitarEliminacionForzada());
        existing.setHabilitarCargaMasiva(config.isHabilitarCargaMasiva());
        existing.setHabilitarAjustesInventario(config.isHabilitarAjustesInventario());
        return superMasterConfigRepo.save(existing);
    }

    @Transactional
    public void sendVerificationCode(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        verificationCodeRepo.deleteByEmail(email);
        SuperMasterVerificationCode entity = SuperMasterVerificationCode.builder()
                .email(email.trim())
                .code(code)
                .expiryDate(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES))
                .build();
        verificationCodeRepo.save(entity);
        String html = createVerificationEmailContent(code);
        try {
            emailService.sendHtmlEmail(email.trim(), "Código de verificación - Super Master", html);
            log.info("Verification code sent to {}", email);
        } catch (MessagingException e) {
            log.error("Failed to send verification email: {}", e.getMessage());
            throw new RuntimeException("No se pudo enviar el correo de verificación", e);
        }
    }

    @Transactional
    public boolean completeProfile(String email, String code, String newPassword) {
        if (email == null || email.isBlank() || code == null || code.isBlank() || newPassword == null || newPassword.length() < 8) {
            return false;
        }
        Optional<SuperMasterVerificationCode> opt = verificationCodeRepo.findByEmailAndCode(email.trim(), code.trim());
        if (opt.isEmpty()) {
            log.info("Invalid verification code for email {}", email);
            return false;
        }
        SuperMasterVerificationCode vc = opt.get();
        if (vc.isExpired()) {
            verificationCodeRepo.delete(vc);
            log.info("Expired verification code for email {}", email);
            return false;
        }
        User superMaster = userRepository.findByUsername("super_master")
                .orElseThrow(() -> new RuntimeException("super_master user not found"));
        superMaster.setEmail(email.trim());
        superMaster.setPassword(PasswordConfig.encodePassword(newPassword, "super_master"));
        userRepository.save(superMaster);
        verificationCodeRepo.delete(vc);
        log.info("Super master profile completed for email {}", email);
        return true;
    }

    private String createVerificationEmailContent(String code) {
        return "<html>"
                + "<body style='font-family: Arial, sans-serif;'>"
                + "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>"
                + "<h2 style='color: #333;'>Código de verificación</h2>"
                + "<p>Tu código de verificación para completar el perfil de Super Master es:</p>"
                + "<p style='font-size: 24px; font-weight: bold; letter-spacing: 4px; background-color: #e8f5e9; padding: 10px; border-radius: 4px;'>" + code + "</p>"
                + "<p>Este código expira en " + CODE_EXPIRY_MINUTES + " minutos.</p>"
                + "<p>Si no solicitaste este código, ignora este correo.</p>"
                + "</div>"
                + "</body>"
                + "</html>";
    }
}
