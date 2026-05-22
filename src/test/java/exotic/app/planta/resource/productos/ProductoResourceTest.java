package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.dto.ProductoInventareableUpdateDTO;
import exotic.app.planta.service.productos.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductoResourceTest {

    @Test
    void updateMaterialInventareableReturnsOkWithUpdatedMaterial() {
        ProductoService service = mock(ProductoService.class);
        ProductoResource resource = new ProductoResource(service);
        Material material = material("M-1", false);
        when(service.updateMaterialInventareable("M-1", false)).thenReturn(material);

        ResponseEntity<Object> response = resource.updateMaterialInventareable(
                "M-1",
                new ProductoInventareableUpdateDTO(false)
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(material, response.getBody());
    }

    @Test
    void updateMaterialInventareableReturnsBadRequestWhenPayloadIsInvalid() {
        ProductoService service = mock(ProductoService.class);
        ProductoResource resource = new ProductoResource(service);

        ResponseEntity<Object> response = resource.updateMaterialInventareable(
                "M-1",
                new ProductoInventareableUpdateDTO(null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void updateMaterialInventareableReturnsBadRequestForNonMaterialProduct() {
        ProductoService service = mock(ProductoService.class);
        ProductoResource resource = new ProductoResource(service);
        doThrow(new IllegalArgumentException("Solo los materiales pueden cambiar el estado inventareable."))
                .when(service).updateMaterialInventareable("S-1", true);

        ResponseEntity<Object> response = resource.updateMaterialInventareable(
                "S-1",
                new ProductoInventareableUpdateDTO(true)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void updateMaterialInventareableReturnsNotFoundWhenProductDoesNotExist() {
        ProductoService service = mock(ProductoService.class);
        ProductoResource resource = new ProductoResource(service);
        doThrow(new NoSuchElementException("Producto no encontrado: M-X"))
                .when(service).updateMaterialInventareable("M-X", false);

        ResponseEntity<Object> response = resource.updateMaterialInventareable(
                "M-X",
                new ProductoInventareableUpdateDTO(false)
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void updateMaterialInventareableReturnsConflictWhenOperationalRulesBlockChange() {
        ProductoService service = mock(ProductoService.class);
        ProductoResource resource = new ProductoResource(service);
        doThrow(new IllegalStateException("No se puede marcar el material como no inventariable."))
                .when(service).updateMaterialInventareable("M-2", false);

        ResponseEntity<Object> response = resource.updateMaterialInventareable(
                "M-2",
                new ProductoInventareableUpdateDTO(false)
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("reason"));
    }

    private static Material material(String productoId, boolean inventareable) {
        Material material = new Material();
        material.setProductoId(productoId);
        material.setInventareable(inventareable);
        return material;
    }
}
