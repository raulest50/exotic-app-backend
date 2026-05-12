package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.dto.CategoriaResponseDTO;
import exotic.app.planta.service.productos.CategoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CategoriaResourceTest {

    @Test
    void updatePoolCapacidad_missingField_returnsBadRequest() {
        CategoriaService categoriaService = mock(CategoriaService.class);
        CategoriaResource resource = new CategoriaResource(categoriaService);

        ResponseEntity<?> response = resource.updatePoolCapacidad(10, Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(categoriaService);
    }

    @Test
    void updatePoolCapacidad_invalidType_returnsBadRequest() {
        CategoriaService categoriaService = mock(CategoriaService.class);
        CategoriaResource resource = new CategoriaResource(categoriaService);

        ResponseEntity<?> response = resource.updatePoolCapacidad(10, Map.of("poolCapacidadId", "abc"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(categoriaService);
    }

    @Test
    void updatePoolCapacidad_explicitNull_unassignsPool() {
        CategoriaService categoriaService = mock(CategoriaService.class);
        CategoriaResource resource = new CategoriaResource(categoriaService);
        CategoriaResponseDTO categoria = new CategoriaResponseDTO();
        categoria.setCategoriaId(10);
        categoria.setCategoriaNombre("Shampoo");

        when(categoriaService.updatePoolCapacidad(10, null)).thenReturn(categoria);

        Map<String, Object> body = new HashMap<>();
        body.put("poolCapacidadId", null);
        ResponseEntity<?> response = resource.updatePoolCapacidad(10, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(categoriaService).updatePoolCapacidad(10, null);
    }

    @Test
    void updatePoolCapacidad_integerValue_assignsPool() {
        CategoriaService categoriaService = mock(CategoriaService.class);
        CategoriaResource resource = new CategoriaResource(categoriaService);
        CategoriaResponseDTO categoria = new CategoriaResponseDTO();
        categoria.setCategoriaId(10);
        categoria.setCategoriaNombre("Shampoo");

        when(categoriaService.updatePoolCapacidad(10, 7)).thenReturn(categoria);

        ResponseEntity<?> response = resource.updatePoolCapacidad(10, Map.of("poolCapacidadId", 7));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(categoriaService).updatePoolCapacidad(10, 7);
    }
}
