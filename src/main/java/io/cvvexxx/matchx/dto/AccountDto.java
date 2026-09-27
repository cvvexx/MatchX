package io.cvvexxx.matchx.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountDto(
        UUID id,
        String asset,
        BigDecimal available,
        BigDecimal locked
) {}