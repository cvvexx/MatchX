package io.cvvexxx.matchx.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        List<AccountDto> accounts,
        Instant createdAt
) {}