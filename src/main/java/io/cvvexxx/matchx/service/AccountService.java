package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.AccountDto;
import io.cvvexxx.matchx.entity.account.Account;
import io.cvvexxx.matchx.exception.InsufficientFundsException;
import io.cvvexxx.matchx.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    private record AccountKey(UUID userId, String asset) implements Comparable<AccountKey> {
        @Override
        public int compareTo(AccountKey o) {
            int userCompare = this.userId.compareTo(o.userId);
            if (userCompare != 0) {
                return userCompare;
            }
            return this.asset.compareTo(o.asset);
        }
    }

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

    @Transactional
    public void settleTrade(
            UUID buyerId,
            UUID sellerId,
            String baseAsset,
            String quoteAsset,
            BigDecimal price,
            BigDecimal amount
    ) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0 || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price and amount must be greater than zero");
        }

        BigDecimal totalQuoteAmount = price.multiply(amount);

        AccountKey buyerQuoteKey = new AccountKey(buyerId, quoteAsset);
        AccountKey buyerBaseKey = new AccountKey(buyerId, baseAsset);
        AccountKey sellerBaseKey = new AccountKey(sellerId, baseAsset);
        AccountKey sellerQuoteKey = new AccountKey(sellerId, quoteAsset);

        List<AccountKey> sortedKeys = Stream.of(buyerQuoteKey, buyerBaseKey, sellerBaseKey, sellerQuoteKey)
                .distinct()
                .sorted()
                .toList();

        Map<AccountKey, Account> accountMap = new HashMap<>();
        for (AccountKey key : sortedKeys) {
            accountMap.put(key, getAccountForUpdate(key.userId(), key.asset()));
        }

        Account buyerQuoteAccount = accountMap.get(buyerQuoteKey);
        Account buyerBaseAccount = accountMap.get(buyerBaseKey);
        Account sellerBaseAccount = accountMap.get(sellerBaseKey);
        Account sellerQuoteAccount = accountMap.get(sellerQuoteKey);

        if (buyerQuoteAccount.getLocked().compareTo(totalQuoteAmount) < 0) {
            throw new InsufficientFundsException(
                    "Buyer has insufficient locked %s: required %s, available locked %s"
                            .formatted(quoteAsset, totalQuoteAmount, buyerQuoteAccount.getLocked())
            );
        }
        if (sellerBaseAccount.getLocked().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Seller has insufficient locked %s: required %s, available locked %s"
                            .formatted(baseAsset, amount, sellerBaseAccount.getLocked())
            );
        }

        buyerQuoteAccount.setLocked(buyerQuoteAccount.getLocked().subtract(totalQuoteAmount));
        buyerBaseAccount.setAvailable(buyerBaseAccount.getAvailable().add(amount));

        sellerBaseAccount.setLocked(sellerBaseAccount.getLocked().subtract(amount));
        sellerQuoteAccount.setAvailable(sellerQuoteAccount.getAvailable().add(totalQuoteAmount));
    }

    public List<AccountDto> getPortfolio(UUID userId) {
        List<Account> accounts = accountRepository.findAllByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("account with userId %s not found".formatted(userId)));

        return accounts
                .stream()
                .map((account) -> new AccountDto(
                                account.getId(),
                                account.getAsset(),
                                account.getAvailable(),
                                account.getLocked()
                        )
                )
                .toList();
    }

    private Account checkAmountAndFindAccount(UUID userId, String asset, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        return accountRepository.findByUserIdAndAssetForUpdate(userId, asset)
                .orElseThrow(() -> new NoSuchElementException("Account for asset %s not found".formatted(asset)));
    }

    private Account getAccountForUpdate(UUID userId, String asset) {
        return accountRepository.findByUserIdAndAssetForUpdate(userId, asset)
                .orElseThrow(() -> new NoSuchElementException("Account for asset %s not found".formatted(asset)));
    }
}