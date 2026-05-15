package com.kazikonnect.backend.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        String requestPath = request.getRequestURI();

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                if (tokenProvider.validateToken(token)) {
                    String username = tokenProvider.getUsernameFromToken(token);
                    String role = tokenProvider.getRoleFromToken(token);

                    if (role != null) {
                        // Use PascalCase roles as authorities (e.g., Worker, Client, Admin)
                        // This matches the @PreAuthorize("hasAuthority('Worker')") pattern
                        String roleUpper = role.toUpperCase();
                        String pascalRole = roleUpper.substring(0, 1).toUpperCase() + roleUpper.substring(1).toLowerCase();
                        
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(pascalRole);
                        
                        System.out.println("✓ [JWT] Auth successful: " + username + " [" + authority.getAuthority() + "]");

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                username, null, java.util.Collections.singletonList(authority)
                        );
                        
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } else {
                    System.out.println("✗ [JWT] Invalid/Expired token for: " + requestPath + " (Validation failed)");
                }
            } catch (Exception e) {
                System.out.println("✗ [JWT] Auth error for " + requestPath + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        }

        filterChain.doFilter(request, response);
    }
}
