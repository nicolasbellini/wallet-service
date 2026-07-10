package com.recargapay.walletservice.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "related_wallet_id")
    private UUID relatedWalletId;

    @Column(name = "reference")
    private String reference;

    protected LedgerEntryJpaEntity() {
        // required by JPA
    }

    public LedgerEntryJpaEntity(UUID id, UUID walletId, String entryType, BigDecimal amount, BigDecimal balanceAfter,
                                 Instant occurredAt, UUID transferId, UUID relatedWalletId, String reference) {
        this.id = id;
        this.walletId = walletId;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.occurredAt = occurredAt;
        this.transferId = transferId;
        this.relatedWalletId = relatedWalletId;
        this.reference = reference;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getRelatedWalletId() {
        return relatedWalletId;
    }

    public String getReference() {
        return reference;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerEntryJpaEntity other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
