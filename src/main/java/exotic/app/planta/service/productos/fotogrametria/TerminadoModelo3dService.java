package exotic.app.planta.service.productos.fotogrametria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import exotic.app.planta.config.StorageProperties;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.repo.producto.TerminadoRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Un modelo final por terminado, bajo el mismo volumen duradero de los documentos.
 * No guarda fotografias ni introduce entidades, columnas o rutas aportadas por el cliente.
 */
@Service
@Slf4j
public class TerminadoModelo3dService {

    public static final int MAX_MODEL_BYTES = 50 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final int GLB_MAGIC = 0x46546c67;
    private static final int JSON_CHUNK = 0x4e4f534a;
    private static final int BIN_CHUNK = 0x004e4942;
    private final Path root;
    private final TerminadoRepo terminadoRepo;
    private final ObjectMapper mapper;

    public TerminadoModelo3dService(StorageProperties storage, TerminadoRepo terminadoRepo, ObjectMapper mapper) {
        this.root = Path.of(storage.getUPLOAD_DIR(), storage.getPRODUCTOS(), "modelos-3d")
                .toAbsolutePath().normalize();
        this.terminadoRepo = terminadoRepo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ModelMetadata metadata(String productoId) throws IOException {
        return load(productoId).metadata();
    }

    @Transactional(readOnly = true)
    public ModelDownload load(String productoId) throws IOException {
        // Atomic file publication allows readers to use a complete model without
        // taking the product lock reserved for saving or deleting a model.
        Terminado terminado = findTerminado(productoId);
        Path path = modelPath(terminado);
        byte[] bytes = readExisting(path);
        return new ModelDownload(bytes, describe(productoId, path, bytes));
    }

    @Transactional
    public ModelMetadata save(String productoId, InputStream input, long declaredLength,
                              String ifMatch, String ifNoneMatch) throws IOException {
        if (declaredLength > MAX_MODEL_BYTES) throw tooLarge();
        // Validate before taking a database lock; a slow upload must not lock the product row.
        byte[] bytes = input.readNBytes(MAX_MODEL_BYTES + 1);
        if (bytes.length > MAX_MODEL_BYTES) throw tooLarge();
        validateGlb(bytes);
        Terminado terminado = requireTerminado(productoId);
        Path destination = modelPath(terminado);
        String current = Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                ? quotedHash(readExisting(destination)) : null;
        checkPrecondition(current, ifMatch, ifNoneMatch);
        createSafeRoot();
        Path temporary = Files.createTempFile(root, ".upload-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Same-volume atomic publication preserves the previous complete model on failure.
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "El almacenamiento no admite publicar el modelo de forma atomica.", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return describe(productoId, destination, bytes);
    }

    @Transactional
    public void delete(String productoId, String ifMatch) throws IOException {
        Path path = modelPath(requireTerminado(productoId));
        String current = quotedHash(readExisting(path));
        checkPrecondition(current, ifMatch, null);
        Files.delete(path);
    }

    /** Called while the existing product-deletion transaction still owns the entity. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleCleanupAfterDeletion(Terminado terminado) {
        if (terminado.getFechaCreacion() == null) return; // This legacy row cannot have a model.
        Path path = modelPath(terminado);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    ensureSafeExistingRoot();
                    Files.deleteIfExists(path);
                } catch (IOException | RuntimeException exception) {
                    log.warn("No fue posible limpiar el modelo 3D del terminado eliminado.", exception);
                }
            }
        });
    }

    private Terminado requireTerminado(String id) {
        terminadoRepo.lockModelo3dProductoId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe un terminado con ese codigo. El modelo 3D no aplica a semiterminados."));
        return findTerminado(id);
    }

    private Terminado findTerminado(String id) {
        return terminadoRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No existe un terminado con ese codigo. El modelo 3D no aplica a semiterminados."));
    }

    private Path modelPath(Terminado terminado) {
        if (terminado.getFechaCreacion() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El terminado no tiene fecha de creacion y no puede asociar un modelo 3D todavia.");
        }
        String identity = terminado.getProductoId() + "\n" + terminado.getFechaCreacion();
        return root.resolve(hash(identity.getBytes(StandardCharsets.UTF_8)) + ".glb");
    }

    private void createSafeRoot() throws IOException {
        Files.createDirectories(root);
        ensureSafeExistingRoot();
    }

    private void ensureSafeExistingRoot() throws IOException {
        // Refuse redirected storage paths rather than follow a symbolic link outside the data volume.
        Path cursor = root;
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("La ruta de modelos no puede contener enlaces simbolicos.");
            cursor = cursor.getParent();
        }
    }

    private byte[] readExisting(Path path) throws IOException {
        ensureSafeExistingRoot();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El terminado no tiene un modelo 3D guardado.");
        }
        if (Files.size(path) > MAX_MODEL_BYTES) throw new IOException("El modelo almacenado supera el limite admitido.");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] result = input.readNBytes(MAX_MODEL_BYTES + 1);
            if (result.length > MAX_MODEL_BYTES) throw new IOException("El modelo almacenado supera el limite admitido.");
            return result;
        }
    }

    private static ModelMetadata describe(String id, Path path, byte[] bytes) throws IOException {
        String sha256 = hash(bytes);
        return new ModelMetadata(id, bytes.length, sha256, Files.getLastModifiedTime(path).toInstant(),
                "\"" + sha256 + "\"");
    }

    private static void checkPrecondition(String current, String ifMatch, String ifNoneMatch) {
        if (ifMatch != null && ifNoneMatch != null) {
            throw invalid("No combine If-Match con If-None-Match.");
        }
        if (current == null) {
            if (ifMatch != null) throw conflict();
            if (!"*".equals(ifNoneMatch)) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                        "Para crear el modelo envie If-None-Match: *.");
            }
        } else {
            if (ifNoneMatch != null) throw conflict();
            if (ifMatch == null) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                        "Consulte el modelo y envie su ETag en If-Match para reemplazarlo o eliminarlo.");
            }
            if (!current.equals(ifMatch)) throw conflict();
        }
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                "El modelo cambio en otra sesion. Consulte la version guardada antes de continuar.");
    }

    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El modelo no puede superar 50 MiB.");
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String quotedHash(byte[] bytes) {
        return "\"" + hash(bytes) + "\"";
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible.", exception);
        }
    }

    private void validateGlb(byte[] bytes) throws IOException {
        if (bytes.length < 32) throw invalid("El archivo no es un modelo GLB valido.");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (data.getInt() != GLB_MAGIC || data.getInt() != 2
                || Integer.toUnsignedLong(data.getInt()) != bytes.length) {
            throw invalid("Se requiere un GLB version 2 con longitud valida.");
        }
        int jsonLength = data.getInt();
        if (jsonLength <= 0 || jsonLength > MAX_JSON_BYTES || jsonLength % 4 != 0
                || data.getInt() != JSON_CHUNK || jsonLength > data.remaining() - 8) {
            throw invalid("El bloque JSON del GLB no es valido.");
        }
        JsonNode document;
        try {
            document = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(bytes, data.position(), jsonLength);
        } catch (IOException exception) {
            throw invalid("El JSON del modelo GLB no es valido.");
        }
        data.position(data.position() + jsonLength);
        int binLength = data.getInt();
        if (binLength <= 0 || binLength % 4 != 0 || data.getInt() != BIN_CHUNK || binLength != data.remaining()) {
            throw invalid("El modelo debe incluir un unico bloque binario autocontenido.");
        }
        ByteBuffer binary = data.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (document == null || !document.isObject()) throw invalid("El documento glTF debe ser un objeto JSON.");
        if (!"2.0".equals(document.path("asset").path("version").asText())) throw invalid("La version glTF debe ser 2.0.");
        rejectUris(document);
        JsonNode required = document.path("extensionsRequired");
        if (required.isArray() && !required.isEmpty()) throw invalid("El modelo usa extensiones obligatorias no admitidas.");
        JsonNode buffers = document.path("buffers");
        if (!buffers.isArray() || buffers.size() != 1) throw invalid("Se requiere un unico buffer integrado.");
        int bufferLength = nonNegativeInt(buffers.get(0).path("byteLength"), "Longitud de buffer");
        if (bufferLength <= 0 || bufferLength > binLength || binLength - bufferLength > 3) throw invalid("La longitud del buffer no coincide con el GLB.");
        JsonNode views = document.path("bufferViews");
        if (!views.isArray()) throw invalid("El modelo no declara bufferViews.");
        for (JsonNode view : views) {
            if (nonNegativeInt(view.path("buffer"), "Buffer") != 0) throw invalid("Referencia de buffer invalida.");
            int offset = optionalOffset(view, "byteOffset");
            int length = nonNegativeInt(view.path("byteLength"), "Longitud de vista");
            if ((long) offset + length > bufferLength) throw invalid("Una vista excede el buffer del modelo.");
        }
        JsonNode images = document.path("images");
        if (images.isArray()) {
            for (JsonNode image : images) {
                indexed(views, image.path("bufferView"), "Imagen integrada");
                if (!"image/png".equals(image.path("mimeType").asText())
                        && !"image/jpeg".equals(image.path("mimeType").asText())) throw invalid("Solo se admiten texturas PNG/JPEG integradas.");
            }
        }
        JsonNode meshes = document.path("meshes");
        if (!meshes.isArray() || meshes.size() != 1) throw invalid("El resultado debe contener una unica malla del objeto aislado.");
        JsonNode primitives = meshes.get(0).path("primitives");
        if (!primitives.isArray() || primitives.isEmpty() || primitives.size() > 1024) throw invalid("La malla debe contener grupos de triangulos validos.");
        JsonNode accessors = document.path("accessors");
        validateAccessors(accessors, views, binary);
        Map<Integer, Integer> positionsChecked = new HashMap<>();
        long triangleIndices = 0;
        for (JsonNode primitive : primitives) {
            if (primitive.has("mode") && primitive.path("mode").asInt(-1) != 4) throw invalid("El modelo debe contener triangulos.");
            int positionId = nonNegativeInt(primitive.path("attributes").path("POSITION"), "Posiciones");
            JsonNode position = indexed(accessors, primitive.path("attributes").path("POSITION"), "Posiciones");
            int vertexCount = positionsChecked.computeIfAbsent(positionId, ignored -> validatePositions(position, views, binary));
            var attributes = primitive.path("attributes").fields();
            while (attributes.hasNext()) {
                var attribute = attributes.next();
                JsonNode accessor = indexed(accessors, attribute.getValue(), "Atributo de malla");
                if (accessor.path("count").asInt() != vertexCount) throw invalid("Los atributos deben tener un dato por vertice.");
            }
            if (primitive.has("indices")) {
                JsonNode indices = indexed(accessors, primitive.path("indices"), "Indices");
                triangleIndices += indices.path("count").asLong();
                if (triangleIndices > 12_000_000) throw invalid("El modelo supera el limite de triangulos.");
                validateIndices(indices, views, binary, vertexCount);
            } else {
                if (vertexCount % 3 != 0) throw invalid("La cantidad de vertices no forma triangulos completos.");
                triangleIndices += vertexCount;
            }
            if (triangleIndices > 12_000_000) throw invalid("El modelo supera el limite de triangulos.");
            if (primitive.has("material")) indexed(document.path("materials"), primitive.path("material"), "Material");
        }
        int meshInstances = 0;
        JsonNode nodes = document.path("nodes");
        if (!nodes.isArray() || nodes.size() > 1024) throw invalid("Los nodos de la escena no son validos.");
        for (JsonNode node : nodes) {
            if (node.has("mesh")) {
                if (nonNegativeInt(node.path("mesh"), "Malla de nodo") != 0) throw invalid("Referencia de malla invalida.");
                meshInstances++;
            }
            if (node.has("skin") || node.has("camera")) throw invalid("El modelo del terminado no admite esqueletos ni camaras.");
            validateTransform(node, "matrix", 16);
            validateTransform(node, "translation", 3);
            validateTransform(node, "rotation", 4);
            validateTransform(node, "scale", 3);
        }
        if (meshInstances != 1) throw invalid("El modelo debe mostrar una sola instancia del cuerpo.");
        JsonNode scenes = document.path("scenes");
        if (!scenes.isArray() || scenes.size() != 1 || scenes.get(0).path("nodes").isEmpty()) throw invalid("El GLB debe incluir una escena visible.");
        for (JsonNode node : scenes.get(0).path("nodes")) indexed(nodes, node, "Nodo de escena");
        if (document.has("scene") && nonNegativeInt(document.path("scene"), "Escena") != 0) throw invalid("La escena activa no existe.");
        int[] visited = new int[nodes.size()];
        int visibleMeshes = 0;
        for (JsonNode node : scenes.get(0).path("nodes")) visibleMeshes += visitNode(node.intValue(), nodes, visited);
        if (visibleMeshes != 1) throw invalid("La escena debe mostrar una sola instancia de la malla.");
        if (document.has("animations") && !document.path("animations").isEmpty()) throw invalid("El modelo del terminado debe ser estatico.");
    }

    private static void validateAccessors(JsonNode accessors, JsonNode views, ByteBuffer binary) {
        if (!accessors.isArray() || accessors.size() > 4096) throw invalid("La lista de accessors no es valida.");
        long inspectedBytes = 0;
        for (JsonNode accessor : accessors) {
            if (accessor.has("sparse")) throw invalid("Los accessors dispersos no estan admitidos.");
            int componentType = accessor.path("componentType").asInt();
            int componentBytes = switch (componentType) {
                case 5120, 5121 -> 1;
                case 5122, 5123 -> 2;
                case 5125, 5126 -> 4;
                default -> throw invalid("Tipo de componente no admitido.");
            };
            int components = switch (accessor.path("type").asText()) {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                default -> throw invalid("Tipo de accessor no admitido para una malla estatica.");
            };
            int count = nonNegativeInt(accessor.path("count"), "Cantidad de datos");
            if (count == 0 || count > 12_000_000) throw invalid("La cantidad de datos del accessor no es valida.");
            inspectedBytes += (long) count * componentBytes * components;
            if (inspectedBytes > (long) MAX_MODEL_BYTES * 4) throw invalid("El modelo contiene demasiadas referencias de datos repetidos.");
            AccessorLayout layout = layout(accessor, views, componentBytes * components, count);
            if (componentType == 5126) {
                for (int index = 0; index < count; index++) {
                    for (int component = 0; component < components; component++) {
                        if (!Float.isFinite(binary.getFloat(layout.offset + index * layout.stride + component * 4))) {
                            throw invalid("El modelo contiene atributos no finitos.");
                        }
                    }
                }
            }
        }
    }

    private static int visitNode(int index, JsonNode nodes, int[] visited) {
        if (visited[index] != 0) throw invalid("La escena contiene ciclos o nodos repetidos.");
        visited[index] = 1;
        JsonNode node = nodes.get(index);
        int count = node.has("mesh") ? 1 : 0;
        if (node.has("children")) {
            JsonNode children = node.path("children");
            if (!children.isArray()) throw invalid("Los hijos de un nodo deben ser una lista.");
            for (JsonNode child : children) {
                indexed(nodes, child, "Nodo hijo");
                count += visitNode(child.intValue(), nodes, visited);
            }
        }
        visited[index] = 2;
        return count;
    }

    private static void validateTransform(JsonNode node, String field, int size) {
        if (!node.has(field)) return;
        JsonNode values = node.path(field);
        if (!values.isArray() || values.size() != size) throw invalid("La transformacion " + field + " no es valida.");
        for (JsonNode value : values) {
            if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || Math.abs(value.doubleValue()) > 1_000_000) {
                throw invalid("La transformacion contiene valores fuera del limite.");
            }
        }
    }

    private static int validatePositions(JsonNode accessor, JsonNode views, ByteBuffer binary) {
        if (accessor.has("sparse") || accessor.path("componentType").asInt() != 5126
                || !"VEC3".equals(accessor.path("type").asText())) throw invalid("Las posiciones deben ser vectores float VEC3.");
        int count = nonNegativeInt(accessor.path("count"), "Cantidad de vertices");
        if (count < 3 || count > 2_000_000) throw invalid("Cantidad de vertices fuera del limite.");
        AccessorLayout layout = layout(accessor, views, 12, count);
        for (int index = 0; index < count; index++) {
            int start = layout.offset + index * layout.stride;
            for (int component = 0; component < 3; component++) {
                float value = binary.getFloat(start + component * 4);
                if (!Float.isFinite(value) || Math.abs(value) > 1_000_000) throw invalid("El modelo contiene coordenadas no validas o fuera de escala.");
            }
        }
        return count;
    }

    private static void validateIndices(JsonNode accessor, JsonNode views, ByteBuffer binary, int vertices) {
        if (accessor.has("sparse") || !"SCALAR".equals(accessor.path("type").asText())) throw invalid("Los indices deben ser enteros escalares.");
        int component = accessor.path("componentType").asInt();
        int width = switch (component) { case 5121 -> 1; case 5123 -> 2; case 5125 -> 4; default -> throw invalid("Tipo de indice no admitido."); };
        int count = nonNegativeInt(accessor.path("count"), "Cantidad de indices");
        if (count < 3 || count % 3 != 0 || count > 12_000_000) throw invalid("La lista de indices no forma triangulos validos.");
        AccessorLayout layout = layout(accessor, views, width, count);
        for (int index = 0; index < count; index++) {
            int start = layout.offset + index * layout.stride;
            long value = switch (width) { case 1 -> Byte.toUnsignedInt(binary.get(start)); case 2 -> Short.toUnsignedInt(binary.getShort(start)); default -> Integer.toUnsignedLong(binary.getInt(start)); };
            if (value >= vertices) throw invalid("Un triangulo referencia un vertice inexistente.");
        }
    }

    private static AccessorLayout layout(JsonNode accessor, JsonNode views, int elementBytes, int count) {
        JsonNode view = indexed(views, accessor.path("bufferView"), "Vista de accessor");
        int relative = optionalOffset(accessor, "byteOffset");
        int viewStart = optionalOffset(view, "byteOffset");
        int length = nonNegativeInt(view.path("byteLength"), "Longitud de vista");
        int stride = view.has("byteStride") ? nonNegativeInt(view.path("byteStride"), "Paso de datos") : elementBytes;
        if (stride < elementBytes || stride > 252 || (long) relative + (long) (count - 1) * stride + elementBytes > length) {
            throw invalid("Un accessor excede su vista binaria.");
        }
        return new AccessorLayout(viewStart + relative, stride);
    }

    private static JsonNode indexed(JsonNode array, JsonNode index, String name) {
        int value = nonNegativeInt(index, name);
        if (!array.isArray() || value >= array.size()) throw invalid(name + ": referencia fuera de rango.");
        return array.get(value);
    }

    private static int optionalOffset(JsonNode node, String field) {
        return node.has(field) ? nonNegativeInt(node.path(field), field) : 0;
    }

    private static int nonNegativeInt(JsonNode value, String name) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) throw invalid(name + ": entero no negativo requerido.");
        return value.intValue();
    }

    private static void rejectUris(JsonNode node) {
        if (node.isObject()) {
            if (node.has("uri")) throw invalid("El GLB debe ser autocontenido, sin referencias URI ni fotografias externas.");
            node.elements().forEachRemaining(TerminadoModelo3dService::rejectUris);
        } else if (node.isArray()) node.elements().forEachRemaining(TerminadoModelo3dService::rejectUris);
    }

    private record AccessorLayout(int offset, int stride) { }
    public record ModelMetadata(String productoId, long contentLength, String sha256, Instant updatedAt, String etag) { }
    public record ModelDownload(byte[] bytes, ModelMetadata metadata) { }
}
