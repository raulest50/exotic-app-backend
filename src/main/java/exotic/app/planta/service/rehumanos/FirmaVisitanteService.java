package exotic.app.planta.service.rehumanos;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/** Captura grafica local a Gestion Humana; no autentica al visitante. */
@Service
public class FirmaVisitanteService {
    private static final int MAX_BYTES = 128 * 1024;
    private static final int MAX_BASE64 = 4 * ((MAX_BYTES + 2) / 3);

    public Firma validar(String base64, Boolean autoriza) {
        if (base64 == null && !Boolean.TRUE.equals(autoriza)) return null;
        if (!Boolean.TRUE.equals(autoriza) || base64 == null || base64.isBlank() || base64.length() > MAX_BASE64) {
            throw new IllegalArgumentException("La firma requiere autorización específica e imagen válida.");
        }
        byte[] original;
        try { original = Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Firma PNG invalida."); }
        if (original.length == 0 || original.length > MAX_BYTES) throw new IllegalArgumentException("Firma demasiado grande.");
        BufferedImage imagen = null;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Firma PNG invalida.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!"png".equalsIgnoreCase(reader.getFormatName()) || reader.getWidth(0) != 900 || reader.getHeight(0) != 300) {
                    throw new IllegalArgumentException("La firma debe ser PNG de 900 por 300 pixeles.");
                }
                imagen = reader.read(0);
            } finally { reader.dispose(); }
            if (imagen == null) throw new IllegalArgumentException("Firma PNG invalida.");
            BufferedImage limpia = new BufferedImage(900, 300, BufferedImage.TYPE_INT_RGB);
            try {
                Graphics2D graphics = limpia.createGraphics();
                try { graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 900, 300); graphics.drawImage(imagen, 0, 0, null); }
                finally { graphics.dispose(); }
                int trazos = 0;
                for (int y = 0; y < 300; y++) for (int x = 0; x < 900; x++) {
                    int rgb = limpia.getRGB(x, y);
                    if (((rgb >> 16) & 255) < 180 && ((rgb >> 8) & 255) < 180 && (rgb & 255) < 180) trazos++;
                }
                if (trazos < 25) throw new IllegalArgumentException("La firma no contiene trazos suficientes.");
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(limpia, "png", output)) throw new IllegalArgumentException("Firma PNG invalida.");
                byte[] contenido = output.toByteArray();
                if (contenido.length > MAX_BYTES) throw new IllegalArgumentException("Firma demasiado grande.");
                return new Firma(contenido, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido)));
            } finally { limpia.flush(); }
        } catch (IOException e) { throw new IllegalArgumentException("Firma PNG invalida."); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 no disponible", e); }
        finally { if (imagen != null) imagen.flush(); }
    }

    public record Firma(byte[] contenido, String sha256) { }
}
