package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.entity.account.Account;
import io.cvvexxx.matchx.entity.user.User;
import io.cvvexxx.matchx.exception.InsufficientFundsException;
import io.cvvexxx.matchx.repository.AccountRepository;
import io.cvvexxx.matchx.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@SpringBootTest
@Testcontainers
class AccountServiceConcurrencyIT {

    private static final String BTC = "BTC";
    private static final String USDT = "USDT";


    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("lock: нет lost update — 100 параллельных lock(1) дают ровно 100 в locked")
    void concurrentLock_noLostUpdates() throws Exception {
        UUID userId = createUserWithAccount(USDT, "1000", "0");

        List<Runnable> tasks = repeat(100, () -> accountService.lock(userId, USDT, BigDecimal.ONE));
        List<Throwable> errors = runConcurrently(tasks);

        assertThat(errors).isEmpty();
        assertBalance(userId, USDT, "900", "100");
    }

    @Test
    @DisplayName("lock: нет овербукинга — при нехватке средств проходит ровно столько запросов, сколько можно")
    void concurrentLock_neverLocksMoreThanAvailable() throws Exception {
        UUID userId = createUserWithAccount(USDT, "1000", "0");

        List<Runnable> tasks = repeat(50, () -> accountService.lock(userId, USDT, new BigDecimal("100")));
        List<Throwable> errors = runConcurrently(tasks);

        assertThat(errors).hasSize(40);
        assertThat(errors).allMatch(e -> e instanceof InsufficientFundsException);
        assertBalance(userId, USDT, "0", "1000");
    }

    @Test
    @DisplayName("lock + unlock одновременно: итоговый баланс сходится, сумма сохраняется")
    void concurrentLockAndUnlock_keepsTotalConstant() throws Exception {
        UUID userId = createUserWithAccount(USDT, "500", "500");

        List<Runnable> tasks = new ArrayList<>();
        tasks.addAll(repeat(50, () -> accountService.lock(userId, USDT, new BigDecimal("5"))));
        tasks.addAll(repeat(50, () -> accountService.unlock(userId, USDT, new BigDecimal("5"))));
        List<Throwable> errors = runConcurrently(tasks);

        assertThat(errors).isEmpty();
        assertBalance(userId, USDT, "500", "500");
    }


    @Test
    @DisplayName("settleTrade: одни и те же стороны, 30 параллельных сделок при locked хватает на 20")
    void concurrentSettleTrade_sameParties_neverExceedsLocked() throws Exception {
        UUID buyer = createUser();
        UUID seller = createUser();
        createAccount(buyer, USDT, "0", "1000");
        createAccount(buyer, BTC, "0", "0");
        createAccount(seller, BTC, "0", "20");
        createAccount(seller, USDT, "0", "0");

        List<Runnable> tasks = repeat(30, () ->
                accountService.settleTrade(buyer, seller, BTC, USDT, BigDecimal.TEN, BigDecimal.ONE));
        List<Throwable> errors = runConcurrently(tasks);

        assertThat(errors).hasSize(10);
        assertThat(errors).allMatch(e -> e instanceof InsufficientFundsException);

        assertBalance(buyer, USDT, "0", "800");
        assertBalance(buyer, BTC, "20", "0");
        assertBalance(seller, BTC, "0", "0");
        assertBalance(seller, USDT, "200", "0");
    }

    @RepeatedTest(5)
    @DisplayName("settleTrade: встречные сделки A<->B не приводят к deadlock (порядок блокировок)")
    void concurrentSettleTrade_oppositeDirections_noDeadlock() throws Exception {
        UUID a = createUser();
        UUID b = createUser();
        for (UUID user : List.of(a, b)) {
            createAccount(user, USDT, "0", "1000");
            createAccount(user, BTC, "0", "100");
        }

        List<Runnable> tasks = new ArrayList<>();
        tasks.addAll(repeat(25, () -> accountService.settleTrade(a, b, BTC, USDT, BigDecimal.TEN, BigDecimal.ONE)));
        tasks.addAll(repeat(25, () -> accountService.settleTrade(b, a, BTC, USDT, BigDecimal.TEN, BigDecimal.ONE)));

        List<Throwable> errors = runConcurrently(tasks);

        assertThat(errors).isEmpty();
        for (UUID user : List.of(a, b)) {
            assertBalance(user, USDT, "250", "750");
            assertBalance(user, BTC, "25", "75");
        }
    }

    @RepeatedTest(3)
    @DisplayName("stress: случайные lock/unlock/settleTrade — суммы по активам сохраняются, баланс не уходит в минус")
    void randomOperations_preserveInvariants() throws Exception {
        int usersCount = 4;
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < usersCount; i++) {
            UUID id = createUser();
            createAccount(id, BTC, "500", "500");
            createAccount(id, USDT, "500", "500");
            users.add(id);
        }
        BigDecimal expectedBtcTotal = new BigDecimal(usersCount * 1000);
        BigDecimal expectedUsdtTotal = new BigDecimal(usersCount * 1000);

        Random random = new Random(42);
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            UUID user = users.get(random.nextInt(usersCount));
            String asset = random.nextBoolean() ? BTC : USDT;
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(50));

            switch (random.nextInt(3)) {
                case 0 -> tasks.add(() -> accountService.lock(user, asset, amount));
                case 1 -> tasks.add(() -> accountService.unlock(user, asset, amount));
                default -> {
                    UUID seller;
                    do {
                        seller = users.get(random.nextInt(usersCount));
                    } while (seller.equals(user));
                    UUID sellerFinal = seller;
                    BigDecimal price = BigDecimal.valueOf(1 + random.nextInt(5));
                    BigDecimal tradeAmount = BigDecimal.valueOf(1 + random.nextInt(10));
                    tasks.add(() -> accountService.settleTrade(user, sellerFinal, BTC, USDT, price, tradeAmount));
                }
            }
        }

        List<Throwable> errors = runConcurrently(tasks);

        assertThat(errors).allMatch(e -> e instanceof InsufficientFundsException);

        BigDecimal btcTotal = BigDecimal.ZERO;
        BigDecimal usdtTotal = BigDecimal.ZERO;
        for (UUID user : users) {
            for (Account account : accountRepository.findAllByUserId(user).orElseThrow()) {
                assertThat(account.getAvailable()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(account.getLocked()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

                BigDecimal total = account.getAvailable().add(account.getLocked());
                if (BTC.equals(account.getAsset())) {
                    btcTotal = btcTotal.add(total);
                } else {
                    usdtTotal = usdtTotal.add(total);
                }
            }
        }
        assertThat(btcTotal).isEqualByComparingTo(expectedBtcTotal);
        assertThat(usdtTotal).isEqualByComparingTo(expectedUsdtTotal);
    }

    private List<Throwable> runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (Runnable task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        task.run();
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("все потоки должны подготовиться к старту").isTrue();
            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } catch (TimeoutException e) {
            fail("Задачи не завершились за 30 секунд — возможен deadlock");
        } finally {
            executor.shutdownNow();
        }
        return errors;
    }

    private static List<Runnable> repeat(int times, Runnable task) {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return tasks;
    }

    private UUID createUser() {
        User user = User.builder()
                .email("user-" + UUID.randomUUID() + "@test.local")
                .build();
        return userRepository.save(user).getId();
    }

    private void createAccount(UUID userId, String asset, String available, String locked) {
        User user = userRepository.getReferenceById(userId);
        accountRepository.save(Account.builder()
                .user(user)
                .asset(asset)
                .available(new BigDecimal(available))
                .locked(new BigDecimal(locked))
                .build());
    }

    private UUID createUserWithAccount(String asset, String available, String locked) {
        UUID userId = createUser();
        createAccount(userId, asset, available, locked);
        return userId;
    }

    private void assertBalance(UUID userId, String asset, String expectedAvailable, String expectedLocked) {
        Account account = accountRepository.findAllByUserId(userId).orElseThrow().stream()
                .filter(a -> a.getAsset().equals(asset))
                .findFirst()
                .orElseThrow();

        assertThat(account.getAvailable()).as("available %s", asset).isEqualByComparingTo(expectedAvailable);
        assertThat(account.getLocked()).as("locked %s", asset).isEqualByComparingTo(expectedLocked);
    }
}