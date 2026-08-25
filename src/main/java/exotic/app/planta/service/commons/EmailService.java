package exotic.app.planta.service.commons;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

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

    /**
     * Send a simple text email
     *
     * @param to      The recipient email address
     * @param subject The email subject
     * @param text    The email body text
     */
    public void sendSimpleEmail(String to, String subject, String text) {
        log.info("Sending simple email to: {}", to);
        SimpleMailMessage message = new SimpleMailMessage();
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
        log.info("Sending simple email to: {}, CC: {}", to, (Object) cc);
        SimpleMailMessage message = new SimpleMailMessage();
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
        log.info("Sending HTML email to: {}", to);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

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
        log.info("Sending email with attachment to: {}", to);
        MimeMessage message = mailSender.createMimeMessage();
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
        log.info("Preparando email con adjunto para: {}, asunto: {}", to, subject);
        log.info("Detalles del adjunto: nombre={}, tamaño={} bytes, tipo={}", 
                 attachment.getOriginalFilename(), attachment.getSize(), attachment.getContentType());

        try {
            MimeMessage message = mailSender.createMimeMessage();
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
        log.info("Preparando email con adjunto para: {}, CC: {}, asunto: {}", to, (Object) cc, subject);
        log.info("Detalles del adjunto: nombre={}, tamaño={} bytes, tipo={}", 
                 attachment.getOriginalFilename(), attachment.getSize(), attachment.getContentType());

        try {
            MimeMessage message = mailSender.createMimeMessage();
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
        log.info("Sending email to multiple recipients: {}", (Object) to);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Email sent successfully to multiple recipients");
    }
}
