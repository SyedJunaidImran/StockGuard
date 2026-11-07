package com.stockguard.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.order.dto.OrderCreateRequest;
import com.stockguard.order.dto.OrderItemRequest;
import com.stockguard.order.dto.OrderResponse;
import com.stockguard.order.exception.IdempotencyConflictException;
import com.stockguard.order.exception.IdempotencyPayloadMismatchException;
import com.stockguard.order.model.IdempotencyRecord;
import com.stockguard.order.model.IdempotencyStatus;
import com.stockguard.order.model.Order;
import com.stockguard.order.repository.IdempotencyRecordRepository;
import com.stockguard.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Service managing idempotency-key reservation, payload validation,
 * replay caching, and concurrency safety.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int KEY_MAX_LENGTH = 255;
    private static final long DEFAULT_EXPIRATION_HOURS = 24;
    private static final long IN_FLIGHT_TIMEOUT_MINUTES = 2;

    private final IdempotencyRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository recordRepository, ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates idempotency key format.
     */
    public void validateKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Idempotency-Key header cannot be blank");
        }
        if (trimmed.length() > KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key header length cannot exceed " + KEY_MAX_LENGTH + " characters");
        }
    }

    /**
     * Computes a deterministic SHA-256 hex hash from the order request.
     * Sorts items by product ID to produce a stable fingerprint across payloads.
     */
    public String computeRequestHash(OrderCreateRequest request) {
        if (request == null || request.getItems() == null) {
            return "";
        }
        List<OrderItemRequest> sorted = request.getItems().stream()
                .sorted(Comparator.comparing(OrderItemRequest::getProductId))
                .toList();

        StringBuilder sb = new StringBuilder();
        for (OrderItemRequest it : sorted) {
            sb.append(it.getProductId()).append(':').append(it.getQuantity()).append(';');
        }
        if (request.getNotes() != null && !request.getNotes().trim().isEmpty()) {
            sb.append('|').append(request.getNotes().trim());
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Attempts to reserve an idempotency key under a dedicated transaction.
     * If already completed, returns the cached OrderResponse.
     * If an in-flight request is already processing, throws IdempotencyConflictException (409 Conflict).
     * If previously failed, resets status to PROCESSING to allow retry.
     *
     * @param idempotencyKey Validated idempotency key
     * @param customer       Authenticated customer
     * @param requestHash    SHA-256 fingerprint of request
     * @return Optional containing cached OrderResponse if completed; empty if newly reserved
     */
    @Transactional
    public Optional<OrderResponse> reserveKey(String idempotencyKey, User customer, String requestHash) {
        Optional<IdempotencyRecord> existingOpt = recordRepository.findByIdempotencyKeyWithLock(idempotencyKey);

        if (existingOpt.isPresent()) {
            return handleExistingRecord(existingOpt.get(), customer, requestHash);
        }

        IdempotencyRecord newRecord = new IdempotencyRecord(
                idempotencyKey,
                customer,
                requestHash,
                IdempotencyStatus.PROCESSING,
                Instant.now().plus(DEFAULT_EXPIRATION_HOURS, ChronoUnit.HOURS)
        );

        try {
            recordRepository.saveAndFlush(newRecord);
            log.info("Reserved idempotency key [{}] for customer [{}] with status PROCESSING",
                    idempotencyKey, customer.getEmail());
            return Optional.empty();
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race collision: another thread inserted the same key in-flight
            log.warn("Concurrent insert collision for idempotency key [{}], rejecting with 409 Conflict", idempotencyKey);
            throw new IdempotencyConflictException(
                    "An order request with idempotency key '" + idempotencyKey + "' is currently being processed. Please wait."
            );
        }
    }

    /**
     * Locks the active idempotency record under the caller's active transaction (SELECT ... FOR UPDATE).
     * Prevents any concurrent request or stalled recovery from taking over the key while order placement executes.
     * If a concurrent request or recovery attempt already completed the order, returns the cached OrderResponse
     * so the caller can safely abort duplicate order placement and stock deduction.
     *
     * @param idempotencyKey Validated idempotency key
     * @return Optional containing cached OrderResponse if already completed; empty if active
     */
    public Optional<OrderResponse> lockActiveRecord(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }

        Optional<IdempotencyRecord> recordOpt = recordRepository.findByIdempotencyKeyWithLock(idempotencyKey);
        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord record = recordOpt.get();
        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            log.info("Idempotency record [{}] was already COMPLETED by a concurrent or recovery transaction. Replaying cached response.", idempotencyKey);
            if (record.getResponsePayload() != null && !record.getResponsePayload().isBlank()) {
                try {
                    return Optional.of(objectMapper.readValue(record.getResponsePayload(), OrderResponse.class));
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize responsePayload for key [{}], falling back to entity", idempotencyKey, e);
                }
            }
            if (record.getOrder() != null) {
                return Optional.of(OrderResponse.fromEntity(record.getOrder()));
            }
        }

        return Optional.empty();
    }

    /**
     * Marks an idempotency record as COMPLETED, linking the persisted Order and serializing the OrderResponse.
     * Participates in the caller transaction so the order and idempotency record commit together atomically,
     * satisfying foreign key integrity constraints.
     */
    @Transactional
    public void markCompleted(String idempotencyKey, Order order, OrderResponse response) {
        if (idempotencyKey == null) return;

        recordRepository.findByIdempotencyKeyWithLock(idempotencyKey).ifPresent(record -> {
            if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getOrder() != null) {
                log.info("Idempotency record [{}] is already COMPLETED with order #{}, skipping duplicate completion",
                        idempotencyKey, record.getOrder().getOrderNumber());
                return;
            }
            record.setOrder(order);
            record.setStatus(IdempotencyStatus.COMPLETED);
            try {
                record.setResponsePayload(objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize OrderResponse for idempotency key [{}]", idempotencyKey, e);
            }
            recordRepository.save(record);
            log.info("Idempotency record [{}] marked COMPLETED with order #{}", idempotencyKey, order.getOrderNumber());
        });
    }

    /**
     * Marks an idempotency record as FAILED, allowing future retries to re-attempt the order.
     * Uses pessimistic lock to ensure it never downgrades an already-completed record under concurrent transactions.
     */
    @Transactional
    public void markFailed(String idempotencyKey) {
        if (idempotencyKey == null) return;

        recordRepository.findByIdempotencyKeyWithLock(idempotencyKey).ifPresent(record -> {
            if (record.getStatus() != IdempotencyStatus.COMPLETED) {
                record.setStatus(IdempotencyStatus.FAILED);
                recordRepository.save(record);
                log.warn("Idempotency record [{}] marked FAILED", idempotencyKey);
            } else {
                log.info("Idempotency record [{}] is already COMPLETED, skipping markFailed", idempotencyKey);
            }
        });
    }

    private Optional<OrderResponse> handleExistingRecord(IdempotencyRecord record, User customer, String requestHash) {
        // 1. Cross-customer ownership validation
        if (!record.getUser().getId().equals(customer.getId())) {
            throw new IdempotencyConflictException(
                    "Idempotency key '" + record.getIdempotencyKey() + "' is already registered to another customer account"
            );
        }

        // 2. Payload fingerprint validation
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyPayloadMismatchException(
                    "Idempotency key '" + record.getIdempotencyKey() + "' was previously used with a different order payload"
            );
        }

        // 3. Status checks
        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            log.info("Idempotent replay detected for key [{}]. Returning cached order snapshot.", record.getIdempotencyKey());
            try {
                OrderResponse cached = objectMapper.readValue(record.getResponsePayload(), OrderResponse.class);
                return Optional.of(cached);
            } catch (JsonProcessingException e) {
                log.error("Corrupted cached response payload for key [{}], re-creating response from entity", record.getIdempotencyKey(), e);
                if (record.getOrder() != null) {
                    return Optional.of(OrderResponse.fromEntity(record.getOrder()));
                }
                throw new IllegalStateException("Corrupted idempotency record without order reference", e);
            }
        }

        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            // Check if expired or stalled (e.g. server crash during order placement)
            boolean isExpired = record.getExpiresAt() != null && record.getExpiresAt().isBefore(Instant.now());
            boolean isStalled = record.getCreatedAt() != null && record.getCreatedAt().isBefore(Instant.now().minus(IN_FLIGHT_TIMEOUT_MINUTES, ChronoUnit.MINUTES));

            if (isExpired || isStalled) {
                log.warn("Found {} in-flight idempotency record [{}]. Allowing retry.",
                        isExpired ? "expired" : "stalled", record.getIdempotencyKey());
                record.setStatus(IdempotencyStatus.PROCESSING);
                record.setCreatedAt(Instant.now());
                record.setExpiresAt(Instant.now().plus(DEFAULT_EXPIRATION_HOURS, ChronoUnit.HOURS));
                recordRepository.save(record);
                return Optional.empty();
            }
            throw new IdempotencyConflictException(
                    "An order request with idempotency key '" + record.getIdempotencyKey() + "' is currently being processed. Please wait."
            );
        }

        if (record.getStatus() == IdempotencyStatus.FAILED) {
            log.info("Retrying previously failed order attempt for idempotency key [{}]. Re-activating to PROCESSING.", record.getIdempotencyKey());
            record.setStatus(IdempotencyStatus.PROCESSING);
            record.setCreatedAt(Instant.now());
            record.setExpiresAt(Instant.now().plus(DEFAULT_EXPIRATION_HOURS, ChronoUnit.HOURS));
            recordRepository.save(record);
            return Optional.empty();
        }

        return Optional.empty();
    }
}
