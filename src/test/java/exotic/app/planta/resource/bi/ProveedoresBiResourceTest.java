package exotic.app.planta.resource.bi;

import exotic.app.planta.model.bi.dto.LeadTimeProveedorMaterialPageRowDTO;
import exotic.app.planta.model.bi.dto.ProveedorMaterialLeadTimeMetricDTO;
import exotic.app.planta.model.bi.dto.PuntoReordenEstimadoDTO;
import exotic.app.planta.model.compras.Proveedor;
import exotic.app.planta.model.compras.proveedor.metricas.EstadoLeadTimeProveedorKPI;
import exotic.app.planta.model.compras.proveedor.metricas.LeadTimeProveedorKPI;
import exotic.app.planta.repo.compras.ProveedorRepo;
import exotic.app.planta.repo.compras.proveedor.metricas.LeadTimeProveedorKPIRepo;
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
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
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

    @MockBean
    private ProveedorRepo proveedorRepo;

    @MockBean
    private LeadTimeProveedorKPIRepo leadTimeProveedorKPIRepo;

    @Test
    void obtenerLeadTimeProveedorKpi_returnsExistingKpi() throws Exception {
        Proveedor proveedor = proveedor(10L, "PROV-1", "Proveedor Uno");
        LeadTimeProveedorKPI kpi = new LeadTimeProveedorKPI();
        kpi.setProveedor(proveedor);
        kpi.setEstado(EstadoLeadTimeProveedorKPI.VIGENTE);
        kpi.setLeadTimeMedianoDias(12.0);
        kpi.setObservaciones(8);
        kpi.setOrdenesConsideradas(6);
        kpi.setFechaCorte(LocalDate.of(2026, 6, 7));
        kpi.setVentanaDias(365);
        kpi.setCalculadoEn(LocalDateTime.of(2026, 6, 7, 18, 0));
        kpi.setUltimaEvaluacionEn(LocalDateTime.of(2026, 6, 7, 18, 0));
        kpi.setUltimaFechaCorteEvaluada(LocalDate.of(2026, 6, 7));

        when(proveedorRepo.findById("PROV-1")).thenReturn(Optional.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(10L)).thenReturn(Optional.of(kpi));

        mockMvc.perform(get("/bi/proveedores/PROV-1/lead-time-kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedorId").value("PROV-1"))
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.leadTimeMedianoDias").value(12.0))
                .andExpect(jsonPath("$.observaciones").value(8))
                .andExpect(jsonPath("$.ordenesConsideradas").value(6));
    }

    @Test
    void obtenerLeadTimeProveedorKpi_returnsSinInformacionWhenProviderHasNoKpi() throws Exception {
        Proveedor proveedor = proveedor(10L, "PROV-1", "Proveedor Uno");
        when(proveedorRepo.findById("PROV-1")).thenReturn(Optional.of(proveedor));
        when(leadTimeProveedorKPIRepo.findByProveedor_Pk(10L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/bi/proveedores/PROV-1/lead-time-kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedorId").value("PROV-1"))
                .andExpect(jsonPath("$.estado").value("SIN_INFORMACION"))
                .andExpect(jsonPath("$.leadTimeMedianoDias").value(nullValue()))
                .andExpect(jsonPath("$.observaciones").value(0))
                .andExpect(jsonPath("$.ordenesConsideradas").value(0))
                .andExpect(jsonPath("$.motivoEstado").value("KPI no generado todavía."));
    }

    @Test
    void obtenerLeadTimeProveedorKpi_returnsNotFoundWhenProviderDoesNotExist() throws Exception {
        when(proveedorRepo.findById("PROV-X")).thenReturn(Optional.empty());

        mockMvc.perform(get("/bi/proveedores/PROV-X/lead-time-kpi"))
                .andExpect(status().isNotFound());
    }

    @Test
    void calcularLeadTimeProveedorMaterial_returnsPayload() throws Exception {
        ProveedorMaterialLeadTimeMetricDTO dto = new ProveedorMaterialLeadTimeMetricDTO(
                "PROV-1",
                "Proveedor Uno",
                "MAT-1",
                "Material Uno",
                LocalDate.of(2026, 3, 31),
                365,
                3.0,
                2,
                3,
                true,
                null,
                LocalDateTime.of(2026, 3, 31, 8, 0),
                1,
                1
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
                .andExpect(jsonPath("$.leadTimeMedianoDias").value(3.0))
                .andExpect(jsonPath("$.observaciones").value(2))
                .andExpect(jsonPath("$.observacionesConFallbackFechaEmision").value(1));
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

    private static Proveedor proveedor(Long pk, String id, String nombre) {
        Proveedor proveedor = new Proveedor();
        proveedor.setPk(pk);
        proveedor.setId(id);
        proveedor.setNombre(nombre);
        return proveedor;
    }
}
