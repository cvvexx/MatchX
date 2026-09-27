package io.cvvexxx.matchx.entity.account;

import io.cvvexxx.matchx.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_accounts_user_asset", columnNames = {"user_id", "asset"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "asset", nullable = false, length = 20)
    private String asset;

    @Column(name = "available", nullable = false, precision = 24, scale = 8)
    @Builder.Default
    private BigDecimal available = BigDecimal.ZERO;

    @Column(name = "locked", nullable = false, precision = 24, scale = 8)
    @Builder.Default
    private BigDecimal locked = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}