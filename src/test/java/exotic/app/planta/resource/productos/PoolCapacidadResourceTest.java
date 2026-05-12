package exotic.app.planta.resource.productos;

import exotic.app.planta.model.producto.dto.PoolCapacidadUpsertRequestDTO;
import exotic.app.planta.resource.productos.exceptions.PoolCapacidadNotFoundException;
import exotic.app.planta.service.productos.PoolCapacidadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PoolCapacidadResourceTest {

    @Test
    void update_notFound_returns404() {
        PoolCapacidadService poolCapacidadService = mock(PoolCapacidadService.class);
        PoolCapacidadResource resource = new PoolCapacidadResource(poolCapacidadService);

        when(poolCapacidadService.update(15, new PoolCapacidadUpsertRequestDTO("Pool", 10, null, true)))
                .thenThrow(new PoolCapacidadNotFoundException("No se encontro pool de capacidad con ID: 15"));

        ResponseEntity<?> response = resource.update(15, new PoolCapacidadUpsertRequestDTO("Pool", 10, null, true));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
