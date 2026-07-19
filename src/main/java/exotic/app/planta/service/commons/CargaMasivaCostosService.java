package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.CargaCostosDTOs;
import exotic.app.planta.model.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CargaMasivaCostosService {
    private final CargaCostosExcelParser parser;
    private final CargaCostosWorkflowService workflow;
    private final CargaCostosLifecycleService lifecycle;

    public CargaCostosDTOs.PreparacionResponse preparar(MultipartFile file, String motivo, User usuario) {
        String safeReason = validateReason(motivo);
        CargaCostosExcelParser.ParsedWorkbook parsed = parser.parse(file);
        return workflow.crearPreparacion(
                parsed,
                safeFilename(file.getOriginalFilename()),
                sha256(file),
                safeReason,
                usuario);
    }

    public CargaCostosDTOs.ItemsPageResponse listarItems(
            UUID loteId,
            User usuario,
            int page,
            int size
    ) {
        lifecycle.expirarSiCorresponde(loteId, usuario.getId());
        return workflow.listarItems(loteId, usuario, page, size);
    }

    public CargaCostosDTOs.TokenResponse generarToken(UUID loteId, User usuario) {
        lifecycle.expirarSiCorresponde(loteId, usuario.getId());
        return workflow.generarToken(loteId, usuario);
    }

    public CargaCostosDTOs.ConfirmacionResponse confirmar(UUID loteId, String token, User usuario) {
        lifecycle.expirarSiCorresponde(loteId, usuario.getId());
        return workflow.confirmar(loteId, token, usuario);
    }

    public void cancelar(UUID loteId, User usuario) {
        lifecycle.expirarSiCorresponde(loteId, usuario.getId());
        workflow.cancelar(loteId, usuario);
    }

    private String validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("El motivo no puede superar 500 caracteres");
        }
        return trimmed;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "carga_costos.xlsx";
        String safe = filename.replaceAll("[\\\\/\\r\\n]", "_").trim();
        return safe.substring(0, Math.min(safe.length(), 255));
    }

    private String sha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("No fue posible calcular la huella del archivo", ex);
        }
    }
}
