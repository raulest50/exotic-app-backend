package exotic.app.planta.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecurityFailureTest {

    private static final String SECRET =
            "test-secret-key-with-at-least-thirty-two-characters-for-jwt";

    private ObjectMapper objectMapper;
    private JwtTokenProvider tokenProvider;
    private RestAuthenticationEntryPoint authenticationEntryPoint;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", 60_000L);
        tokenProvider.init();

        authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
        filter = new JwtAuthenticationFilter(tokenProvider, authenticationEntryPoint);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthenticationUsesUnauthorizedContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Authentication required")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(body.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(body.path("path").asText()).isEqualTo("/api/protected");
    }

    @Test
    void expiredTokenReturnsUnauthorizedWithoutContinuingChain() throws Exception {
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", -1_000L);
        String token = tokenProvider.generateToken(authentication());

        MockHttpServletRequest request = authorizedRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("error=\"invalid_token\"");
        assertThat(body.path("code").asText()).isEqualTo("TOKEN_EXPIRED");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedTokenReturnsUnauthorizedWithoutContinuingChain() throws Exception {
        MockHttpServletRequest request = authorizedRequest("not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.path("code").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void validTokenPopulatesAuthenticationAndContinuesChain() throws Exception {
        String token = tokenProvider.generateToken(authentication());
        MockHttpServletRequest request = authorizedRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .extracting(Authentication::getName)
                .isEqualTo("security-test");
    }

    @Test
    void accessDeniedRemainsForbidden() throws Exception {
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/restricted");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Denied"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.path("code").asText()).isEqualTo("ACCESS_DENIED");
    }

    private Authentication authentication() {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ACCESO_TEST"));
        User principal = new User("security-test", "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private MockHttpServletRequest authorizedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
