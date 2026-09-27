package io.cvvexxx.matchx.entity.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_symbol_status", columnList = "symbol, status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 8)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private OrderType type;

    @Column(name = "price", precision = 24, scale = 8)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "filled_quantity", nullable = false, precision = 24, scale = 8)
    private BigDecimal filledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;


    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
