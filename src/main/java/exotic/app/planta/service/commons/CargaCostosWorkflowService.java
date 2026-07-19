package exotic.app.planta.service.commons;

import exotic.app.planta.model.commons.dto.CargaCostosDTOs;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.costos.CargaCostosItem;
import exotic.app.planta.model.producto.costos.CargaCostosLote;
import exotic.app.planta.model.producto.costos.ProductoCostoOrigen;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.producto.ProductoRepo;
import exotic.app.planta.repo.producto.costos.CargaCostosItemRepo;
import exotic.app.planta.repo.producto.costos.CargaCostosLoteRepo;
import exotic.app.planta.service.productos.CostoVersionConflictException;
import exotic.app.planta.service.productos.ProductoCostoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CargaCostosWorkflowService {
    private static final int MAX_TOKEN_ATTEMPTS = 5;
    private static final int MAX_TOKEN_GENERATIONS = 3;
    private static final int PREPARATION_MINUTES = 30;
    private static final int TOKEN_MINUTES = 10;
    private static final int TOKEN_COOLDOWN_SECONDS = 30;

    private final ProductoRepo productoRepo;
    private final CargaCostosLoteRepo loteRepo;
    private final CargaCostosItemRepo itemRepo;
    private final ProductoCostoService productoCostoService;
    private final Clock applicationClock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder tokenEncoder = new BCryptPasswordEncoder();

    @Transactional
    public CargaCostosDTOs.PreparacionResponse crearPreparacion(
            CargaCostosExcelParser.ParsedWorkbook parsed,
            String filename,
            String sha256,
            String motivo,
            User usuario
    ) {
        Set<String> ids = parsed.filas().stream()
                .map(CargaCostosExcelParser.ParsedRow::productoId)
                .collect(Collectors.toCollection(TreeSet::new));
        Map<String, Producto> productos = productoRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getProductoId, Function.identity()));

        List<CargaCostosDTOs.ErrorFila> errors = new ArrayList<>();
        for (CargaCostosExcelParser.ParsedRow row : parsed.filas()) {
            Producto producto = productos.get(row.productoId());
            if (producto == null) {
                errors.add(error(row, "CODIGO", "Producto no encontrado"));
            } else if (!(producto instanceof Material)) {
                errors.add(error(row, "CODIGO", "El producto no es un Material"));
            }
        }
        if (!errors.isEmpty()) {
            throw new CargaCostosValidationException(
                    "La validacion contra el catalogo encontro errores",
                    errors,
                    parsed.advertencias());
        }

        LocalDateTime now = LocalDateTime.now(applicationClock);
        CargaCostosLote lote = new CargaCostosLote();
        lote.setId(UUID.randomUUID());
        lote.setUsuario(usuario);
        lote.setUsuarioUsername(snapshotUsername(usuario));
        lote.setNombreArchivo(filename);
        lote.setArchivoSha256(sha256);
        lote.setMotivo(motivo);
        lote.setEstado(CargaCostosLote.Estado.PREPARADO);
        lote.setCreadoEn(now);
        lote.setExpiraEn(now.plusMinutes(PREPARATION_MINUTES));
        lote.setTotalFilas(parsed.totalFilas());
        lote.setTotalCandidatas(parsed.filas().size());
        lote.setTotalOmitidas(parsed.totalOmitidas());

        int changes = 0;
        int unchanged = 0;
        int descriptionDifferences = 0;
        for (CargaCostosExcelParser.ParsedRow row : parsed.filas()) {
            Producto producto = productos.get(row.productoId());
            BigDecimal current = productoCostoService.normalizar(producto.getCosto());
            boolean changesCost = current.compareTo(row.costo()) != 0;
            boolean descriptionMatches = descriptionsMatch(row.descripcion(), producto.getNombre());
            if (changesCost) changes++; else unchanged++;
            if (!descriptionMatches) descriptionDifferences++;

            CargaCostosItem item = new CargaCostosItem();
            item.setLote(lote);
            item.setFilaExcel(row.fila());
            item.setProductoId(producto.getProductoId());
            item.setProductoNombre(producto.getNombre());
            item.setTipoProducto(producto.getTipo_producto());
            item.setDescripcionExcel(row.descripcion());
            item.setDescripcionCoincide(descriptionMatches);
            item.setCostoAnterior(current);
            item.setCostoNuevo(row.costo());
            item.setCostoVersionAnterior(producto.getCostoVersion());
            lote.getItems().add(item);
        }

        lote.setTotalActualizadas(changes);
        lote.setTotalSinCambio(unchanged);
        loteRepo.save(lote);

        List<String> warnings = new ArrayList<>(parsed.advertencias());
        if (descriptionDifferences > 0) {
            warnings.add(descriptionDifferences
                    + " materiales tienen una descripcion diferente entre el Excel y el sistema.");
        }
        return preparationResponse(lote, warnings);
    }

    @Transactional(readOnly = true)
    public CargaCostosDTOs.ItemsPageResponse listarItems(UUID loteId, User usuario, int page, int size) {
        CargaCostosLote lote = requireOwnedLote(loteId, usuario, false);
        requirePrepared(lote);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<CargaCostosItem> result = itemRepo.findByLote_IdOrderByFilaExcelAsc(
                loteId, PageRequest.of(safePage, safeSize));
        return new CargaCostosDTOs.ItemsPageResponse(
                result.getContent().stream().map(this::toPreview).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public CargaCostosDTOs.TokenResponse generarToken(UUID loteId, User usuario) {
        CargaCostosLote lote = requireOwnedLote(loteId, usuario, true);
        requirePrepared(lote);
        if (lote.getTotalActualizadas() == 0) {
            throw new CargaCostosStateException("SIN_CAMBIOS", "La preparacion no contiene costos por actualizar");
        }
        if (lote.getGeneracionesToken() >= MAX_TOKEN_GENERATIONS) {
            throw new CargaCostosStateException(
                    "LIMITE_TOKENS", "Se alcanzo el maximo de tokens para esta preparacion");
        }

        LocalDateTime now = LocalDateTime.now(applicationClock);
        if (lote.getUltimaGeneracionTokenEn() != null
                && lote.getUltimaGeneracionTokenEn().plusSeconds(TOKEN_COOLDOWN_SECONDS).isAfter(now)) {
            throw new CargaCostosStateException(
                    "TOKEN_COOLDOWN", "Espere 30 segundos antes de generar otro token");
        }

        String token = Integer.toString(1_000 + secureRandom.nextInt(9_000));
        lote.setTokenHash(tokenEncoder.encode(token));
        LocalDateTime tokenExpiry = now.plusMinutes(TOKEN_MINUTES);
        lote.setTokenExpiraEn(tokenExpiry.isBefore(lote.getExpiraEn())
                ? tokenExpiry
                : lote.getExpiraEn());
        lote.setIntentosToken(0);
        lote.setGeneracionesToken(lote.getGeneracionesToken() + 1);
        lote.setUltimaGeneracionTokenEn(now);
        return new CargaCostosDTOs.TokenResponse(
                token,
                toOffset(lote.getTokenExpiraEn()),
                MAX_TOKEN_GENERATIONS - lote.getGeneracionesToken(),
                MAX_TOKEN_ATTEMPTS);
    }

    @Transactional(noRollbackFor = CargaCostosTokenException.class)
    public CargaCostosDTOs.ConfirmacionResponse confirmar(UUID loteId, String token, User usuario) {
        CargaCostosLote lote = requireOwnedLote(loteId, usuario, true);
        if (lote.getEstado() == CargaCostosLote.Estado.EJECUTADO) {
            return successResponse(lote, "La carga ya habia sido ejecutada");
        }
        requirePrepared(lote);

        LocalDateTime now = LocalDateTime.now(applicationClock);
        if (lote.getTokenHash() == null || lote.getTokenExpiraEn() == null
                || !lote.getTokenExpiraEn().isAfter(now)) {
            throw new CargaCostosStateException(
                    "TOKEN_EXPIRADO", "El token no existe o expiro; genere uno nuevo");
        }

        if (!tokenEncoder.matches(token, lote.getTokenHash())) {
            lote.setIntentosToken(lote.getIntentosToken() + 1);
            int remaining = Math.max(0, MAX_TOKEN_ATTEMPTS - lote.getIntentosToken());
            boolean blocked = remaining == 0;
            if (blocked) {
                lote.setEstado(CargaCostosLote.Estado.BLOQUEADO);
                lote.setTokenHash(null);
                lote.setTokenExpiraEn(null);
            }
            loteRepo.save(lote);
            throw new CargaCostosTokenException(
                    blocked ? "La preparacion fue bloqueada" : "El token es incorrecto",
                    remaining,
                    blocked);
        }

        List<CargaCostosItem> items = itemRepo.findByLote_IdOrderByProductoIdAsc(loteId);
        Map<String, Producto> lockedProducts = new LinkedHashMap<>();
        for (CargaCostosItem item : items) {
            Producto producto = productoRepo.findByProductoIdForUpdate(item.getProductoId())
                    .orElseThrow(() -> new CostoVersionConflictException(item.getProductoId()));
            BigDecimal current = productoCostoService.normalizar(producto.getCosto());
            if (!(producto instanceof Material)
                    || producto.getCostoVersion() != item.getCostoVersionAnterior()
                    || current.compareTo(item.getCostoAnterior()) != 0) {
                throw new CostoVersionConflictException(item.getProductoId());
            }
            lockedProducts.put(item.getProductoId(), producto);
        }

        int updated = 0;
        int unchanged = 0;
        for (CargaCostosItem item : items) {
            ProductoCostoService.ResultadoCambio result = productoCostoService.actualizarProductoBloqueado(
                    lockedProducts.get(item.getProductoId()),
                    item.getCostoVersionAnterior(),
                    item.getCostoAnterior(),
                    item.getCostoNuevo(),
                    new ProductoCostoService.ContextoCambio(
                            usuario,
                            usuario.getUsername(),
                            ProductoCostoOrigen.CARGA_MASIVA_COSTOS,
                            lote.getMotivo(),
                            lote.getNombreArchivo(),
                            lote));
            if (result.actualizado()) updated++; else unchanged++;
        }

        lote.setEstado(CargaCostosLote.Estado.EJECUTADO);
        lote.setEjecutadoEn(now);
        lote.setTotalActualizadas(updated);
        lote.setTotalSinCambio(unchanged);
        lote.setTokenHash(null);
        lote.setTokenExpiraEn(null);
        loteRepo.save(lote);
        return successResponse(lote, "Costos actualizados correctamente");
    }

    @Transactional
    public void cancelar(UUID loteId, User usuario) {
        CargaCostosLote lote = requireOwnedLote(loteId, usuario, true);
        if (lote.getEstado() == CargaCostosLote.Estado.CANCELADO
                || lote.getEstado() == CargaCostosLote.Estado.EJECUTADO) return;
        if (lote.getEstado() != CargaCostosLote.Estado.PREPARADO) return;
        lote.setEstado(CargaCostosLote.Estado.CANCELADO);
        lote.setTokenHash(null);
        lote.setTokenExpiraEn(null);
        loteRepo.save(lote);
    }

    private CargaCostosLote requireOwnedLote(UUID loteId, User usuario, boolean forUpdate) {
        CargaCostosLote lote = (forUpdate ? loteRepo.findByIdForUpdate(loteId) : loteRepo.findById(loteId))
                .orElseThrow(() -> new NoSuchElementException("Preparacion no encontrada"));
        if (lote.getUsuario() == null || !lote.getUsuario().getId().equals(usuario.getId())) {
            throw new NoSuchElementException("Preparacion no encontrada");
        }
        return lote;
    }

    private void requirePrepared(CargaCostosLote lote) {
        if (lote.getEstado() != CargaCostosLote.Estado.PREPARADO) {
            throw new CargaCostosStateException(
                    "PREPARACION_NO_DISPONIBLE",
                    "La preparacion no esta disponible: " + lote.getEstado());
        }
    }

    private CargaCostosDTOs.PreparacionResponse preparationResponse(
            CargaCostosLote lote,
            List<String> warnings
    ) {
        return new CargaCostosDTOs.PreparacionResponse(
                lote.getId(),
                lote.getEstado().name(),
                lote.getNombreArchivo(),
                lote.getMotivo(),
                toOffset(lote.getExpiraEn()),
                lote.getTotalFilas(),
                lote.getTotalCandidatas(),
                lote.getTotalActualizadas(),
                lote.getTotalSinCambio(),
                lote.getTotalOmitidas(),
                List.copyOf(warnings));
    }

    private CargaCostosDTOs.ItemPreview toPreview(CargaCostosItem item) {
        BigDecimal difference = item.getCostoNuevo().subtract(item.getCostoAnterior());
        BigDecimal percentage = item.getCostoAnterior().signum() == 0
                ? null
                : difference.multiply(BigDecimal.valueOf(100))
                        .divide(item.getCostoAnterior(), 2, RoundingMode.HALF_UP);
        return new CargaCostosDTOs.ItemPreview(
                item.getFilaExcel(),
                item.getProductoId(),
                item.getProductoNombre(),
                item.getDescripcionExcel(),
                item.isDescripcionCoincide(),
                item.getCostoAnterior(),
                item.getCostoNuevo(),
                difference,
                percentage,
                difference.signum() != 0);
    }

    private CargaCostosDTOs.ConfirmacionResponse successResponse(CargaCostosLote lote, String message) {
        return new CargaCostosDTOs.ConfirmacionResponse(
                lote.getId(),
                true,
                lote.getEstado().name(),
                message,
                toOffset(lote.getEjecutadoEn()),
                lote.getTotalActualizadas(),
                lote.getTotalSinCambio());
    }

    private CargaCostosDTOs.ErrorFila error(
            CargaCostosExcelParser.ParsedRow row,
            String field,
            String message
    ) {
        return new CargaCostosDTOs.ErrorFila(row.fila(), row.productoId(), field, message);
    }

    private boolean descriptionsMatch(String excelDescription, String productName) {
        if (excelDescription == null || excelDescription.isBlank()) return true;
        return normalizeDescription(excelDescription).equals(normalizeDescription(productName));
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(applicationClock.getZone()).toOffsetDateTime();
    }

    private String snapshotUsername(User usuario) {
        String username = usuario.getUsername().trim();
        return username.substring(0, Math.min(username.length(), 120));
    }

    private String normalizeDescription(String value) {
        if (value == null) return "";
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
