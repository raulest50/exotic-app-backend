package exotic.app.planta.service.commons;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailServiceTest {

    @Test
    void sendSimpleEmailWithCC_sendsTextMessageWithCopiesAndNoAttachment() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailService service = new EmailService(mailSender);

        service.sendSimpleEmailWithCC(
                "proveedor@example.com",
                new String[]{"compras@example.com", "produccion@example.com"},
                "Cancelación de OCM",
                "Contenido del correo"
        );

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertArrayEquals(new String[]{"proveedor@example.com"}, message.getTo());
        assertArrayEquals(new String[]{"compras@example.com", "produccion@example.com"}, message.getCc());
        assertEquals("Cancelación de OCM", message.getSubject());
        assertEquals("Contenido del correo", message.getText());
    }
}
