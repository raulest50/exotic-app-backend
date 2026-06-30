package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.ConversionUnidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaResponseDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaRequestDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import exotic.app.planta.model.organizacion.UnidadRelacionAreaOperativa;
import exotic.app.planta.repo.producto.procesos.AreaOperativaCategoriaUnidadMedidaRepo;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.UnidadMedidaAreaOperativaRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AreaOperativaUnidadMedidaServiceTest {

    @Test
    void crearUnidad_validRequest_savesUnidad() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        AreaOperativaCategoriaUnidadMedidaRepo areaCategoriaUnidadRepo = mock(AreaOperativaCategoriaUnidadMedidaRepo.class);
        AreaOperativaUnidadMedidaService service = new AreaOperativaUnidadMedidaService(
                areaRepo,
                unidadRepo,
                areaCategoriaUnidadRepo
        );

        AreaOperativa area = buildArea(10, "Fabricacion 1");
        when(areaRepo.findById(10)).thenReturn(Optional.of(area));
        when(unidadRepo.existsByNombreIgnoreCase("Marmita")).thenReturn(false);
        when(unidadRepo.save(any(UnidadMedidaAreaOperativa.class))).thenAnswer(invocation -> {
            UnidadMedidaAreaOperativa unidad = invocation.getArgument(0);
            unidad.setId(5L);
            return unidad;
        });

        UnidadMedidaAreaOperativaDTO result = service.crearUnidad(10, new UnidadMedidaAreaOperativaRequestDTO(
                "Marmita",
                new BigDecimal("1000"),
                UnidadRelacionAreaOperativa.L
        ));

        assertEquals(5L, result.getId());
        assertEquals("Marmita", result.getNombre());
        assertEquals(UnidadRelacionAreaOperativa.L, result.getUnidadRelacion());
        assertEquals(new BigDecimal("1000"), result.getRelacionEstandar());
    }

    @Test
    void crearUnidad_duplicateName_throwsValidationError() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        AreaOperativaCategoriaUnidadMedidaRepo areaCategoriaUnidadRepo = mock(AreaOperativaCategoriaUnidadMedidaRepo.class);
        AreaOperativaUnidadMedidaService service = new AreaOperativaUnidadMedidaService(
                areaRepo,
                unidadRepo,
                areaCategoriaUnidadRepo
        );

        when(areaRepo.findById(10)).thenReturn(Optional.of(buildArea(10, "Fabricacion 1")));
        when(unidadRepo.existsByNombreIgnoreCase("Marmita")).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearUnidad(10, new UnidadMedidaAreaOperativaRequestDTO(
                        "Marmita",
                        BigDecimal.ONE,
                        UnidadRelacionAreaOperativa.L
                ))
        );

        assertEquals("Ya existe una unidad de medida con el nombre: Marmita", error.getMessage());
    }

    @Test
    void convertir_compatibleUnits_returnsConvertedQuantity() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        AreaOperativaCategoriaUnidadMedidaRepo areaCategoriaUnidadRepo = mock(AreaOperativaCategoriaUnidadMedidaRepo.class);
        AreaOperativaUnidadMedidaService service = new AreaOperativaUnidadMedidaService(
                areaRepo,
                unidadRepo,
                areaCategoriaUnidadRepo
        );

        AreaOperativa fabricacion1 = buildArea(10, "Fabricacion 1");
        AreaOperativa fabricacion2 = buildArea(11, "Fabricacion 2");
        UnidadMedidaAreaOperativa marmita = buildUnidad(1L, fabricacion1, "Marmita", UnidadRelacionAreaOperativa.L, "1000");
        UnidadMedidaAreaOperativa tarroAzul = buildUnidad(2L, fabricacion2, "Tanque azul", UnidadRelacionAreaOperativa.L, "200");

        when(unidadRepo.findById(1L)).thenReturn(Optional.of(marmita));
        when(unidadRepo.findById(2L)).thenReturn(Optional.of(tarroAzul));

        ConversionUnidadAreaOperativaResponseDTO result = service.convertir(
                new ConversionUnidadAreaOperativaRequestDTO(1L, BigDecimal.ONE, 2L)
        );

        assertEquals(new BigDecimal("1000000"), result.getCantidadBase());
        assertEquals(new BigDecimal("5.000000"), result.getCantidadDestino());
    }

    @Test
    void convertir_incompatibleUnits_throwsValidationError() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        AreaOperativaCategoriaUnidadMedidaRepo areaCategoriaUnidadRepo = mock(AreaOperativaCategoriaUnidadMedidaRepo.class);
        AreaOperativaUnidadMedidaService service = new AreaOperativaUnidadMedidaService(
                areaRepo,
                unidadRepo,
                areaCategoriaUnidadRepo
        );

        UnidadMedidaAreaOperativa litros = buildUnidad(1L, buildArea(10, "Fabricacion"), "Litros", UnidadRelacionAreaOperativa.L, "1");
        UnidadMedidaAreaOperativa kilos = buildUnidad(2L, buildArea(11, "Pesaje"), "Kilos", UnidadRelacionAreaOperativa.KG, "1");

        when(unidadRepo.findById(1L)).thenReturn(Optional.of(litros));
        when(unidadRepo.findById(2L)).thenReturn(Optional.of(kilos));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.convertir(new ConversionUnidadAreaOperativaRequestDTO(1L, BigDecimal.ONE, 2L))
        );

        assertEquals("Las unidades no son compatibles para conversion", error.getMessage());
    }

    private AreaOperativa buildArea(int id, String nombre) {
        AreaOperativa area = new AreaOperativa();
        area.setAreaId(id);
        area.setNombre(nombre);
        return area;
    }

    private UnidadMedidaAreaOperativa buildUnidad(
            Long id,
            AreaOperativa area,
            String nombre,
            UnidadRelacionAreaOperativa unidadRelacion,
            String factor
    ) {
        UnidadMedidaAreaOperativa unidad = new UnidadMedidaAreaOperativa();
        unidad.setId(id);
        unidad.setAreaOperativa(area);
        unidad.setNombre(nombre);
        unidad.setUnidadRelacion(unidadRelacion);
        unidad.setRelacionEstandar(new BigDecimal(factor));
        return unidad;
    }
}
