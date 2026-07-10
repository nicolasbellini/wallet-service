package com.recargapay.walletservice.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_key")
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "request_path", nullable = false)
    private String requestPath;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyJpaEntity() {
        // required by JPA
    }

    public IdempotencyKeyJpaEntity(String key, String requestPath, Integer responseStatus, String responseBody, Instant createdAt) {
        this.key = key;
        this.requestPath = requestPath;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKeyJpaEntity other)) return false;
        return Objects.equals(key, other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }
}
