package exotic.app.planta.service.compras;


import org.springframework.transaction.annotation.Transactional;
import exotic.app.planta.model.compras.ContactoProveedor;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.dto.search.DTO_SearchProveedor;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.service.commons.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class ProveedorService {

    @Autowired
    private final ProveedorRepo proveedorRepo;

    private final FileStorageService fileStorageService;


    /**
     * Saves a Proveedor and its optional files (RUT and Cámara).
     * The method is transactional so that if any part fails, the whole transaction is rolled back.
     * <p>
     * Note: While this method uses the business identifier (id) for validation and file storage,
     * the entity is saved with a surrogate key (pk) that is used for all database relationships.
     * 
     * @param proveedor The Proveedor entity populated from the request.
     * @param rutFile   The optional RUT file.
     * @param camaraFile The optional Cámara file.
     * @return The saved Proveedor entity.
     * @throws IOException if file storage fails.
     */
    @Transactional
    public Proveedor saveProveedorWithFiles(Proveedor proveedor,
                                            MultipartFile rutFile,
                                            MultipartFile camaraFile) throws IOException {

        // Check if a provider with the same business ID already exists
        if(proveedorRepo.findById(proveedor.getId()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un Proveedor con el Id " + proveedor.getId());
        }

        // Save files if provided using the file storage service.
        if (rutFile != null && !rutFile.isEmpty()) {
            // Save file to /data/proveedores/{proveedorId}/rut.pdf and update the URL.
            // We use the business ID for file naming for user readability
            String rutPath = fileStorageService.storeFileProveedor(proveedor.getId(), rutFile, "rut.pdf");
            proveedor.setRutUrl(rutPath);
        }
        if (camaraFile != null && !camaraFile.isEmpty()) {
            String camaraPath = fileStorageService.storeFileProveedor(proveedor.getId(), camaraFile, "camara.pdf");
            proveedor.setCamaraUrl(camaraPath);
        }
        // Set bidirectional back-reference on each contact before persisting.
        if (proveedor.getContactos() != null) {
            proveedor.getContactos().forEach(c -> c.setProveedor(proveedor));
        }

        // Save the proveedor entity; if any exception occurs (including in file saving),
        // the whole transaction will roll back.
        return proveedorRepo.save(proveedor);
    }




    public List<Proveedor> searchProveedores(String searchText) {

        // Search by nombre containing the search text
        List<Proveedor> result = new ArrayList<>(proveedorRepo.findByNombreContainingIgnoreCase(searchText));

        // Remove duplicates if an entity matched both name and id
        return result.stream().distinct().collect(Collectors.toList());
    }

    public Page<Proveedor> searchProveedores(String searchText, String searchType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if ("nombre".equalsIgnoreCase(searchType)) {
            return proveedorRepo.findByNombreContainingIgnoreCase(searchText, pageable);
        } else if ("nit".equalsIgnoreCase(searchType) || "id".equalsIgnoreCase(searchType)) {
            // Use the business identifier search method
            return proveedorRepo.findByBusinessId(searchText, pageable);
        } else {
            return Page.empty(pageable);
        }
    }

    /**
     * Search for providers based on the search criteria in the DTO
     *
     * @param searchDTO The search criteria
     * @param page The page number
     * @param size The page size
     * @return A page of providers matching the search criteria
     */
    public Page<Proveedor> searchProveedores(DTO_SearchProveedor searchDTO, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // Verificar si la cadena de búsqueda está vacía o solo contiene espacios en blanco
        // Si es así, devolver todos los proveedores

        // 1. Buscar por ID (business identifier) (prefijo)
        if (searchDTO.getSearchType() == DTO_SearchProveedor.SearchType.ID && searchDTO.getId() != null) {
            // Si el ID está vacío o solo contiene espacios en blanco, devolver todos los proveedores
            if (isBlank(searchDTO.getId())) {
                return proveedorRepo.findAll(pageable);
            }
            // Use the method that searches by business identifier prefix
            return proveedorRepo.findByIdStartingWith(searchDTO.getId(), pageable);
        }

        // 2. Buscar por nombre y categoría (en base de datos)
        if (searchDTO.getSearchType() == DTO_SearchProveedor.SearchType.NOMBRE_Y_CATEGORIA) {
            // Si el nombre está vacío o solo contiene espacios en blanco, establecerlo como null
            // para que la consulta SQL ignore esa condición y devuelva todos los proveedores
            String nombre = (searchDTO.getNombre() != null && !isBlank(searchDTO.getNombre()))
                    ? searchDTO.getNombre()
                    : null;

            int[] categorias = (searchDTO.getCategorias() != null && searchDTO.getCategorias().length > 0)
                    ? searchDTO.getCategorias()
                    : null;

            return proveedorRepo.searchByNombreAndCategorias(nombre, categorias, pageable);
        }

        // 3. Caso por defecto: retornar página vacía
        return Page.empty(pageable);
    }



    /**
     * Checks if a string is null, empty, or contains only whitespace.
     *
     * @param str The string to check
     * @return true if the string is null, empty, or contains only whitespace
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Actualiza un proveedor existente y sus archivos opcionales (RUT y Cámara).
     * El método es transaccional para que si alguna parte falla, toda la transacción se revierta.
     * <p>
     * Note: This method uses the business identifier (id) to look up the Proveedor entity,
     * demonstrating the advantage of the surrogate key pattern - the business ID can be used
     * for API operations while the surrogate key (pk) maintains database relationships.
     *
     * @param id El ID del proveedor a actualizar (business identifier)
     * @param proveedor La entidad Proveedor con los datos actualizados
     * @param rutFile El archivo RUT opcional
     * @param camaraFile El archivo Cámara opcional
     * @return La entidad Proveedor actualizada
     * @throws IOException si falla el almacenamiento de archivos
     * @throws IllegalArgumentException si el proveedor no existe o hay errores de validación
     */
    @Transactional
    public Proveedor updateProveedorWithFiles(String id, 
                                            Proveedor proveedor,
                                            MultipartFile rutFile,
                                            MultipartFile camaraFile) throws IOException {

        // Validar que el ID en la URL coincida con el ID en el cuerpo de la solicitud
        if (!id.equals(proveedor.getId())) {
            throw new IllegalArgumentException("El ID en la URL no coincide con el ID en el cuerpo de la solicitud");
        }

        // Verificar que el proveedor existe usando el business identifier
        Optional<Proveedor> optionalProveedor = proveedorRepo.findById(id);
        if (optionalProveedor.isEmpty()) {
            throw new IllegalArgumentException("No existe un Proveedor con el Id: " + id);
        }

        // Obtener el proveedor original para preservar campos que no deben modificarse
        Proveedor proveedorOriginal = optionalProveedor.get();

        // Validar archivos si se proporcionan
        if (rutFile != null && !rutFile.isEmpty() && !rutFile.getContentType().equals("application/pdf")) {
            throw new IllegalArgumentException("El archivo RUT debe ser un PDF");
        }

        if (camaraFile != null && !camaraFile.isEmpty() && !camaraFile.getContentType().equals("application/pdf")) {
            throw new IllegalArgumentException("El archivo Cámara de Comercio debe ser un PDF");
        }

        // Actualizar solo los campos editables
        proveedorOriginal.setRegimenTributario(proveedor.getRegimenTributario());
        proveedorOriginal.setCondicionPago(proveedor.getCondicionPago());

        // Reemplazar contactos: clear dispara orphanRemoval, luego se añaden los nuevos
        // con el back-reference correctamente establecido.
        proveedorOriginal.getContactos().clear();
        if (proveedor.getContactos() != null) {
            for (ContactoProveedor contacto : proveedor.getContactos()) {
                contacto.setContactoId(0);
                contacto.setProveedor(proveedorOriginal);
                proveedorOriginal.getContactos().add(contacto);
            }
        }

        // Registrar en log los cambios realizados
        log.info("Actualizando proveedor {}: regimenTributario={}, condicionPago={}", 
                 id, proveedor.getRegimenTributario(), proveedor.getCondicionPago());

        // Guardar archivos si se proporcionan utilizando el servicio de almacenamiento de archivos
        if (rutFile != null && !rutFile.isEmpty()) {
            String rutPath = fileStorageService.storeFileProveedor(id, rutFile, "rut.pdf");
            proveedorOriginal.setRutUrl(rutPath);
            log.info("Actualizado archivo RUT para proveedor: {}", id);
        }

        if (camaraFile != null && !camaraFile.isEmpty()) {
            String camaraPath = fileStorageService.storeFileProveedor(id, camaraFile, "camara.pdf");
            proveedorOriginal.setCamaraUrl(camaraPath);
            log.info("Actualizado archivo Cámara de Comercio para proveedor: {}", id);
        }

        // Guardar la entidad proveedor; si ocurre alguna excepción (incluido en el guardado de archivos),
        // toda la transacción se revertirá
        return proveedorRepo.save(proveedorOriginal);
    }
}
