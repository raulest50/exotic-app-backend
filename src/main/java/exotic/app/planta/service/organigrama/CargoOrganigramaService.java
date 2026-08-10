package exotic.app.planta.service.organigrama;

import exotic.app.planta.config.AppTime;
import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.model.organigrama.Cargo;
import exotic.app.planta.model.organigrama.OrganigramaEstado;
import exotic.app.planta.model.organigrama.dto.CargoOrganigramaRequest;
import exotic.app.planta.model.organigrama.dto.CargoOrganigramaResponse;
import exotic.app.planta.model.organigrama.dto.GuardarOrganigramaRequest;
import exotic.app.planta.model.organigrama.dto.OrganigramaSnapshotResponse;
import exotic.app.planta.model.organigrama.dto.RelacionOrganigramaRequest;
import exotic.app.planta.model.organigrama.dto.RelacionOrganigramaResponse;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.organigrama.CargoOrganigramaRepo;
import exotic.app.planta.repo.organigrama.OrganigramaEstadoRepo;
import exotic.app.planta.repo.usuarios.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CargoOrganigramaService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final CargoOrganigramaRepo cargoOrganigramaRepo;
    private final OrganigramaEstadoRepo organigramaEstadoRepo;
    private final UserRepository userRepository;
    private final StorageProperties storageProperties;

    @Transactional(readOnly = true)
    public OrganigramaSnapshotResponse getSnapshot() {
        OrganigramaEstado estado = getEstado();
        return buildSnapshot(estado, cargoOrganigramaRepo.findAllByOrderByIdCargoAsc());
    }

    /**
     * Guarda el organigrama completo como un agregado versionado. Las coordenadas,
     * los cargos y las relaciones proceden del mismo snapshot del cliente.
     */
    @Transactional
    public OrganigramaSnapshotResponse saveSnapshot(
            GuardarOrganigramaRequest request,
            String actualizadoPor
    ) {
        ValidatedGraph graph = validateGraph(request);
        OrganigramaEstado estado = organigramaEstadoRepo
                .findByIdForUpdate(OrganigramaEstado.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("No existe el estado del organigrama."));

        if (!Objects.equals(estado.getRevision(), request.getBaseRevision())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El organigrama fue modificado por otro usuario. Recargue antes de guardar."
            );
        }

        Map<String, Cargo> existentes = cargoOrganigramaRepo.findAll().stream()
                .collect(Collectors.toMap(Cargo::getIdCargo, Function.identity()));

        // Libera primero la asociación @OneToOne para permitir intercambiar usuarios
        // entre cargos dentro de la misma transacción.
        existentes.values().forEach(cargo -> cargo.setUsuario(null));
        cargoOrganigramaRepo.saveAll(existentes.values());
        cargoOrganigramaRepo.flush();

        Set<String> idsSolicitados = graph.cargosById().keySet();
        List<String> idsEliminados = existentes.keySet().stream()
                .filter(id -> !idsSolicitados.contains(id))
                .toList();
        if (!idsEliminados.isEmpty()) {
            cargoOrganigramaRepo.deleteAllByIdInBatch(idsEliminados);
        }

        Map<String, User> usuarios = resolveUsers(graph.usernames());
        List<Cargo> cargosAGuardar = new ArrayList<>();
        for (CargoOrganigramaRequest cargoRequest : request.getCargos()) {
            String id = cargoRequest.getIdCargo().trim();
            Cargo cargo = existentes.getOrDefault(id, new Cargo());
            cargo.setIdCargo(id);
            cargo.setTituloCargo(cargoRequest.getTituloCargo().trim());
            cargo.setDescripcionCargo(cargoRequest.getDescripcionCargo().trim());
            cargo.setDepartamento(cargoRequest.getDepartamento().trim());
            cargo.setPosicionX(cargoRequest.getPosicionX());
            cargo.setPosicionY(cargoRequest.getPosicionY());
            cargo.setNivel(cargoRequest.getNivel());
            cargo.setJefeInmediato(graph.jefePorSubordinado().get(id));

            String username = trimToNull(cargoRequest.getUsuario());
            cargo.setUsuario(username == null ? null : usuarios.get(username));
            // urlDocManualFunciones se conserva en cargos existentes. Los manuales
            // se administran exclusivamente mediante sus endpoints dedicados.
            cargosAGuardar.add(cargo);
        }

        cargoOrganigramaRepo.saveAll(cargosAGuardar);
        cargoOrganigramaRepo.flush();

        estado.setRevision(estado.getRevision() + 1);
        estado.setActualizadoEn(AppTime.now());
        estado.setActualizadoPor(truncate(trimToNull(actualizadoPor), 120));
        organigramaEstadoRepo.save(estado);

        return buildSnapshot(estado, cargoOrganigramaRepo.findAllByOrderByIdCargoAsc());
    }

    @Transactional
    public CargoOrganigramaResponse uploadManualFunciones(String cargoId, MultipartFile file) throws IOException {
        Cargo cargo = getCargoForUpdate(cargoId);
        validatePdf(file);

        Path manualesRoot = Paths.get(
                storageProperties.getUPLOAD_DIR(),
                storageProperties.getORGANIGRAMA(),
                "manuales"
        ).toAbsolutePath().normalize();
        Path cargoFolder = manualesRoot.resolve(cargo.getIdCargo()).normalize();
        if (!cargoFolder.startsWith(manualesRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID de cargo inválido.");
        }

        Files.createDirectories(cargoFolder);
        Path destination = cargoFolder.resolve("manual_funciones.pdf");
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        cargo.setUrlDocManualFunciones(destination.toString());
        return CargoOrganigramaResponse.fromEntity(cargoOrganigramaRepo.save(cargo));
    }

    @Transactional
    public CargoOrganigramaResponse setManualFuncionesUrl(String cargoId, String url) {
        Cargo cargo = getCargoForUpdate(cargoId);
        String normalizedUrl = validateHttpUrl(url);
        cargo.setUrlDocManualFunciones(normalizedUrl);
        return CargoOrganigramaResponse.fromEntity(cargoOrganigramaRepo.save(cargo));
    }

    @Transactional
    public CargoOrganigramaResponse clearManualFunciones(String cargoId) {
        Cargo cargo = getCargoForUpdate(cargoId);
        cargo.setUrlDocManualFunciones(null);
        return CargoOrganigramaResponse.fromEntity(cargoOrganigramaRepo.save(cargo));
    }

    @Transactional(readOnly = true)
    public ManualDownload getManualFunciones(String cargoId) throws IOException {
        Cargo cargo = cargoOrganigramaRepo.findById(cargoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cargo no encontrado."));
        String location = trimToNull(cargo.getUrlDocManualFunciones());
        if (location == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El cargo no tiene manual de funciones.");
        }
        if (isHttpUrl(location)) {
            return ManualDownload.redirect(URI.create(location));
        }

        Path path = Paths.get(location);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el archivo del manual.");
        }
        return ManualDownload.file(
                Files.readAllBytes(path),
                "manual_funciones_" + safeFilename(cargo.getTituloCargo()) + ".pdf"
        );
    }

    private Cargo getCargoForUpdate(String cargoId) {
        // El bloqueo del singleton serializa cambios de manual con un guardado del
        // grafo, pero no incrementa la revisión porque no modifica el organigrama.
        organigramaEstadoRepo.findByIdForUpdate(OrganigramaEstado.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("No existe el estado del organigrama."));
        return cargoOrganigramaRepo.findById(cargoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cargo no encontrado."));
    }

    private ValidatedGraph validateGraph(GuardarOrganigramaRequest request) {
        if (request == null || request.getBaseRevision() == null || request.getBaseRevision() < 0) {
            throw badRequest("La revisión base del organigrama es obligatoria.");
        }
        if (request.getCargos() == null || request.getRelaciones() == null) {
            throw badRequest("Los cargos y las relaciones son obligatorios.");
        }

        Map<String, CargoOrganigramaRequest> cargosById = new LinkedHashMap<>();
        Set<String> usernames = new HashSet<>();

        for (CargoOrganigramaRequest cargo : request.getCargos()) {
            if (cargo == null) {
                throw badRequest("El organigrama contiene un cargo inválido.");
            }
            String id = requiredText(cargo.getIdCargo(), 128, "ID del cargo");
            requiredText(cargo.getTituloCargo(), 255, "Título del cargo");
            requiredText(cargo.getDescripcionCargo(), 255, "Descripción del cargo");
            requiredText(cargo.getDepartamento(), 255, "Departamento del cargo");
            if (cargo.getNivel() < 1 || cargo.getNivel() > 10) {
                throw badRequest("El nivel jerárquico debe estar entre 1 y 10.");
            }
            if (!Double.isFinite(cargo.getPosicionX()) || !Double.isFinite(cargo.getPosicionY())) {
                throw badRequest("Las coordenadas de todos los cargos deben ser números finitos.");
            }
            if (cargosById.putIfAbsent(id, cargo) != null) {
                throw badRequest("Hay IDs de cargo duplicados: " + id);
            }
            String username = trimToNull(cargo.getUsuario());
            if (username != null && username.length() > 120) {
                throw badRequest("El nombre de usuario asignado supera la longitud permitida.");
            }
            if (username != null && !usernames.add(username)) {
                throw badRequest("Un usuario no puede estar asignado a más de un cargo: " + username);
            }
        }

        Map<String, String> jefePorSubordinado = new HashMap<>();
        Set<String> relaciones = new HashSet<>();
        for (RelacionOrganigramaRequest relacion : request.getRelaciones()) {
            if (relacion == null) {
                throw badRequest("El organigrama contiene una relación inválida.");
            }
            String jefeId = requiredText(relacion.getJefeId(), 128, "ID del jefe");
            String subordinadoId = requiredText(relacion.getSubordinadoId(), 128, "ID del subordinado");
            if (!cargosById.containsKey(jefeId) || !cargosById.containsKey(subordinadoId)) {
                throw badRequest("Todas las relaciones deben referenciar cargos existentes.");
            }
            if (jefeId.equals(subordinadoId)) {
                throw badRequest("Un cargo no puede ser jefe de sí mismo.");
            }
            if (!relaciones.add(jefeId + "\u0000" + subordinadoId)) {
                throw badRequest("Hay relaciones duplicadas en el organigrama.");
            }
            String previous = jefePorSubordinado.putIfAbsent(subordinadoId, jefeId);
            if (previous != null) {
                throw badRequest("Cada cargo puede tener como máximo un jefe inmediato.");
            }
        }

        for (String cargoId : cargosById.keySet()) {
            Set<String> visited = new HashSet<>();
            String current = cargoId;
            while (current != null) {
                if (!visited.add(current)) {
                    throw badRequest("El organigrama no puede contener ciclos jerárquicos.");
                }
                current = jefePorSubordinado.get(current);
            }
        }

        return new ValidatedGraph(cargosById, jefePorSubordinado, usernames);
    }

    private Map<String, User> resolveUsers(Set<String> usernames) {
        Map<String, User> resolved = new HashMap<>();
        for (String username : usernames) {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> badRequest("No existe el usuario asignado: " + username));
            resolved.put(username, user);
        }
        return resolved;
    }

    private OrganigramaSnapshotResponse buildSnapshot(OrganigramaEstado estado, List<Cargo> cargos) {
        List<Cargo> sorted = cargos.stream()
                .sorted(Comparator.comparing(Cargo::getIdCargo))
                .toList();
        Set<String> ids = sorted.stream().map(Cargo::getIdCargo).collect(Collectors.toSet());

        List<CargoOrganigramaResponse> cargoResponses = sorted.stream()
                .map(CargoOrganigramaResponse::fromEntity)
                .toList();
        List<RelacionOrganigramaResponse> relaciones = sorted.stream()
                .filter(cargo -> trimToNull(cargo.getJefeInmediato()) != null)
                .filter(cargo -> ids.contains(cargo.getJefeInmediato()))
                .map(cargo -> new RelacionOrganigramaResponse(cargo.getJefeInmediato(), cargo.getIdCargo()))
                .sorted(Comparator
                        .comparing(RelacionOrganigramaResponse::jefeId)
                        .thenComparing(RelacionOrganigramaResponse::subordinadoId))
                .toList();

        return new OrganigramaSnapshotResponse(
                estado.getRevision(),
                estado.getActualizadoEn(),
                estado.getActualizadoPor(),
                cargoResponses,
                relaciones
        );
    }

    private OrganigramaEstado getEstado() {
        return organigramaEstadoRepo.findById(OrganigramaEstado.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("No existe el estado del organigrama."));
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Debe seleccionar un archivo PDF.");
        }
        String filename = file.getOriginalFilename();
        boolean pdfExtension = filename != null && filename.toLowerCase().endsWith(".pdf");
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(file.getContentType()) || !pdfExtension) {
            throw badRequest("El archivo debe ser un PDF.");
        }
    }

    private String validateHttpUrl(String url) {
        String normalized = trimToNull(url);
        if (normalized == null || normalized.length() > 255 || !isHttpUrl(normalized)) {
            throw badRequest("La URL del manual debe ser una dirección HTTP o HTTPS válida.");
        }
        try {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null) {
                throw badRequest("La URL del manual debe incluir un dominio válido.");
            }
        } catch (IllegalArgumentException error) {
            throw badRequest("La URL del manual debe ser una dirección HTTP o HTTPS válida.");
        }
        return normalized;
    }

    private boolean isHttpUrl(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String safeFilename(String value) {
        String normalized = value == null ? "cargo" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return normalized.isBlank() ? "cargo" : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requiredText(String value, int maxLength, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw badRequest(fieldName + " es obligatorio.");
        }
        if (normalized.length() > maxLength) {
            throw badRequest(fieldName + " supera la longitud permitida.");
        }
        return normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record ValidatedGraph(
            Map<String, CargoOrganigramaRequest> cargosById,
            Map<String, String> jefePorSubordinado,
            Set<String> usernames
    ) {
    }

    public record ManualDownload(byte[] content, String filename, URI redirectUri) {
        public static ManualDownload file(byte[] content, String filename) {
            return new ManualDownload(content, filename, null);
        }

        public static ManualDownload redirect(URI uri) {
            return new ManualDownload(null, null, uri);
        }

        public boolean isRedirect() {
            return redirectUri != null;
        }
    }
}
