package exotic.app.planta.resource.productos;


import exotic.app.planta.model.producto.dto.InsumoWithStockDTO;
import exotic.app.planta.model.producto.dto.ProductoStockDTO;
import exotic.app.planta.model.producto.dto.search.ProductoSearchCriteria;
import exotic.app.planta.model.producto.dto.search.DTO_SearchTerminado;
import exotic.app.planta.model.producto.dto.procdesigner.TargetDTO;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.Producto;
import exotic.app.planta.model.producto.SemiTerminado;
import exotic.app.planta.model.producto.Terminado;
import exotic.app.planta.model.producto.manufacturing.packaging.dto.CasePackResponseDTO;
import exotic.app.planta.service.productos.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
@Slf4j
public class ProductoResource {

    private final ProductoService productoService;

    /**
     * Endpoints Para la codificacion de materias primas, semi y terminados
     */


    /**
     * possibly to be deprecated but not clear still. at least, mprima is not going to be
     * persisted anylonger by this endpoint.
     * @param producto
     * @return
     */
    @PostMapping("/save")
    public ResponseEntity<Producto> saveProducto(@RequestBody Producto producto){
        return ResponseEntity.created(URI.create("/productos/productoID")).body(productoService.saveProducto(producto));
    }


    @PostMapping(value = "/save_mprima_v2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveMateriaPrimaV2(
            @RequestPart("materiaPrima") Material material,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        try {
            Material savedMP = productoService.saveMateriaPrimaV2(material, file);
            // You can customize the URI as needed.
            return ResponseEntity.created(URI.create("/productos/" + savedMP.getProductoId()))
                    .body(savedMP);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error guardando el Material: " + e.getMessage());
        }
    }


    @GetMapping("/search_p4_receta_v2")
    public ResponseEntity<Page<Producto>> searchP4RecetaV2(
            @RequestParam String searchTerm,
            @RequestParam String tipoBusqueda,   // "NOMBRE" or "ID"
            @RequestParam String clasificacion,  // "Terminado" or "Semiterminado"
            @RequestParam int page,
            @RequestParam int size) {
        Page<Producto> result = productoService.searchP4RecetaV2(searchTerm, tipoBusqueda, clasificacion, page, size);
        return ResponseEntity.ok(result);
    }


    @GetMapping("/search_mprima")
    public ResponseEntity<Page<Material>> search_mprima(
            @RequestParam(value="page", defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam String search,
            @RequestParam String tipoBusqueda,
            @RequestParam(value = "fuzzy", defaultValue = "false") boolean fuzzy,
            @RequestParam(value = "threshold", defaultValue = "0.3") double threshold) {

        // Si estamos en modo ID pero el parámetro 'search' está vacío,
        // redirigimos a la búsqueda por nombre (que hace LIKE "%%" y retorna todo).
        if ("ID".equalsIgnoreCase(tipoBusqueda)) {
            if (search == null || search.trim().isEmpty()) {
                // -> devuelve todas las materias primas (paginadas)
                Page<Material> todas = productoService.searchByName_MP("", page, size, false, threshold);
                return ResponseEntity.ok(todas);
            }

            String id = search.trim();
            Optional<Material> mpOpt = productoService.findMateriaPrimaByProductoId(id);
            List<Material> lista = mpOpt.map(List::of).orElse(List.of());
            Page<Material> resultado = new PageImpl<>(lista, PageRequest.of(page, size), lista.size());
            return ResponseEntity.ok(resultado);
        } else {
            // Modo NOMBRE (incluye el caso search == "")
            Page<Material> resultadoNombre = productoService.searchByName_MP(search, page, size, fuzzy, threshold);
            return ResponseEntity.ok(resultadoNombre);
        }
    }

    @GetMapping("/search_semi")
    public ResponseEntity<Page<SemiTerminado>> search_semi(
            @RequestParam(value="page", defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam String search,
            @RequestParam String tipoBusqueda,
            @RequestParam(value = "fuzzy", defaultValue = "false") boolean fuzzy,
            @RequestParam(value = "threshold", defaultValue = "0.3") double threshold)
    {
        if(tipoBusqueda.equals("ID")){
            Optional<SemiTerminado> semiTerminadoOptional = productoService.findSemiTerminadoByProductoId(search);
            if (semiTerminadoOptional.isPresent()) {
                List<SemiTerminado> semiTerminadoList = List.of(semiTerminadoOptional.get());
                Pageable pageable = PageRequest.of(page, size);
                return ResponseEntity.ok().body(new PageImpl<>(semiTerminadoList, pageable, 1));
            } else {
                return ResponseEntity.ok().body(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));
            }
        } else{
            return ResponseEntity.ok().body(productoService.searchByName_S(search, page, size, fuzzy, threshold));
        }
    }


    @GetMapping("/search_semiytermi")
    public ResponseEntity<Page<ProductoStockDTO>> searchProductos(
            @RequestParam String searchTerm,
            @RequestParam String tipoBusqueda, // 'NOMBRE' or 'ID'
            @RequestParam int page,
            @RequestParam int size
    ) {
        Page<ProductoStockDTO> productos = productoService.searchTerminadoAndSemiTerminadoWithStock(searchTerm, tipoBusqueda, page, size);
        return ResponseEntity.ok().body(productos);
    }

    /**
     * Valida si el prefijo de lote esta disponible (unico entre productos terminados).
     * productoId es opcional: al editar, pasarlo para excluir el producto actual.
     */
    @GetMapping("/prefijo-lote/valido")
    public ResponseEntity<Map<String, Object>> isPrefijoLoteValido(
            @RequestParam String prefijoLote,
            @RequestParam(required = false) String productoId) {
        boolean valido = productoService.isPrefijoLoteDisponible(prefijoLote, productoId);
        if (valido) {
            return ResponseEntity.ok(Map.of("valido", true));
        }
        return ResponseEntity.ok(Map.of("valido", false, "mensaje",
                "El prefijo de lote ya esta asignado a otro producto terminado."));
    }

    @GetMapping("/{productoId}")
    public ResponseEntity<Producto> getProductoById(@PathVariable String productoId) {
        return productoService.findProductoById(productoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/terminado/{productoId}/case-pack")
    public ResponseEntity<CasePackResponseDTO> getCasePackByTerminadoId(@PathVariable String productoId) {
        Optional<Terminado> terminadoOpt = productoService.findTerminadoByProductoId(productoId);
        if (terminadoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CasePackResponseDTO dto = CasePackResponseDTO.fromCasePack(terminadoOpt.get().getCasePack());
        return ResponseEntity.ok(dto);
    }


    @GetMapping("/{productoId}/insumos_with_stock")
    public ResponseEntity<List<InsumoWithStockDTO>> getInsumosWithStock(@PathVariable String productoId) {
        List<InsumoWithStockDTO> insumosWithStock = productoService.getInsumosWithStock(productoId);
        return ResponseEntity.ok().body(insumosWithStock);
    }




    /**
     * Endpoints para Process Designer feature
     */

    // Endpoint to search for Terminados (targets)
    @GetMapping("/search_terminados")
    public ResponseEntity<Page<TargetDTO>> searchTerminados(
            @RequestParam String searchTerm,
            @RequestParam String tipoBusqueda,  // "NOMBRE" or "ID"
            @RequestParam int page,
            @RequestParam int size) {
        Page<Terminado> pageResult;

        if ("ID".equalsIgnoreCase(tipoBusqueda)) {
            // Search by ID
            Optional<Terminado> terminadoOpt = productoService.findTerminadoByProductoId(searchTerm);
            List<Terminado> result = terminadoOpt.map(List::of).orElse(List.of());
            pageResult = new PageImpl<>(result, PageRequest.of(page, size), result.size());
        } else {
            // Search by name
            pageResult = productoService.searchByName_T(searchTerm, page, size);
        }

        Page<TargetDTO> dtoPage = pageResult.map(TargetDTO::fromProducto);
        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Endpoint para buscar productos terminados para el módulo de creación manual de órdenes de producción.
     * Este endpoint es utilizado específicamente por el componente terminado picker en el proceso
     * de creación de órdenes de producción.
     * 
     * @param searchCriteria Criterios de búsqueda para productos terminados
     * @return Lista paginada de productos terminados que coinciden con los criterios
     */
    @PostMapping("/search_terminados_picker")
    public ResponseEntity<Page<Terminado>> searchTerminadosPicker(
            @RequestBody DTO_SearchTerminado searchCriteria) {
        Page<Terminado> pageResult;

        // Establecer valores predeterminados si no se proporcionan
        int page = searchCriteria.getPage() != null ? searchCriteria.getPage() : 0;
        int size = searchCriteria.getSize() != null ? searchCriteria.getSize() : 10;
        String searchTerm = searchCriteria.getSearchTerm() != null ? searchCriteria.getSearchTerm() : "";

        if (searchCriteria.getTipoBusqueda() == DTO_SearchTerminado.TipoBusqueda.ID) {
            // Búsqueda por ID
            Optional<Terminado> terminadoOpt = productoService.findTerminadoByProductoId(searchTerm);
            List<Terminado> result = terminadoOpt.map(List::of).orElse(List.of());
            pageResult = new PageImpl<>(result, PageRequest.of(page, size), result.size());
        } else {
            // Búsqueda por nombre (valor predeterminado)
            pageResult = productoService.searchByName_T(searchTerm, page, size);
        }

        // Ya no es necesario convertir a TargetDTO
        return ResponseEntity.ok(pageResult);
    }

    // Endpoint para buscar semiterminados para la feuture de process designer
    @GetMapping("/search_semi_4pd")
    public ResponseEntity<Page<TargetDTO>> searchSemiterminados(
            @RequestParam String search,
            @RequestParam String tipoBusqueda, // "NOMBRE" or "ID"
            @RequestParam int page,
            @RequestParam int size) {
        Page<SemiTerminado> pageResult;

        if ("ID".equalsIgnoreCase(tipoBusqueda)) {
            // Search by ID
            Optional<SemiTerminado> semiTerminadoOpt = productoService.findSemiTerminadoByProductoId(search);
            List<SemiTerminado> result = semiTerminadoOpt.map(List::of).orElse(List.of());
            pageResult = new PageImpl<>(result, PageRequest.of(page, size), result.size());
        } else {
            // Search by name
            pageResult = productoService.searchByName_S(search, page, size, false, 0.3);
        }

        Page<TargetDTO> dtoPage = pageResult.map(TargetDTO::fromProducto);
        return ResponseEntity.ok(dtoPage);
    }


    /**
     * para hacer carga masiva desde arhcivo de excel local
     * @param file
     * @return
     */
    @PostMapping(value = "/bulk_upload_excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> bulkUploadExcel(@RequestPart("file") MultipartFile file) {
        try {
            int count = productoService.bulkUploadMateriasPrimas(file);
            return ResponseEntity.ok("Successfully uploaded " + count + " records.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        }
    }


    /**
     * para usar en el tab de Consulta de productos. aca se filtra solamente por cateforias:
     * terminadoo, semiterminado, materia prima o material de empaque
     * @param criteria
     * @return
     */
    @PostMapping("/consulta1")
    public Page<Producto> searchProductos(@RequestBody ProductoSearchCriteria criteria) {
        int page = criteria.getPage() != null ? criteria.getPage() : 0;
        int size = criteria.getSize() != null ? criteria.getSize() : 10;
        return productoService.consultaProductos(criteria.getSearch(), criteria.getCategories(), page, size);
    }

    /**
     * Endpoint para actualizar un producto existente.
     * Permite modificar campos como nombre, tipo de material (para productos tipo Material),
     * cantidad por unidad, porcentaje de IVA y observaciones.
     * 
     * @param productoId ID del producto a actualizar
     * @param producto Objeto Producto con los datos actualizados
     * @return El producto actualizado
     */
    @PutMapping("/{productoId}")
    public ResponseEntity<Object> updateProducto(
            @PathVariable String productoId,
            @RequestBody Producto producto) {
        try {
            // Delegar toda la lógica al método del servicio
            Producto updatedProducto = productoService.updateProducto(productoId, producto);
            return ResponseEntity.ok(updatedProducto);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al actualizar el producto: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al actualizar el producto: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar el producto: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{productoId}")
    public ResponseEntity<Object> deleteMaterial(@PathVariable String productoId) {
        try {
            productoService.deleteMaterial(productoId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
