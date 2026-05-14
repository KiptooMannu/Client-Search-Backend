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
import java.util.Collections;

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
                        String formattedRole = role.substring(0, 1).toUpperCase() + role.substring(1).toLowerCase();
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(formattedRole);
                        
                        System.out.println("✓ [JWT] Auth successful: " + username + " [" + authority.getAuthority() + "]");

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                username, null, Collections.singletonList(authority)
                        );
                        
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } else {
                    System.out.println("✗ [JWT] Invalid/Expired token for: " + requestPath);
                }
            } catch (Exception e) {
                System.out.println("✗ [JWT] Auth error: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
