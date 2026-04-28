package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialDTO;
import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialPageRowDTO;
import exotic.app.planta.model.bi.dto.LeadTimeStatsDTO;
import exotic.app.planta.model.bi.dto.PuntoReordenEstimadoDTO;
import exotic.app.planta.service.bi.ProveedoresBiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProveedoresBiResource.class)
@AutoConfigureMockMvc(addFilters = false)
class ProveedoresBiResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProveedoresBiService proveedoresBiService;

    @Test
    void calcularLeadTimeProveedorMaterial_returnsPayload() throws Exception {
        LeadTimeStatsDTO first = new LeadTimeStatsDTO(
                true,
                null,
                3.0,
                3.0,
                3.0,
                3.0,
                3.0,
                0.0,
                1,
                1,
                81,
                LocalDateTime.of(2026, 3, 10, 8, 0)
        );
        LeadTimeProveedorMaterialDTO dto = new LeadTimeProveedorMaterialDTO(
                "PROV-1",
                "Proveedor Uno",
                "MAT-1",
                "Material Uno",
                LocalDate.of(2026, 3, 31),
                365,
                1,
                first,
                first
        );

        when(proveedoresBiService.calcularLeadTimeProveedorMaterial(
                eq("PROV-1"),
                eq("MAT-1"),
                eq(LocalDate.of(2026, 3, 31)),
                eq(365)
        )).thenReturn(dto);

        mockMvc.perform(get("/bi/proveedores/lead-time")
                        .param("proveedorId", "PROV-1")
                        .param("materialId", "MAT-1")
                        .param("fechaCorte", "2026-03-31")
                        .param("ventanaDias", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedorId").value("PROV-1"))
                .andExpect(jsonPath("$.materialId").value("MAT-1"))
                .andExpect(jsonPath("$.firstReceipt.representativeLeadTimeDays").value(3.0));
    }

    @Test
    void listarLeadTimesPorMaterial_returnsBadRequestWhenDirectionIsInvalid() throws Exception {
        when(proveedoresBiService.listarLeadTimesPorMaterial(
                eq("MAT-1"),
                eq(LocalDate.of(2026, 3, 31)),
                eq(365),
                eq(0),
                eq(10),
                eq("sideways")
        )).thenThrow(new IllegalArgumentException("direction debe ser 'asc' o 'desc'."));

        mockMvc.perform(get("/bi/proveedores/materiales/MAT-1/lead-times")
                        .param("fechaCorte", "2026-03-31")
                        .param("ventanaDias", "365")
                        .param("page", "0")
                        .param("size", "10")
                        .param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("direction debe ser 'asc' o 'desc'."));
    }

    @Test
    void listarLeadTimesPorMaterial_returnsPagePayload() throws Exception {
        LeadTimeProveedorMaterialPageRowDTO row = new LeadTimeProveedorMaterialPageRowDTO(
                "PROV-1",
                "Proveedor Uno",
                "MAT-1",
                "Material Uno",
                2.0,
                2.0,
                90,
                90,
                3,
                3,
                3,
                2.1
        );

        when(proveedoresBiService.listarLeadTimesPorMaterial(
                eq("MAT-1"),
                eq(LocalDate.of(2026, 3, 31)),
                eq(365),
                eq(0),
                eq(10),
                eq("asc")
        )).thenReturn(new PageImpl<>(List.of(row)));

        mockMvc.perform(get("/bi/proveedores/materiales/MAT-1/lead-times")
                        .param("fechaCorte", "2026-03-31")
                        .param("ventanaDias", "365")
                        .param("page", "0")
                        .param("size", "10")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].proveedorId").value("PROV-1"))
                .andExpect(jsonPath("$.content[0].adjustedLeadTimeDays").value(2.1));
    }

    @Test
    void estimarPuntoReorden_returnsNotFoundWhenMaterialDoesNotExist() throws Exception {
        when(proveedoresBiService.estimarPuntoReorden(
                eq("MAT-X"),
                eq(LocalDate.of(2026, 3, 31)),
                eq(365)
        )).thenThrow(new NoSuchElementException("Material no encontrado: MAT-X"));

        mockMvc.perform(get("/bi/proveedores/materiales/MAT-X/reorder-point-estimate")
                        .param("fechaCorte", "2026-03-31")
                        .param("ventanaDias", "365"))
                .andExpect(status().isNotFound());
    }

    @Test
    void estimarPuntoReorden_returnsNoDataPayloadWhenServiceProvidesIt() throws Exception {
        PuntoReordenEstimadoDTO dto = new PuntoReordenEstimadoDTO(
                "MAT-2",
                "Agua de proceso",
                LocalDate.of(2026, 3, 31),
                365,
                "NO_DATA",
                "El material no es inventareable.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                365,
                0,
                0,
                0
        );

        when(proveedoresBiService.estimarPuntoReorden(
                eq("MAT-2"),
                eq(LocalDate.of(2026, 3, 31)),
                eq(365)
        )).thenReturn(dto);

        mockMvc.perform(get("/bi/proveedores/materiales/MAT-2/reorder-point-estimate")
                        .param("fechaCorte", "2026-03-31")
                        .param("ventanaDias", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodoUsado").value("NO_DATA"))
                .andExpect(jsonPath("$.reason").value("El material no es inventareable."));
    }
}
