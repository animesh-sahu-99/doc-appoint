package com.clinic.doc_appointment.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Skip if no Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String email = jwtService.extractEmail(jwt);

            // Only authenticate if not already authenticated
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    // Signature and expiry are fine, but the token is not usable as an access
                    // credential here — wrong owner, or a non-access token type.
                    request.setAttribute(JwtAuthEntryPoint.JWT_ERROR_ATTR, JwtAuthEntryPoint.ERROR_INVALID);
                }
            }
        } catch (ExpiredJwtException e) {
            // Recoverable: the client should refresh rather than log the user out. The entry
            // point turns this into the X-Token-Expired hint.
            request.setAttribute(JwtAuthEntryPoint.JWT_ERROR_ATTR, JwtAuthEntryPoint.ERROR_EXPIRED);
            log.debug("Expired access token for request to {}", request.getRequestURI());
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            request.setAttribute(JwtAuthEntryPoint.JWT_ERROR_ATTR, JwtAuthEntryPoint.ERROR_INVALID);
            log.warn("JWT validation failed for request to {}: {}", request.getRequestURI(), e.getMessage());
        }

        // Always continue the chain, even after a failure: public endpoints (/api/auth/**,
        // swagger) must still be served when a request happens to carry a stale token.
        filterChain.doFilter(request, response);
    }
}
