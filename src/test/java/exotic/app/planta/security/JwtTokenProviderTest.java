package exotic.app.planta.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Minimal Spring contexts only: no Spring Boot startup, properties files, database or network. */
class JwtTokenProviderTest {
    @Test void startsWithoutAConfiguredSecretAndPreservesTheTokenContract() {
        try (var context = context(new MockEnvironment())) {
            var provider = context.getBean(JwtTokenProvider.class);
            String token = tokenFor(provider, "fixture-user");

            // Verify with JJWT directly, independently of the provider's parsing methods.
            var parsed = Jwts.parserBuilder().setSigningKey(keyOf(provider))
                    .build().parseClaimsJws(token);
            var claims = parsed.getBody();

            assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS512");
            assertThat(claims.getSubject()).isEqualTo("fixture-user");
            assertThat(claims.get("accesos", String.class)).isEqualTo("FIXTURE");
            assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                    .isEqualTo(86_400_000L);
            assertThat(provider.getUsernameFromToken(token)).isEqualTo("fixture-user");
        }
    }

    @Test void issuingAnotherTokenDoesNotInvalidateEarlierTokensInTheSameContext() {
        try (var context = context(new MockEnvironment())) {
            var provider = context.getBean(JwtTokenProvider.class);
            String first = tokenFor(provider, "first-user");
            String second = tokenFor(provider, "second-user");

            assertThat(provider.getUsernameFromToken(first)).isEqualTo("first-user");
            assertThat(provider.parseClaims(second).getSubject()).isEqualTo("second-user");
            assertThat(provider.getUsernameFromToken(first)).isEqualTo("first-user");
        }
    }

    @Test void aNewSpringContextRejectsEarlierTokensAndAcceptsANewLogin() {
        String previousToken;
        try (var previous = context(new MockEnvironment())) {
            previousToken = tokenFor(previous.getBean(JwtTokenProvider.class), "fixture-user");
        }

        try (var restarted = context(new MockEnvironment())) {
            var provider = restarted.getBean(JwtTokenProvider.class);

            assertThatThrownBy(() -> provider.parseClaims(previousToken))
                    .isInstanceOf(SignatureException.class);
            assertThat(provider.getUsernameFromToken(tokenFor(provider, "fixture-user")))
                    .isEqualTo("fixture-user");
        }
    }

    @Test void independentContextsDoNotShareKeysEvenWithTheSameLegacySecretProperty() {
        // Fictional legacy configuration must not override per-context generation.
        try (var first = context(new MockEnvironment().withProperty("jwt.secret", "unused-test-fixture"));
             var second = context(new MockEnvironment().withProperty("jwt.secret", "unused-test-fixture"))) {
            var firstProvider = first.getBean(JwtTokenProvider.class);
            var secondProvider = second.getBean(JwtTokenProvider.class);
            String firstToken = tokenFor(firstProvider, "first-user");
            String secondToken = tokenFor(secondProvider, "second-user");

            assertThatThrownBy(() -> secondProvider.getUsernameFromToken(firstToken))
                    .isInstanceOf(SignatureException.class);
            assertThatThrownBy(() -> firstProvider.parseClaims(secondToken))
                    .isInstanceOf(SignatureException.class);
            assertThat(firstProvider.getUsernameFromToken(firstToken)).isEqualTo("first-user");
            assertThat(secondProvider.getUsernameFromToken(secondToken)).isEqualTo("second-user");
        }
    }

    @Test void aModifiedSubjectIsRejected() {
        try (var context = context(new MockEnvironment())) {
            var provider = context.getBean(JwtTokenProvider.class);
            String[] parts = tokenFor(provider, "fixture-user").split("\\.");
            String payload = new String(Decoders.BASE64URL.decode(parts[1]), StandardCharsets.UTF_8)
                    .replace("fixture-user", "other-user");
            String tampered = parts[0] + "."
                    + Encoders.BASE64URL.encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

            assertThatThrownBy(() -> provider.parseClaims(tampered))
                    .isInstanceOf(SignatureException.class);
        }
    }

    @Test void expiredTokensRemainRejectedEvenWhenSignedWithTheCurrentKey() {
        try (var context = context(new MockEnvironment())) {
            var provider = context.getBean(JwtTokenProvider.class);
            String expired = Jwts.builder().setSubject("fixture-user")
                    .setExpiration(Date.from(Instant.EPOCH.plusSeconds(60)))
                    .signWith(keyOf(provider), SignatureAlgorithm.HS512).compact();

            assertThatThrownBy(() -> provider.parseClaims(expired))
                    .isInstanceOf(ExpiredJwtException.class);
        }
    }

    @Test void configuredTokenLifetimeIsPreserved() {
        try (var context = context(new MockEnvironment().withProperty("jwt.expiration", "60000"))) {
            var provider = context.getBean(JwtTokenProvider.class);
            var claims = provider.parseClaims(tokenFor(provider, "fixture-user"));

            assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                    .isEqualTo(60_000L);
        }
    }

    private static AnnotationConfigApplicationContext context(MockEnvironment environment) {
        var context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        var placeholders = new PropertySourcesPlaceholderConfigurer();
        placeholders.setEnvironment(environment);
        context.addBeanFactoryPostProcessor(placeholders);
        context.registerBean(JwtTokenProvider.class);
        context.refresh();
        return context;
    }

    private static String tokenFor(JwtTokenProvider provider, String username) {
        var authorities = List.of(new SimpleGrantedAuthority("ACCESO_FIXTURE"));
        var principal = new User(username, "unused", authorities);
        return provider.generateToken(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private static SecretKey keyOf(JwtTokenProvider provider) {
        return (SecretKey) ReflectionTestUtils.getField(provider, "key");
    }
}
