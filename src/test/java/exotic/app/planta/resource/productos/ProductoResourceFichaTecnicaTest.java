package exotic.app.planta.resource.productos;

import exotic.app.planta.model.users.ModuloAcceso;
import exotic.app.planta.model.users.ModuloSistema;
import exotic.app.planta.model.users.TabAcceso;
import exotic.app.planta.model.users.User;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.productos.ProductoService;
import exotic.app.planta.service.productos.fichatecnica.MaterialFichaTecnicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach
    void setUp() {
        ProductoResource resource = new ProductoResource(
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
        when(fichaTecnicaService.isAvailable("M-1")).thenReturn(true);

        mockMvc.perform(get("/productos/M-1/ficha-tecnica/metadata").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true));
    }

    @Test
    void returnsNotFoundForUnknownOrNonMaterialProduct() throws Exception {
        Authentication authentication = authentication("master");
        when(userRepository.findByUsername("master"))
                .thenReturn(Optional.of(User.builder().username("master").build()));
        when(fichaTecnicaService.isAvailable("T-1")).thenThrow(new NoSuchElementException());

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
                        pdf.length
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
}
