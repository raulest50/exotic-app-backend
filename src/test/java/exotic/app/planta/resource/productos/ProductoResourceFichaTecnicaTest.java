package exotic.app.planta.resource.productos;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.model.producto.Material;
import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersion;
import exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersionResponse;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.productos.ProductoService;
import exotic.app.planta.service.productos.fichatecnica.MaterialFichaTecnicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductoResourceFichaTecnicaTest {

    @Mock private ProductoService productoService;
    @Mock private MaterialFichaTecnicaService fichaTecnicaService;
    @Mock private UserRepository userRepository;

    private MockMvc mockMvc;
    private ProductoResource resource;

    @BeforeEach
    void setUp() {
        resource = new ProductoResource(
                productoService,
                fichaTecnicaService,
                userRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
    }

    @Test
    void returnsMetadataForAuthorizedProductsReader() throws Exception {
        Authentication authentication = authentication("reader");
        when(userRepository.findByUsername("reader")).thenReturn(Optional.of(productsReader("reader", 1)));
        when(fichaTecnicaService.getMetadata("M-1")).thenReturn(
                new MaterialFichaTecnicaService.FichaTecnicaMetadata(
                        true,
                        versionResponse(1L, 1, true),
                        1
                )
        );

        mockMvc.perform(get("/productos/M-1/ficha-tecnica/metadata").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.versionVigente.version").value(1))
                .andExpect(jsonPath("$.totalVersiones").value(1));
    }

    @Test
    void returnsNotFoundForUnknownOrNonMaterialProduct() throws Exception {
        Authentication authentication = authentication("master");
        when(userRepository.findByUsername("master"))
                .thenReturn(Optional.of(User.builder().username("master").build()));
        when(fichaTecnicaService.getMetadata("T-1")).thenThrow(new NoSuchElementException());

        mockMvc.perform(get("/productos/T-1/ficha-tecnica/metadata").principal(authentication))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamsPdfWithSafeHeaders() throws Exception {
        Authentication authentication = authentication("master");
        byte[] pdf = "%PDF-test".getBytes();
        when(userRepository.findByUsername("master"))
                .thenReturn(Optional.of(User.builder().username("master").build()));
        when(fichaTecnicaService.load("M-1")).thenReturn(
                new MaterialFichaTecnicaService.TechnicalSheetDownload(
                        new ByteArrayResource(pdf),
                        pdf.length,
                        1
                )
        );

        mockMvc.perform(get("/productos/M-1/ficha-tecnica").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().longValue("Content-Length", pdf.length))
                .andExpect(header().string("Content-Disposition", containsString("ficha-tecnica-M-1.pdf")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void rejectsUserWithoutProductsAccess() throws Exception {
        Authentication authentication = authentication("blocked");
        when(userRepository.findByUsername("blocked"))
                .thenReturn(Optional.of(User.builder().username("blocked").build()));

        mockMvc.perform(get("/productos/M-1/ficha-tecnica/metadata").principal(authentication))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsVersionForProductsAdministrator() throws Exception {
        Authentication authentication = authentication("admin");
        MockMultipartFile pdf = new MockMultipartFile(
                "archivo", "ficha.pdf", "application/pdf", "%PDF-test".getBytes()
        );
        MockMultipartFile reason = new MockMultipartFile(
                "motivoCambio", "", "text/plain", "Cambio de proveedor".getBytes()
        );
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(productsReader("admin", 3)));
        when(fichaTecnicaService.crearNuevaVersion("M-1", pdf, "Cambio de proveedor", "admin"))
                .thenReturn(versionResponse(2L, 2, true));

        mockMvc.perform(multipart("/productos/M-1/ficha-tecnica/versiones")
                        .file(pdf)
                        .file(reason)
                        .principal(authentication))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void rejectsVersionCreationForLevelTwoUser() throws Exception {
        Authentication authentication = authentication("creator");
        MockMultipartFile pdf = new MockMultipartFile(
                "archivo", "ficha.pdf", "application/pdf", "%PDF-test".getBytes()
        );
        when(userRepository.findByUsername("creator"))
                .thenReturn(Optional.of(productsReader("creator", 2)));

        mockMvc.perform(multipart("/productos/M-1/ficha-tecnica/versiones")
                        .file(pdf)
                        .principal(authentication))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsLevelTwoUserToAttachInitialSheetDuringMaterialCreation() {
        Authentication authentication = authentication("creator");
        Material material = new Material();
        material.setProductoId("M-1");
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "ficha.pdf", "application/pdf", "%PDF-test".getBytes()
        );
        when(userRepository.findByUsername("creator"))
                .thenReturn(Optional.of(productsReader("creator", 2)));
        when(productoService.saveMateriaPrimaV2(material, pdf, "creator")).thenReturn(material);

        var response = resource.saveMateriaPrimaV2(authentication, material, pdf);

        assertEquals(201, response.getStatusCode().value());
        verify(productoService).saveMateriaPrimaV2(material, pdf, "creator");
    }

    @Test
    void streamsHistoricalVersionWithCanonicalFilename() throws Exception {
        Authentication authentication = authentication("reader");
        byte[] pdf = "%PDF-history".getBytes();
        when(userRepository.findByUsername("reader"))
                .thenReturn(Optional.of(productsReader("reader", 1)));
        when(fichaTecnicaService.loadVersion("M-1", 20L)).thenReturn(
                new MaterialFichaTecnicaService.TechnicalSheetDownload(
                        new ByteArrayResource(pdf), pdf.length, 2
                )
        );

        mockMvc.perform(get("/productos/M-1/ficha-tecnica/versiones/20/archivo")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf))
                .andExpect(header().string(
                        "Content-Disposition", containsString("ficha-tecnica-M-1-v2.pdf")
                ));
    }

    private static Authentication authentication(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }

    private static User productsReader(String username, int level) {
        TabAcceso tabAccess = TabAcceso.builder().tabId("MAIN").nivel(level).build();
        ModuloAcceso moduleAccess = ModuloAcceso.builder()
                .modulo(ModuloSistema.PRODUCTOS)
                .tabs(new HashSet<>(Set.of(tabAccess)))
                .build();
        return User.builder()
                .username(username)
                .moduloAccesos(new HashSet<>(Set.of(moduleAccess)))
                .build();
    }

    private static MaterialFichaTecnicaVersionResponse versionResponse(
            long id,
            int version,
            boolean available
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 10, 0);
        return new MaterialFichaTecnicaVersionResponse(
                id,
                version,
                MaterialFichaTecnicaVersion.Estado.VIGENTE,
                "ficha.pdf",
                100L,
                now,
                null,
                now,
                "admin",
                version == 1 ? "Carga inicial" : "Actualización",
                available
        );
    }
}
