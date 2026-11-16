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
    private final Duration refreshInterval;
    private final String cookieName;
    private final boolean secureCookie;

    public GuestSessionService(
            DatabaseClient databaseClient,
            TransactionalOperator transactionalOperator,
            @Value("${aeolens.history.retention:90d}") Duration retention,
            @Value("${aeolens.guest.refresh-interval:1d}") Duration refreshInterval,
            @Value("${aeolens.guest.cookie-name:aeolens_guest}") String cookieName,
            @Value("${aeolens.guest.secure-cookie:false}") boolean secureCookie
    ) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.retention = retention;
        this.refreshInterval = refreshInterval;
        this.cookieName = cookieName;
        this.secureCookie = secureCookie;
    }

    public Mono<GuestIdentity> resolve(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
        if (cookie == null || cookie.getValue().isBlank()) {
            return createGuest(exchange);
        }

        String token = cookie.getValue();
        String tokenHash = hash(token);
        return databaseClient.sql("""
                        SELECT guest_id, last_seen_at
                        FROM guest_sessions
                        WHERE token_hash = :tokenHash
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("tokenHash", tokenHash)
                .map((row, metadata) -> new ActiveSession(
                        new GuestIdentity(row.get("guest_id", UUID.class)),
                        row.get("last_seen_at", OffsetDateTime.class)))
                .one()
                .flatMap(session -> refreshIfStale(exchange, session, token, tokenHash))
                .switchIfEmpty(Mono.defer(() -> createGuest(exchange)));
    }

    /**
     * Rolls the retention window forward. The database expiry and the cookie Max-Age are
     * always extended together, and at most once per refresh interval to avoid a write on
     * every request.
     */
    private Mono<GuestIdentity> refreshIfStale(
            ServerWebExchange exchange, ActiveSession session, String token, String tokenHash) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (session.lastSeenAt() != null && session.lastSeenAt().isAfter(now.minus(refreshInterval))) {
            return Mono.just(session.identity());
        }
        return touch(session.identity(), tokenHash, now)
                .then(Mono.fromRunnable(() -> issueCookie(exchange, token)))
                .thenReturn(session.identity());
    }

    private Mono<Void> touch(GuestIdentity identity, String tokenHash, OffsetDateTime now) {
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
                .doOnSuccess(identity -> issueCookie(exchange, token));
    }

    private void issueCookie(ServerWebExchange exchange, String token) {
        exchange.getResponse().addCookie(ResponseCookie
                .from(cookieName, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(retention)
                .build());
    }

    private record ActiveSession(GuestIdentity identity, OffsetDateTime lastSeenAt) {
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
