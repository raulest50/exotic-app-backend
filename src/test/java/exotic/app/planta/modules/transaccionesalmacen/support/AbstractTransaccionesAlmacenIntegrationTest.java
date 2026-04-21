package exotic.app.planta.modules.transaccionesalmacen.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exotic.app.planta.repo.usuarios.UserRepository;
import exotic.app.planta.service.commons.DatabasePurgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Tag("transacciones-almacen-local")
public abstract class AbstractTransaccionesAlmacenIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transacciones_almacen_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DatabasePurgeService databasePurgeService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TransaccionesAlmacenFixtureFactory fixtureFactory;

    private String masterToken;

    @BeforeEach
    void resetDatabase() {
        databasePurgeService.purgeDatabaseKeepingMasterLikeAccess();
        masterToken = null;
    }

    protected RequestPostProcessor bearerToken() throws Exception {
        String token = loginAsMaster();
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    protected String loginAsMaster() throws Exception {
        if (masterToken != null) {
            return masterToken;
        }

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "master",
                                  "password": "m1243"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        masterToken = response.path("token").asText();
        assertThat(masterToken).isNotBlank();
        return masterToken;
    }

    protected long masterUserId() {
        return userRepository.findByUsername("master")
                .orElseThrow(() -> new IllegalStateException("master user must exist"))
                .getId();
    }

    protected String readJsonResource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    protected MockMultipartFile classpathFile(String path, String contentType) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new MockMultipartFile(
                    "file",
                    resource.getFilename(),
                    contentType,
                    inputStream
            );
        }
    }

    protected MockMultipartFile jsonPart(String partName, Object payload) throws IOException {
        return new MockMultipartFile(
                partName,
                partName + ".json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(payload)
        );
    }
}
