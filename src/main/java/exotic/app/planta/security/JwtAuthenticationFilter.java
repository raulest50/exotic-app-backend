package exotic.app.planta.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String jwt = getJwtFromRequest(request);
        if (!StringUtils.hasText(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = tokenProvider.parseClaims(jwt);
            Authentication authentication = tokenProvider.getAuthentication(jwt, claims);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            log.info("Expired JWT presented for path {}", request.getRequestURI());
            rejectRequest(request, response, RestAuthenticationEntryPoint.TOKEN_EXPIRED, ex);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT presented for path {}: {}", request.getRequestURI(), ex.getMessage());
            rejectRequest(request, response, RestAuthenticationEntryPoint.INVALID_TOKEN, ex);
        }
    }

    private void rejectRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            String code,
            RuntimeException cause
    ) throws IOException {
        SecurityContextHolder.clearContext();
        request.setAttribute(RestAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, code);
        authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException("JWT authentication failed", cause)
        );
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
