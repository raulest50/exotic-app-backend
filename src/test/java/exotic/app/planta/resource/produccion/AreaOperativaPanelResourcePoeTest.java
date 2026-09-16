package exotic.app.planta.resource.produccion;

import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.produccion.AreaOperativaPanelDetalleService;
import exotic.app.planta.service.produccion.AreaOperativaPoeService;
import exotic.app.planta.service.produccion.AreaOperativaRuidoMuestraService;
import exotic.app.planta.service.produccion.MasterProductionScheduleDraftService;
import exotic.app.planta.service.produccion.MasterProductionScheduleOrderGenerationService;
import exotic.app.planta.service.produccion.OrdenFabricacionOperacionService;
import exotic.app.planta.service.produccion.OrdenFabricacionService;
import exotic.app.planta.service.productos.procesos.ProcesoProduccionDocumentoPdfService;
import exotic.app.planta.service.users.UserOperationalCompatibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AreaOperativaPanelResourcePoeTest {

    @Mock
    private AreaOperativaPanelDetalleService detalleService;
    @Mock
    private AreaOperativaPoeService poeService;
    @Mock
    private AreaOperativaRuidoMuestraService ruidoService;
    @Mock
    private MasterProductionScheduleDraftService mpsService;
    @Mock
    private MasterProductionScheduleOrderGenerationService mpsOrderService;
    @Mock
    private UserOperationalCompatibilityService compatibilityService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrdenFabricacionOperacionService operacionService;
    @Mock
    private OrdenFabricacionService ordenFabricacionService;
    @InjectMocks
    private AreaOperativaPanelResource resource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
    }

    @Test
    void entregaLaVistaComoPdfInlineSinCache() throws Exception {
        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes();
        User user = new User();
        user.setId(9L);
        user.setUsername("lider");
        when(userRepository.findByUsername("lider")).thenReturn(Optional.of(user));
        when(poeService.getDescarga(701, 81L, 9L)).thenReturn(
                new ProcesoProduccionDocumentoPdfService.DocumentoPdf(
                        new ByteArrayResource(pdf), "poe-mezcla.pdf", (long) pdf.length));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("lider", "n/a", List.of());

        mockMvc.perform(get(
                        "/api/area-operativa-panel/ordenes/701/seguimientos/81/poe/archivo")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION, startsWith("inline;")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
