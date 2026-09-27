package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.BinanceTradeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BinanceMarketDataService extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketDataService.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final ObjectMapper objectMapper;
    private final String binanceWsUrl;

    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofPlatform().name("binance-reconnect").daemon(true).unstarted(r)
    );

    private final BlockingQueue<BinanceTradeEvent> eventQueue = new ArrayBlockingQueue<>(10_000);
    private final AtomicReference<BinanceTradeEvent> latestEvent = new AtomicReference<>();
    private final AtomicLong droppedEvents = new AtomicLong();

    private final AtomicBoolean isConnectingOrConnected = new AtomicBoolean(false);
    private final AtomicReference<WebSocketSession> currentSession = new AtomicReference<>();

    private volatile boolean running = true;

    public BinanceMarketDataService(ObjectMapper objectMapper,
                                    @Value("${binance.api.websocket}") String binanceWsUrl) {
        this.objectMapper = objectMapper;
        this.binanceWsUrl = binanceWsUrl;
    }

    @PostConstruct
    public void init() {
        connect();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        reconnectScheduler.shutdownNow();

        WebSocketSession session = currentSession.getAndSet(null);
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.warn("Ошибка при закрытии WebSocket сессии", e);
            }
        }
    }

    public void connect() {
        if (!running || !isConnectingOrConnected.compareAndSet(false, true)) {
            return;
        }

        log.info("Подключение к Binance WebSocket: {}", binanceWsUrl);
        client.execute(this, null, URI.create(binanceWsUrl))
                .whenComplete((session, error) -> {
                    if (error != null) {
                        log.error("Ошибка подключения. Реконнект через {} сек...", RECONNECT_DELAY_SECONDS, error);
                        scheduleReconnect();
                    } else {
                        currentSession.set(session);
                    }
                });
    }

    public BlockingQueue<BinanceTradeEvent> getEventQueue() {
        return eventQueue;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            BinanceTradeEvent event = objectMapper.readValue(message.getPayload(), BinanceTradeEvent.class);

            if (!eventQueue.offer(event)) {
                long dropped = droppedEvents.incrementAndGet();
                if (dropped % 1000 == 1) {
                    log.warn("Очередь событий переполнена, пропущено событий: {}", dropped);
                }
            }

            latestEvent.set(event);
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON", e);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void printLatestPriceToConsole() {
        BinanceTradeEvent event = latestEvent.get();
        if (event != null) {
            log.info("Символ: {} | Цена: {} USDT | Объём сделки: {}",
                    event.symbol(), event.price(), event.quantity());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Ошибка транспорта WebSocket", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        currentSession.set(null);
        isConnectingOrConnected.set(false);
        log.warn("Соединение закрыто: {}. Повтор через {} сек...", status, RECONNECT_DELAY_SECONDS);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        try {
            reconnectScheduler.schedule(this::connect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Планировщик остановлен, переподключение отменено");
        }
    }
}