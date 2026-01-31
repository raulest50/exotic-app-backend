package exotic.app.planta.service.users;

import jakarta.mail.MessagingException;
import exotic.app.planta.config.PasswordConfig;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.users.auth.PasswordResetToken;
import exotic.app.planta.repo.usuarios.PasswordResetTokenRepository;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.security.JwtTokenProvider;
import exotic.app.planta.service.commons.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for authentication-related operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    /**
     * Authenticates a user and returns a JWT token
     *
     * @param username the username
     * @param password the password
     * @return a map containing the JWT token and username
     * @throws AuthenticationException if authentication fails
     */
    @Transactional
    public Map<String, String> authenticateUser(String username, String password) {
        try {
            // Verify that the user exists and is active before authenticating
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.info("Login attempt for non-existent user: {}", username);
                throw new BadCredentialsException("User not found");
            }

            User user = userOpt.get();

            // Allow login for master and super_master even if inactive
            if (!"master".equalsIgnoreCase(user.getUsername()) && !"super_master".equalsIgnoreCase(user.getUsername()) && user.getEstado() != 1) {
                log.info("Login attempt for inactive user: {}", username);

                throw new BadCredentialsException("User is inactive");
            }

            // Create authentication object
            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(
                    username,
                    password
            );

            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(authRequest);

            // Set the authentication in the SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Generate JWT token
            String jwt = tokenProvider.generateToken(authentication);

            // Prepare response
            Map<String, String> response = new HashMap<>();
            response.put("token", jwt);
            response.put("username", username);

            return response;
        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage());
            throw new AuthenticationException("Authentication failed: " + e.getMessage()) {};
        }
    }

    /**
     * Requests a password reset for a user with the given username.
     * If the user exists, a password reset token is created and an email is sent to the user.
     *
     * @param username the username (used as email) of the user
     * @return true if the request was successful, false otherwise
     */
    @Transactional
    public boolean requestPasswordReset(String username) {
        Optional<User> userOpt = userRepository.findByEmail(username);

        if (userOpt.isEmpty()) {
            // User not found, but don't reveal this information for security reasons
            log.info("Password reset requested for non-existent user: {}", username);
            return false;
        }

        User user = userOpt.get();

        // Only allow reset for active users
        if (user.getEstado() != 1) {
            log.info("Password reset requested for inactive user: {}", username);
            return false;
        }

        // Delete any existing tokens for this user
        passwordResetTokenRepository.deleteByUser(user);

        // Create a new token
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        passwordResetTokenRepository.save(resetToken);

        // Send email with reset link
        try {
            String resetUrl = getDomain() + "/reset-password?token=" + token;
            String emailContent = createPasswordResetEmailContent(user.getUsername(), user.getNombreCompleto(), resetUrl);
            emailService.sendHtmlEmail(user.getEmail(), "Solicitud de Restablecimiento de Contraseña", emailContent);
            log.info("Password reset email sent to: {}", user.getUsername());
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Determina la URL base dependiendo de si la aplicación está corriendo
     * en entorno local o de producción.
     * @return La URL base para construir enlaces
     */
    public String getDomain() {
        // Verificar si estamos en producción usando la variable de entorno
        if (System.getenv("PRODUCTION") != null && System.getenv("PRODUCTION").equals("TRUE")) {
            // Entorno de producción
            return "https://lac-manufacture-frontend.onrender.com";
        } else {
            // Entorno local/desarrollo
            return "http://localhost:5173";
        }
    }

    /**
     * Sets a new password for a user using a password reset token.
     *
     * @param token the password reset token
     * @param newPassword the new password
     * @return true if the password was successfully reset, false otherwise
     */
    @Transactional
    public boolean setNewPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token);

        if (resetToken == null || resetToken.isExpired()) {
            log.info("Invalid or expired password reset token: {}", token);
            return false;
        }

        User user = resetToken.getUser();

        // Encode the new password
        String encodedPassword = PasswordConfig.encodePassword(newPassword, user.getUsername());
        user.setPassword(encodedPassword);

        // Save the user with the new password
        userRepository.save(user);

        // Delete the used token
        passwordResetTokenRepository.delete(resetToken);

        log.info("Password successfully reset for user: {}", user.getUsername());
        return true;
    }

    /**
     * Creates the HTML content for the password reset email.
     *
     * @param username the username of the user
     * @param nombreCompleto the full name of the user
     * @param resetUrl the URL to reset the password
     * @return the HTML content for the email
     */
    private String createPasswordResetEmailContent(String username, String nombreCompleto, String resetUrl) {
        return "<html>"
                + "<body style='font-family: Arial, sans-serif;'>"
                + "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;'>"
                + "<h2 style='color: #333;'>Solicitud de Restablecimiento de Contraseña</h2>"
                + "<p>Hola " + nombreCompleto + ",</p>"
                + "<p>Has solicitado restablecer tu contraseña. Por favor, haz clic en el enlace a continuación para establecer una nueva contraseña:</p>"
                + "<p><a href='" + resetUrl + "' style='display: inline-block; padding: 10px 20px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 4px;'>Restablecer Contraseña</a></p>"
                + "<p>Este enlace expirará en 1 hora.</p>"
                + "<div style='background-color: #f8f9fa; border-radius: 4px; padding: 10px; margin: 15px 0; border-left: 4px solid #4CAF50;'>"
                + "<p style='margin: 0; font-weight: bold;'>Información de la cuenta:</p>"
                + "<p style='margin: 5px 0;'>Usuario: <span style='background-color: #e8f5e9; padding: 2px 6px; border-radius: 3px; font-family: monospace;'>" + username + "</span></p>"
                + "</div>"
                + "<p>Si no solicitaste restablecer tu contraseña, por favor ignora este correo o contacta a soporte si tienes alguna inquietud.</p>"
                + "<p>Saludos,<br>Equipo de LA Cosmetics</p>"
                + "</div>"
                + "</body>"
                + "</html>";
    }
}
