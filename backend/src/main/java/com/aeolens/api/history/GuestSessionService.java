package com.aeolens.api.history;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Service
public class GuestSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final Duration retention;
    private final String cookieName;
    private final boolean secureCookie;

    public GuestSessionService(
            DatabaseClient databaseClient,
            TransactionalOperator transactionalOperator,
            @Value("${aeolens.history.retention:90d}") Duration retention,
            @Value("${aeolens.guest.cookie-name:aeolens_guest}") String cookieName,
            @Value("${aeolens.guest.secure-cookie:false}") boolean secureCookie
    ) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.retention = retention;
        this.cookieName = cookieName;
        this.secureCookie = secureCookie;
    }

    public Mono<GuestIdentity> resolve(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
        if (cookie == null || cookie.getValue().isBlank()) {
            return createGuest(exchange);
        }

        String tokenHash = hash(cookie.getValue());
        return databaseClient.sql("""
                        SELECT guest_id
                        FROM guest_sessions
                        WHERE token_hash = :tokenHash
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("tokenHash", tokenHash)
                .map((row, metadata) -> new GuestIdentity(row.get("guest_id", UUID.class)))
                .one()
                .flatMap(identity -> touch(identity, tokenHash).thenReturn(identity))
                .switchIfEmpty(createGuest(exchange));
    }

    private Mono<Void> touch(GuestIdentity identity, String tokenHash) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(retention);
        return databaseClient.sql("""
                        UPDATE guest_sessions
                        SET last_seen_at = :now, expires_at = :expiresAt
                        WHERE token_hash = :tokenHash
                        """)
                .bind("now", now)
                .bind("expiresAt", expiresAt)
                .bind("tokenHash", tokenHash)
                .fetch()
                .rowsUpdated()
                .then(databaseClient.sql("""
                                UPDATE guest_users
                                SET last_seen_at = :now, expires_at = :expiresAt
                                WHERE id = :guestId
                                """)
                        .bind("now", now)
                        .bind("expiresAt", expiresAt)
                        .bind("guestId", identity.id())
                        .fetch()
                        .rowsUpdated())
                .then();
    }

    private Mono<GuestIdentity> createGuest(ServerWebExchange exchange) {
        UUID guestId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = generateToken();
        String tokenHash = hash(token);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(retention);

        Mono<GuestIdentity> creation = databaseClient.sql("""
                        INSERT INTO guest_users (id, created_at, last_seen_at, expires_at)
                        VALUES (:id, :now, :now, :expiresAt)
                        """)
                .bind("id", guestId)
                .bind("now", now)
                .bind("expiresAt", expiresAt)
                .fetch()
                .rowsUpdated()
                .then(databaseClient.sql("""
                                INSERT INTO guest_sessions
                                    (id, guest_id, token_hash, created_at, last_seen_at, expires_at)
                                VALUES (:id, :guestId, :tokenHash, :now, :now, :expiresAt)
                                """)
                        .bind("id", sessionId)
                        .bind("guestId", guestId)
                        .bind("tokenHash", tokenHash)
                        .bind("now", now)
                        .bind("expiresAt", expiresAt)
                        .fetch()
                        .rowsUpdated())
                .thenReturn(new GuestIdentity(guestId));

        return transactionalOperator.transactional(creation)
                .doOnSuccess(identity -> exchange.getResponse().addCookie(ResponseCookie
                        .from(cookieName, token)
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(retention)
                        .build()));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
