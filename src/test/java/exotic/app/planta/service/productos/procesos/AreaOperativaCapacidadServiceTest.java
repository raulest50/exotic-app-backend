package exotic.app.planta.service.productos.procesos;

import exotic.app.planta.dto.CapacidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaRequestDTO;
import exotic.app.planta.dto.ConversionUnidadAreaOperativaResponseDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaDTO;
import exotic.app.planta.dto.UnidadMedidaAreaOperativaRequestDTO;
import exotic.app.planta.model.organizacion.AreaOperativa;
import exotic.app.planta.model.organizacion.DimensionUnidadAreaOperativa;
import exotic.app.planta.model.organizacion.PeriodoCapacidadAreaOperativa;
import exotic.app.planta.model.organizacion.TipoCapacidadAreaOperativa;
import exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa;
import exotic.app.planta.repo.producto.procesos.AreaProduccionRepo;
import exotic.app.planta.repo.producto.procesos.CapacidadAreaOperativaRepo;
import exotic.app.planta.repo.producto.procesos.UnidadMedidaAreaOperativaRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AreaOperativaCapacidadServiceTest {

    @Test
    void crearUnidad_validRequest_savesUnidad() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        CapacidadAreaOperativaRepo capacidadRepo = mock(CapacidadAreaOperativaRepo.class);
        AreaOperativaCapacidadService service = new AreaOperativaCapacidadService(areaRepo, unidadRepo, capacidadRepo);

        AreaOperativa area = buildArea(10, "Fabricacion 1");
        when(areaRepo.findById(10)).thenReturn(Optional.of(area));
        when(unidadRepo.existsByAreaOperativa_AreaIdAndCodigoIgnoreCase(10, "MARMITA")).thenReturn(false);
        when(unidadRepo.findAllByAreaOperativa_AreaIdOrderByPrincipalDescNombreAsc(10)).thenReturn(List.of());
        when(unidadRepo.save(any(UnidadMedidaAreaOperativa.class))).thenAnswer(invocation -> {
            UnidadMedidaAreaOperativa unidad = invocation.getArgument(0);
            unidad.setId(5L);
            return unidad;
        });

        UnidadMedidaAreaOperativaDTO result = service.crearUnidad(10, new UnidadMedidaAreaOperativaRequestDTO(
                "marmita",
                "Marmita",
                "Marmita de calentamiento",
                DimensionUnidadAreaOperativa.VOLUMEN,
                "l",
                new BigDecimal("1000"),
                true,
                true,
                true
        ));

        assertEquals(5L, result.getId());
        assertEquals("MARMITA", result.getCodigo());
        assertEquals("L", result.getUnidadReferencia());
        assertEquals(new BigDecimal("1000"), result.getFactorAReferencia());
    }

    @Test
    void crearUnidad_duplicateCode_throwsValidationError() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        CapacidadAreaOperativaRepo capacidadRepo = mock(CapacidadAreaOperativaRepo.class);
        AreaOperativaCapacidadService service = new AreaOperativaCapacidadService(areaRepo, unidadRepo, capacidadRepo);

        when(areaRepo.findById(10)).thenReturn(Optional.of(buildArea(10, "Fabricacion 1")));
        when(unidadRepo.existsByAreaOperativa_AreaIdAndCodigoIgnoreCase(10, "MARMITA")).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearUnidad(10, new UnidadMedidaAreaOperativaRequestDTO(
                        "MARMITA",
                        "Marmita",
                        null,
                        DimensionUnidadAreaOperativa.VOLUMEN,
                        "L",
                        BigDecimal.ONE,
                        false,
                        true,
                        true
                ))
        );

        assertEquals("Ya existe una unidad con codigo MARMITA para esta area operativa", error.getMessage());
    }

    @Test
    void crearCapacidad_inactiveUnidad_throwsValidationError() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        CapacidadAreaOperativaRepo capacidadRepo = mock(CapacidadAreaOperativaRepo.class);
        AreaOperativaCapacidadService service = new AreaOperativaCapacidadService(areaRepo, unidadRepo, capacidadRepo);

        AreaOperativa area = buildArea(10, "Fabricacion 1");
        UnidadMedidaAreaOperativa unidad = buildUnidad(3L, area, "MARMITA", "L", "1000");
        unidad.setActivo(false);

        when(areaRepo.findById(10)).thenReturn(Optional.of(area));
        when(unidadRepo.findByIdAndAreaOperativa_AreaId(3L, 10)).thenReturn(Optional.of(unidad));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.crearCapacidad(10, new CapacidadAreaOperativaRequestDTO(
                        3L,
                        TipoCapacidadAreaOperativa.PRODUCTIVA,
                        new BigDecimal("3"),
                        PeriodoCapacidadAreaOperativa.DIA,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        true
                ))
        );

        assertEquals("La unidad de medida seleccionada no esta activa", error.getMessage());
    }

    @Test
    void convertir_compatibleUnits_returnsConvertedQuantity() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        CapacidadAreaOperativaRepo capacidadRepo = mock(CapacidadAreaOperativaRepo.class);
        AreaOperativaCapacidadService service = new AreaOperativaCapacidadService(areaRepo, unidadRepo, capacidadRepo);

        AreaOperativa fabricacion1 = buildArea(10, "Fabricacion 1");
        AreaOperativa fabricacion2 = buildArea(11, "Fabricacion 2");
        UnidadMedidaAreaOperativa marmita = buildUnidad(1L, fabricacion1, "MARMITA", "L", "1000");
        UnidadMedidaAreaOperativa tarroAzul = buildUnidad(2L, fabricacion2, "TARRO_AZUL", "L", "200");

        when(unidadRepo.findById(1L)).thenReturn(Optional.of(marmita));
        when(unidadRepo.findById(2L)).thenReturn(Optional.of(tarroAzul));

        ConversionUnidadAreaOperativaResponseDTO result = service.convertir(
                new ConversionUnidadAreaOperativaRequestDTO(1L, BigDecimal.ONE, 2L)
        );

        assertEquals(new BigDecimal("1000"), result.getCantidadReferencia());
        assertEquals(new BigDecimal("5.000000"), result.getCantidadDestino());
    }

    @Test
    void convertir_incompatibleUnits_throwsValidationError() {
        AreaProduccionRepo areaRepo = mock(AreaProduccionRepo.class);
        UnidadMedidaAreaOperativaRepo unidadRepo = mock(UnidadMedidaAreaOperativaRepo.class);
        CapacidadAreaOperativaRepo capacidadRepo = mock(CapacidadAreaOperativaRepo.class);
        AreaOperativaCapacidadService service = new AreaOperativaCapacidadService(areaRepo, unidadRepo, capacidadRepo);

        UnidadMedidaAreaOperativa litros = buildUnidad(1L, buildArea(10, "Fabricacion"), "LITROS", "L", "1");
        UnidadMedidaAreaOperativa kilos = buildUnidad(2L, buildArea(11, "Pesaje"), "KILOS", "KG", "1");
        kilos.setDimension(DimensionUnidadAreaOperativa.MASA);

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
            String codigo,
            String referencia,
            String factor
    ) {
        UnidadMedidaAreaOperativa unidad = new UnidadMedidaAreaOperativa();
        unidad.setId(id);
        unidad.setAreaOperativa(area);
        unidad.setCodigo(codigo);
        unidad.setNombre(codigo);
        unidad.setDimension(DimensionUnidadAreaOperativa.VOLUMEN);
        unidad.setUnidadReferencia(referencia);
        unidad.setFactorAReferencia(new BigDecimal(factor));
        unidad.setActivo(true);
        return unidad;
    }
}
