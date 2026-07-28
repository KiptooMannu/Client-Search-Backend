package com.kazikonnect.backend.core.config;

import com.kazikonnect.backend.core.security.JwtTokenProvider;
import com.kazikonnect.backend.features.auth.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import java.util.Collections;
import java.util.List;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtChannelInterceptor(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    String role = jwtTokenProvider.getRoleFromToken(token);
                    List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
                    // The principal NAME is what Spring matches against when routing
                    // convertAndSendToUser(...). Every sender in this codebase addresses
                    // users by their UUID, so the principal must be the UUID and not the
                    // JWT subject (username) — otherwise no /user/** push ever resolves
                    // to a session and every real-time message is silently dropped.
                    String principalName = userRepository.findByUsername(username)
                            .map(user -> user.getId().toString())
                            .orElse(username);
                    Authentication auth = new UsernamePasswordAuthenticationToken(principalName, null, authorities);
                    accessor.setUser(auth);
                }
            }
        }
        return message;
    }
}
