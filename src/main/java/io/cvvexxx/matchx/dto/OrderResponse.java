package io.cvvexxx.matchx.dto;

import io.cvvexxx.matchx.entity.order.Order;
import io.cvvexxx.matchx.entity.order.OrderSide;
import io.cvvexxx.matchx.entity.order.OrderStatus;
import io.cvvexxx.matchx.entity.order.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        String symbol,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getSymbol(),
                order.getSide(),
                order.getType(),
                order.getPrice(),
                order.getQuantity(),
                order.getFilledQuantity(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
