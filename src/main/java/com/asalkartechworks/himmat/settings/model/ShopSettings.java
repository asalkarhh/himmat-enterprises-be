package com.asalkartechworks.himmat.settings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "shop_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    @Builder.Default
    private Long id = SINGLETON_ID;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "gstin", length = 20)
    private String gstin;

    @Column(name = "state_code", length = 10)
    private String stateCode;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Builder.Default
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Asia/Kolkata";

    @Builder.Default
    @Column(name = "credit_days", nullable = false)
    private Integer creditDays = 30;

    @Builder.Default
    @Column(name = "invoice_prefix", nullable = false, length = 20)
    private String invoicePrefix = "HE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
