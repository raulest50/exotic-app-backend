package exotic.app.planta.service.commons;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Map;

/**
 * Service for sending emails.
 * This service provides methods to send different types of emails:
 * - Simple text emails
 * - HTML emails
 * - Emails with attachments
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled = true;

    @Value("${spring.mail.properties.mail.from:${spring.mail.username:}}")
    private String fromAddress;

    private void requireMailEnabled() {
        if (!mailEnabled) {
            throw new MailSendException("El envío de correo está deshabilitado en este entorno");
        }
    }

    /**
     * Send a simple text email
     *
     * @param to      The recipient email address
     * @param subject The email subject
     * @param text    The email body text
     */
    public void sendSimpleEmail(String to, String subject, String text) {
        requireMailEnabled();
        log.info("Sending simple email to: {}", to);
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Simple email sent successfully to: {}", to);
    }

    /**
     * Send a simple text email with CC recipients.
     *
     * @param to      The recipient email address
     * @param cc      Array of CC email addresses
     * @param subject The email subject
     * @param text    The email body text
     */
    public void sendSimpleEmailWithCC(String to, String[] cc, String subject, String text) {
        requireMailEnabled();
        log.info("Sending simple email to: {}, CC: {}", to, (Object) cc);
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
        message.setTo(to);
        if (cc != null && cc.length > 0) {
            message.setCc(cc);
        }
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Simple email sent successfully to: {}, CC: {}", to, (Object) cc);
    }

    /**
     * Send an HTML email
     *
     * @param to          The recipient email address
     * @param subject     The email subject
     * @param htmlContent The email body as HTML
     * @throws MessagingException If there is an error creating or sending the message
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        sendHtmlEmail(to, subject, htmlContent, Map.of());
    }

    /** Sends HTML with CID images provided by the message renderer. */
    public void sendHtmlEmail(
            String to, String subject, String htmlContent, Map<String, Resource> inlineImages
    ) throws MessagingException {
        requireMailEnabled();
        log.info("Sending HTML email to: {}", to);
        MimeMessage message = mailSender.createMimeMessage();
        if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        // MIME readers resolve CID references only after the HTML body has been set.
        for (Map.Entry<String, Resource> image : Objects.requireNonNull(inlineImages).entrySet()) {
            helper.addInline(image.getKey(), image.getValue());
        }

        mailSender.send(message);
        log.info("HTML email sent successfully to: {}", to);
    }

    /**
     * Send an email with attachment from a file path
     *
     * @param to             The recipient email address
     * @param subject        The email subject
     * @param text           The email body text
     * @param attachmentPath The path to the attachment file
     * @param attachmentName The name to give the attachment in the email
     * @throws MessagingException If there is an error creating or sending the message
     */
    public void sendEmailWithAttachment(String to, String subject, String text, String attachmentPath, String attachmentName) throws MessagingException {
        requireMailEnabled();
        log.info("Sending email with attachment to: {}", to);
        MimeMessage message = mailSender.createMimeMessage();
        if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text);

        FileSystemResource file = new FileSystemResource(new File(attachmentPath));
        helper.addAttachment(attachmentName, file);

        mailSender.send(message);
        log.info("Email with attachment sent successfully to: {}", to);
    }

    /**
     * Send an email with attachment from a MultipartFile
     *
     * @param to         The recipient email address
     * @param subject    The email subject
     * @param text       The email body text
     * @param attachment The MultipartFile attachment
     * @throws MessagingException If there is an error creating or sending the message
     * @throws IOException        If there is an error reading the attachment
     */
    public void sendEmailWithAttachment(String to, String subject, String text, MultipartFile attachment) throws MessagingException, IOException {
        requireMailEnabled();
        log.info("Preparando email con adjunto para: {}, asunto: {}", to, subject);
        log.info("Detalles del adjunto: nombre={}, tamaño={} bytes, tipo={}", 
                 attachment.getOriginalFilename(), attachment.getSize(), attachment.getContentType());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);

            helper.addAttachment(
                Objects.requireNonNull(attachment.getOriginalFilename()),
                new ByteArrayResource(attachment.getBytes())
            );

            log.info("Enviando email a: {}", to);
            mailSender.send(message);
            log.info("Email con adjunto enviado exitosamente a: {}", to);
        } catch (MessagingException e) {
            log.error("Error al crear o enviar el mensaje: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            log.error("Error al procesar el archivo adjunto: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al enviar email: {}", e.getMessage(), e);
            throw new MessagingException("Error inesperado al enviar email: " + e.getMessage(), e);
        }
    }

    /**
     * Send an email with attachment and CC recipients from a MultipartFile
     *
     * @param to         The recipient email address
     * @param cc         Array of CC email addresses
     * @param subject    The email subject
     * @param text       The email body text
     * @param attachment The MultipartFile attachment
     * @throws MessagingException If there is an error creating or sending the message
     * @throws IOException        If there is an error reading the attachment
     */
    public void sendEmailWithAttachmentAndCC(String to, String[] cc, String subject, String text, MultipartFile attachment) throws MessagingException, IOException {
        requireMailEnabled();
        log.info("Preparando email con adjunto para: {}, CC: {}, asunto: {}", to, (Object) cc, subject);
        log.info("Detalles del adjunto: nombre={}, tamaño={} bytes, tipo={}", 
                 attachment.getOriginalFilename(), attachment.getSize(), attachment.getContentType());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            if (cc != null && cc.length > 0) {
                helper.setCc(cc);
                log.info("Configurados {} destinatarios en copia", cc.length);
            }
            helper.setSubject(subject);
            helper.setText(text);

            helper.addAttachment(
                Objects.requireNonNull(attachment.getOriginalFilename()),
                new ByteArrayResource(attachment.getBytes())
            );

            log.info("Enviando email a: {} con CC: {}", to, (Object) cc);
            mailSender.send(message);
            log.info("Email con adjunto enviado exitosamente a: {} con CC: {}", to, (Object) cc);
        } catch (MessagingException e) {
            log.error("Error al crear o enviar el mensaje: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            log.error("Error al procesar el archivo adjunto: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al enviar email: {}", e.getMessage(), e);
            throw new MessagingException("Error inesperado al enviar email: " + e.getMessage(), e);
        }
    }

    /**
     * Send an email with multiple recipients
     *
     * @param to      Array of recipient email addresses
     * @param subject The email subject
     * @param text    The email body text
     */
    public void sendEmailToMultipleRecipients(String[] to, String subject, String text) {
        requireMailEnabled();
        log.info("Sending email to multiple recipients: {}", (Object) to);
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Email sent successfully to multiple recipients");
    }
}
