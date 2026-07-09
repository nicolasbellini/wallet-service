package com.recargapay.walletservice.application.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private IdempotencyService service() {
        return new IdempotencyService(repository, objectMapper, clock);
    }

    @Test
    void withoutHeaderRunsActionDirectlyAndSkipsRepository() {
        AtomicInteger calls = new AtomicInteger();
        ResponseEntity<Map> result = service().executeIdempotent(null, "/api/v1/wallet", Map.class, () -> {
            calls.incrementAndGet();
            return ResponseEntity.status(201).body(Map.of("ok", true));
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        verifyNoInteractions(repository);
    }

    @Test
    void newKeyReservesExecutesOnceAndStoresResult() {
        when(repository.findByKeyForUpdate("key-1")).thenReturn(Optional.empty());

        AtomicInteger calls = new AtomicInteger();
        ResponseEntity<Map> result = service().executeIdempotent("key-1", "/api/v1/wallet", Map.class, () -> {
            calls.incrementAndGet();
            return ResponseEntity.status(201).body(Map.of("ok", true));
        });

        assertThat(calls.get()).isEqualTo(1);
        verify(repository).reserve(eq("key-1"), eq("/api/v1/wallet"), any(Instant.class));
        verify(repository).complete(eq("key-1"), eq(201), anyString());
    }

    @Test
    void repeatedKeyReturnsCachedResponseWithoutRunningActionAgain() throws Exception {
        String cachedBody = objectMapper.writeValueAsString(Map.of("ok", true));
        IdempotencyRecord completed = new IdempotencyRecord("key-1", "/api/v1/wallet", 201, cachedBody, Instant.now(clock));
        when(repository.findByKeyForUpdate("key-1")).thenReturn(Optional.of(completed));

        AtomicInteger calls = new AtomicInteger();
        ResponseEntity<Map> result = service().executeIdempotent("key-1", "/api/v1/wallet", Map.class, () -> {
            calls.incrementAndGet();
            return ResponseEntity.status(201).body(Map.of("ok", false));
        });

        assertThat(calls.get()).isZero();
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody()).isEqualTo(Map.of("ok", true));
        verify(repository, never()).reserve(anyString(), anyString(), any());
        verify(repository, never()).complete(anyString(), anyInt(), anyString());
    }

    @Test
    void reusingKeyForDifferentPathIsRejected() {
        IdempotencyRecord completed = new IdempotencyRecord("key-1", "/api/v1/wallet/x/deposit", 201, "{}", Instant.now(clock));
        when(repository.findByKeyForUpdate("key-1")).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service().executeIdempotent("key-1", "/api/v1/wallet/y/deposit", Map.class, () -> ResponseEntity.ok(Map.of())))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void concurrentReservationRaceSurfacesAsInFlightException() {
        when(repository.findByKeyForUpdate("key-1")).thenReturn(Optional.empty());
        when(repository.reserve(eq("key-1"), anyString(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service().executeIdempotent("key-1", "/api/v1/wallet", Map.class, () -> ResponseEntity.ok(Map.of())))
                .isInstanceOf(IdempotencyKeyInFlightException.class);
    }
}
