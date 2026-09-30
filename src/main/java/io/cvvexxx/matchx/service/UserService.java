package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.AccountDto;
import io.cvvexxx.matchx.dto.CreateUserRequest;
import io.cvvexxx.matchx.dto.UserDto;
import io.cvvexxx.matchx.entity.account.Account;
import io.cvvexxx.matchx.entity.user.User;
import io.cvvexxx.matchx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.math.BigDecimal;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String USDT_NAME = "USDT";
    private static final String BTC_NAME = "BTC";

    private final UserRepository userRepository;

    @Transactional
    public UserDto registerUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("User with email " + request.email() + " already exists");
        }

        User user = User.builder()
                .email(request.email())
                .build();

        Account usdtAccount = Account.builder()
                .asset(USDT_NAME)
                .available(new BigDecimal("100000.00000000"))
                .locked(BigDecimal.ZERO)
                .build();

        Account btcAccount = Account.builder()
                .asset(BTC_NAME)
                .available(BigDecimal.ZERO)
                .locked(BigDecimal.ZERO)
                .build();

        user.addAccount(usdtAccount);
        user.addAccount(btcAccount);

        User savedUser = userRepository.save(user);

        return toDto(savedUser);
    }

    private UserDto toDto(User user) {
        var accountDtos = user.getAccounts().stream()
                .map(acc -> new AccountDto(acc.getId(), acc.getAsset(), acc.getAvailable(), acc.getLocked()))
                .toList();

        return new UserDto(user.getId(), user.getEmail(), accountDtos, user.getCreatedAt());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Пользователь с таким email уже существует или произошел конфликт данных"));
    }

}
