package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.entity.account.Account;
import io.cvvexxx.matchx.exception.InsufficientFundsException;
import io.cvvexxx.matchx.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public void lock(UUID userId, String asset, BigDecimal amount) {
        Account account = checkAmountAndFindAccount(userId, asset, amount);

        if (account.getAvailable().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient available balance for requested amount (%s)"
                            .formatted(amount)
            );
        }

        account.setAvailable(account.getAvailable().subtract(amount));
        account.setLocked(account.getLocked().add(amount));
    }

    @Transactional
    public void unlock(UUID userId, String asset, BigDecimal amount) {
        Account account = checkAmountAndFindAccount(userId, asset, amount);

        if (account.getLocked().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient locked balance for requested amount (%s)".formatted(amount)
            );
        }

        account.setLocked(account.getLocked().subtract(amount));
        account.setAvailable(account.getAvailable().add(amount));
    }


    private Account checkAmountAndFindAccount(UUID userId, String asset, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        return accountRepository.findByUserIdAndAssetForUpdate(userId, asset)
                .orElseThrow(() -> new NoSuchElementException("Account for asset %s not found".formatted(asset)));
    }
}
