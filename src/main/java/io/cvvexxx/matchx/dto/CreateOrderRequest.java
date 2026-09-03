package io.cvvexxx.matchx.dto;

import io.cvvexxx.matchx.entity.OrderSide;
import io.cvvexxx.matchx.entity.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID userId,
        @NotBlank String symbol,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity
) {}
