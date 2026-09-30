package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.AccountDto;
import io.cvvexxx.matchx.entity.account.Account;
import io.cvvexxx.matchx.exception.InsufficientFundsException;
import io.cvvexxx.matchx.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String BTC = "BTC";
    private static final String USDT = "USDT";

    private static final UUID LOW_ID = new UUID(0L, 1L);
    private static final UUID HIGH_ID = new UUID(0L, 2L);

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;


    private static Account account(String asset, String available, String locked) {
        return Account.builder()
                .id(UUID.randomUUID())
                .asset(asset)
                .available(new BigDecimal(available))
                .locked(new BigDecimal(locked))
                .build();
    }

    private void givenAccount(UUID userId, Account account) {
        when(accountRepository.findByUserIdAndAssetForUpdate(userId, account.getAsset()))
                .thenReturn(Optional.of(account));
    }


    @Nested
    @DisplayName("lock")
    class Lock {

        @Test
        @DisplayName("переносит сумму из available в locked")
        void movesAmountFromAvailableToLocked() {
            Account acc = account(USDT, "100", "10");
            givenAccount(LOW_ID, acc);

            accountService.lock(LOW_ID, USDT, new BigDecimal("40"));

            assertThat(acc.getAvailable()).isEqualByComparingTo("60");
            assertThat(acc.getLocked()).isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("позволяет заблокировать весь доступный баланс")
        void allowsLockingEntireAvailableBalance() {
            Account acc = account(USDT, "100", "0");
            givenAccount(LOW_ID, acc);

            accountService.lock(LOW_ID, USDT, new BigDecimal("100"));

            assertThat(acc.getAvailable()).isEqualByComparingTo("0");
            assertThat(acc.getLocked()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("бросает InsufficientFundsException, если available меньше суммы")
        void throwsWhenAvailableIsInsufficient() {
            Account acc = account(USDT, "10", "5");
            givenAccount(LOW_ID, acc);

            assertThatThrownBy(() -> accountService.lock(LOW_ID, USDT, new BigDecimal("10.01")))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient available balance");

            assertThat(acc.getAvailable()).isEqualByComparingTo("10");
            assertThat(acc.getLocked()).isEqualByComparingTo("5");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "-1", "-0.0001"})
        @DisplayName("бросает IllegalArgumentException для null, нуля и отрицательной суммы")
        void throwsForInvalidAmount(String rawAmount) {
            BigDecimal amount = rawAmount == null ? null : new BigDecimal(rawAmount);

            assertThatThrownBy(() -> accountService.lock(LOW_ID, USDT, amount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be greater than zero");

            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName("бросает NoSuchElementException, если счёт не найден")
        void throwsWhenAccountNotFound() {
            when(accountRepository.findByUserIdAndAssetForUpdate(LOW_ID, USDT))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.lock(LOW_ID, USDT, BigDecimal.ONE))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(USDT);
        }
    }


    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("переносит сумму из locked в available")
        void movesAmountFromLockedToAvailable() {
            Account acc = account(USDT, "10", "100");
            givenAccount(LOW_ID, acc);

            accountService.unlock(LOW_ID, USDT, new BigDecimal("30"));

            assertThat(acc.getAvailable()).isEqualByComparingTo("40");
            assertThat(acc.getLocked()).isEqualByComparingTo("70");
        }

        @Test
        @DisplayName("позволяет разблокировать весь locked баланс")
        void allowsUnlockingEntireLockedBalance() {
            Account acc = account(USDT, "0", "100");
            givenAccount(LOW_ID, acc);

            accountService.unlock(LOW_ID, USDT, new BigDecimal("100"));

            assertThat(acc.getAvailable()).isEqualByComparingTo("100");
            assertThat(acc.getLocked()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("бросает InsufficientFundsException, если locked меньше суммы")
        void throwsWhenLockedIsInsufficient() {
            Account acc = account(USDT, "50", "10");
            givenAccount(LOW_ID, acc);

            assertThatThrownBy(() -> accountService.unlock(LOW_ID, USDT, new BigDecimal("11")))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient locked balance");

            assertThat(acc.getAvailable()).isEqualByComparingTo("50");
            assertThat(acc.getLocked()).isEqualByComparingTo("10");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "-5"})
        @DisplayName("бросает IllegalArgumentException для невалидной суммы")
        void throwsForInvalidAmount(String rawAmount) {
            BigDecimal amount = rawAmount == null ? null : new BigDecimal(rawAmount);

            assertThatThrownBy(() -> accountService.unlock(LOW_ID, USDT, amount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be greater than zero");

            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName("бросает NoSuchElementException, если счёт не найден")
        void throwsWhenAccountNotFound() {
            when(accountRepository.findByUserIdAndAssetForUpdate(LOW_ID, USDT))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.unlock(LOW_ID, USDT, BigDecimal.ONE))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }


    @Nested
    @DisplayName("settleTrade")
    class SettleTrade {

        private final BigDecimal price = new BigDecimal("100");
        private final BigDecimal amount = new BigDecimal("2");

        private Account buyerQuote;
        private Account buyerBase;
        private Account sellerBase;
        private Account sellerQuote;

        private void prepareAccounts(UUID buyerId, UUID sellerId) {
            buyerQuote = account(USDT, "0", "1000");
            buyerBase = account(BTC, "0", "0");
            sellerBase = account(BTC, "0", "5");
            sellerQuote = account(USDT, "0", "0");

            givenAccount(buyerId, buyerQuote);
            givenAccount(buyerId, buyerBase);
            givenAccount(sellerId, sellerBase);
            givenAccount(sellerId, sellerQuote);
        }

        @Test
        @DisplayName("корректно перераспределяет средства между покупателем и продавцом")
        void settlesTradeCorrectly() {
            prepareAccounts(LOW_ID, HIGH_ID);

            accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, price, amount);

            assertThat(buyerQuote.getLocked()).isEqualByComparingTo("800");
            assertThat(buyerBase.getAvailable()).isEqualByComparingTo("2");
            assertThat(sellerBase.getLocked()).isEqualByComparingTo("3");
            assertThat(sellerQuote.getAvailable()).isEqualByComparingTo("200");
        }

        @Test
        @DisplayName("если buyerId < sellerId, сначала блокируются счета покупателя")
        void locksBuyerFirstWhenBuyerIdIsLower() {
            prepareAccounts(LOW_ID, HIGH_ID);

            accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, price, amount);

            InOrder order = inOrder(accountRepository);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(LOW_ID, USDT);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(LOW_ID, BTC);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(HIGH_ID, BTC);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(HIGH_ID, USDT);
        }

        @Test
        @DisplayName("если buyerId > sellerId, сначала блокируются счета продавца")
        void locksSellerFirstWhenBuyerIdIsHigher() {
            prepareAccounts(HIGH_ID, LOW_ID);

            accountService.settleTrade(HIGH_ID, LOW_ID, BTC, USDT, price, amount);

            InOrder order = inOrder(accountRepository);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(LOW_ID, BTC);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(LOW_ID, USDT);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(HIGH_ID, USDT);
            order.verify(accountRepository).findByUserIdAndAssetForUpdate(HIGH_ID, BTC);
        }

        @Test
        @DisplayName("результат не зависит от порядка блокировки (buyerId > sellerId)")
        void settlesCorrectlyWhenBuyerIdIsHigher() {
            prepareAccounts(HIGH_ID, LOW_ID);

            accountService.settleTrade(HIGH_ID, LOW_ID, BTC, USDT, price, amount);

            assertThat(buyerQuote.getLocked()).isEqualByComparingTo("800");
            assertThat(buyerBase.getAvailable()).isEqualByComparingTo("2");
            assertThat(sellerBase.getLocked()).isEqualByComparingTo("3");
            assertThat(sellerQuote.getAvailable()).isEqualByComparingTo("200");
        }

        @Test
        @DisplayName("учитывает дробные значения цены и количества")
        void handlesDecimalValues() {
            prepareAccounts(LOW_ID, HIGH_ID);
            buyerQuote.setLocked(new BigDecimal("20000"));

            accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT,
                    new BigDecimal("30000.25"), new BigDecimal("0.5"));

            assertThat(buyerQuote.getLocked()).isEqualByComparingTo("4999.875");
            assertThat(buyerBase.getAvailable()).isEqualByComparingTo("0.5");
            assertThat(sellerBase.getLocked()).isEqualByComparingTo("4.5");
            assertThat(sellerQuote.getAvailable()).isEqualByComparingTo("15000.125");
        }

        @Test
        @DisplayName("бросает InsufficientFundsException, если у покупателя мало locked")
        void throwsWhenBuyerLockedIsInsufficient() {
            prepareAccounts(LOW_ID, HIGH_ID);
            buyerQuote.setLocked(new BigDecimal("199.99"));

            assertThatThrownBy(() -> accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, price, amount))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Buyer has insufficient locked " + USDT);

            assertThat(buyerQuote.getLocked()).isEqualByComparingTo("199.99");
            assertThat(buyerBase.getAvailable()).isEqualByComparingTo("0");
            assertThat(sellerBase.getLocked()).isEqualByComparingTo("5");
            assertThat(sellerQuote.getAvailable()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("бросает InsufficientFundsException, если у продавца мало locked")
        void throwsWhenSellerLockedIsInsufficient() {
            prepareAccounts(LOW_ID, HIGH_ID);
            sellerBase.setLocked(new BigDecimal("1.99"));

            assertThatThrownBy(() -> accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, price, amount))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Seller has insufficient locked " + BTC);

            assertThat(buyerQuote.getLocked()).isEqualByComparingTo("1000");
            assertThat(buyerBase.getAvailable()).isEqualByComparingTo("0");
            assertThat(sellerBase.getLocked()).isEqualByComparingTo("1.99");
            assertThat(sellerQuote.getAvailable()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("сделка на весь locked баланс обеих сторон обнуляет locked")
        void settlesEntireLockedBalances() {
            prepareAccounts(LOW_ID, HIGH_ID);
            buyerQuote.setLocked(new BigDecimal("200"));
            sellerBase.setLocked(new BigDecimal("2"));

            accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, price, amount);

            assertThat(buyerQuote.getLocked()).isEqualByComparingTo("0");
            assertThat(sellerBase.getLocked()).isEqualByComparingTo("0");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "-1"})
        @DisplayName("бросает IllegalArgumentException для невалидной цены")
        void throwsForInvalidPrice(String rawPrice) {
            BigDecimal invalidPrice = rawPrice == null ? null : new BigDecimal(rawPrice);

            assertThatThrownBy(() -> accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, invalidPrice, amount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Price and amount must be greater than zero");

            verifyNoInteractions(accountRepository);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "-1"})
        @DisplayName("бросает IllegalArgumentException для невалидного количества")
        void throwsForInvalidAmount(String rawAmount) {
            BigDecimal invalidAmount = rawAmount == null ? null : new BigDecimal(rawAmount);

            assertThatThrownBy(() -> accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, price, invalidAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Price and amount must be greater than zero");

            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName("бросает NoSuchElementException, если один из счетов не найден")
        void throwsWhenAccountNotFound() {
            when(accountRepository.findByUserIdAndAssetForUpdate(any(UUID.class), anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.settleTrade(LOW_ID, HIGH_ID, BTC, USDT, price, amount))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("getPortfolio")
    class GetPortfolio {

        @Test
        @DisplayName("маппит счета пользователя в AccountDto")
        void mapsAccountsToDtos() {
            Account btc = account(BTC, "1.5", "0.5");
            Account usdt = account(USDT, "1000", "250");
            when(accountRepository.findAllByUserId(LOW_ID)).thenReturn(Optional.of(List.of(btc, usdt)));

            List<AccountDto> result = accountService.getPortfolio(LOW_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).usingRecursiveComparison()
                    .isEqualTo(new AccountDto(btc.getId(), BTC, btc.getAvailable(), btc.getLocked()));
            assertThat(result.get(1)).usingRecursiveComparison()
                    .isEqualTo(new AccountDto(usdt.getId(), USDT, usdt.getAvailable(), usdt.getLocked()));
        }

        @Test
        @DisplayName("возвращает пустой список, если у пользователя нет счетов")
        void returnsEmptyListWhenNoAccounts() {
            when(accountRepository.findAllByUserId(LOW_ID)).thenReturn(Optional.of(List.of()));

            assertThat(accountService.getPortfolio(LOW_ID)).isEmpty();
        }

        @Test
        @DisplayName("бросает NoSuchElementException, если репозиторий вернул пустой Optional")
        void throwsWhenOptionalIsEmpty() {
            when(accountRepository.findAllByUserId(LOW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getPortfolio(LOW_ID))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(LOW_ID.toString());
        }
    }
}