package exotic.app.planta.service.controles;

import exotic.app.planta.model.controles.MagnitudControl;
import exotic.app.planta.model.controles.dto.ControlDTOs.CatalogoResponse;
import exotic.app.planta.model.controles.dto.ControlDTOs.CatalogoWriteRequest;
import exotic.app.planta.repo.controles.CaracteristicaPlanControlRepo;
import exotic.app.planta.repo.controles.MagnitudControlRepo;
import exotic.app.planta.repo.controles.UnidadControlRepo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControlCatalogServiceTest {

    @Test
    void crearMagnitudConservaYExponeSuSimbolo() {
        MagnitudControlRepo magnitudRepo = mock(MagnitudControlRepo.class);
        CaracteristicaPlanControlRepo caracteristicaRepo = mock(CaracteristicaPlanControlRepo.class);
        ControlCatalogService service = new ControlCatalogService(
                magnitudRepo, mock(UnidadControlRepo.class), caracteristicaRepo);
        when(magnitudRepo.findByCodigoIgnoreCase("TEMPERATURA")).thenReturn(Optional.empty());
        when(magnitudRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            MagnitudControl item = invocation.getArgument(0);
            item.setId(41L);
            return item;
        });

        CatalogoResponse response = service.crearMagnitud(new CatalogoWriteRequest(
                "temperatura", "Temperatura", "temperatura", "T"));

        assertEquals("TEMPERATURA", response.codigo());
        assertEquals("T", response.simbolo());
        assertEquals("TEMPERATURA", response.dimension());
    }
}
