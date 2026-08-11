package com.clinic.doc_appointment.security;

import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP {@code CONNECT} frame with the same access token the REST filter uses.
 *
 * <p>Note that a STOMP session authenticates once and then survives its access token's expiry —
 * nothing re-validates per message. Shortening the access-token lifetime therefore does not drop
 * live sockets; it only means <em>reconnects</em> present a stale token far more often.
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    /** Description carried by the STOMP ERROR frame when the token has expired. */
    public static final String ERROR_TOKEN_EXPIRED = "TOKEN_EXPIRED";

    /** Description carried by the STOMP ERROR frame for any other authentication failure. */
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final boolean rejectUnauthenticatedConnect;

    public WebSocketAuthInterceptor(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            @Value("${websocket.reject-unauthenticated-connect:false}") boolean rejectUnauthenticatedConnect) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.rejectUnauthenticatedConnect = rejectUnauthenticatedConnect;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                try {
                    String email = jwtService.extractEmail(jwt);

                    if (email != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        if (jwtService.isTokenValid(jwt, userDetails)) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails, null, userDetails.getAuthorities());
                            accessor.setUser(authentication);
                            log.info("WebSocket Authenticated successfully for user: {}", email);
                            return message;
                        }
                    }
                    rejectOrWarn(message, ERROR_UNAUTHORIZED, "token not valid for this user");
                } catch (ExpiredJwtException e) {
                    // Distinguished from other failures so the client knows to refresh and
                    // reconnect rather than retry forever with the same dead token.
                    rejectOrWarn(message, ERROR_TOKEN_EXPIRED, "access token expired");
                } catch (MessageDeliveryException e) {
                    throw e;
                } catch (Exception e) {
                    rejectOrWarn(message, ERROR_UNAUTHORIZED, e.getMessage());
                }
            } else {
                rejectOrWarn(message, ERROR_UNAUTHORIZED, "no Bearer token");
            }
        }
        return message;
    }

    /**
     * Historically a failed CONNECT still established an unauthenticated session that silently
     * received nothing. Rejecting instead gives the client a STOMP ERROR frame it can act on —
     * but that changes the reconnect contract, so it stays behind a flag until the client
     * handles the frame. Turning it on before then converts a silent failure into a
     * reconnect storm.
     */
    private void rejectOrWarn(Message<?> message, String errorCode, String detail) {
        if (rejectUnauthenticatedConnect) {
            log.warn("Rejecting WebSocket CONNECT [{}]: {}", errorCode, detail);
            throw new MessageDeliveryException(message, errorCode);
        }
        log.warn("WebSocket JWT authentication failed [{}]: {} "
                + "(session established unauthenticated; enable websocket.reject-unauthenticated-connect "
                + "once clients handle the ERROR frame)", errorCode, detail);
    }
}
