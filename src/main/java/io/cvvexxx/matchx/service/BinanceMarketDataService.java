package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.BinanceTradeEvent;
import tools.jackson.databind.json.JsonMapper;
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

import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BinanceMarketDataService extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketDataService.class);

    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final JsonMapper jsonMapper;
    private final String binanceWsUrl;

    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofPlatform().name("binance-reconnect").daemon(true).unstarted(r)
    );

    private final BlockingQueue<BinanceTradeEvent> eventQueue = new ArrayBlockingQueue<>(10_000);

    private final AtomicReference<BinanceTradeEvent> latestEvent = new AtomicReference<>();

    private final AtomicLong droppedEvents = new AtomicLong();

    private volatile boolean running = true;

    public BinanceMarketDataService(JsonMapper jsonMapper,
                                    @Value("${binance.api.websocket}") String binanceWsUrl) {
        this.jsonMapper = jsonMapper;
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
    }

    public void connect() {
        if (!running) {
            return;
        }
        log.info("Подключение к Binance: {}", binanceWsUrl);
        client.execute(this, null, URI.create(binanceWsUrl))
                .whenComplete((session, error) -> {
                    if (error != null) {
                        log.error("Ошибка подключения. Реконнект через {} сек...", RECONNECT_DELAY_SECONDS, error);
                        scheduleReconnect();
                    }
                });
    }

    public BlockingQueue<BinanceTradeEvent> getEventQueue() {
        return eventQueue;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            BinanceTradeEvent event = jsonMapper.readValue(message.getPayload(), BinanceTradeEvent.class);

            if (!eventQueue.offer(event)) {
                long dropped = droppedEvents.incrementAndGet();
                if (dropped % 1000 == 1) {
                    log.warn("Очередь событий переполнена, потеряно событий: {}", dropped);
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
            log.info("ТИКЕР: {} | ЦЕНА: {} USDT | ОБЪЕМ СДЕЛКИ: {}",
                    event.symbol(), event.price(), event.quantity());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Ошибка транспорта WebSocket", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("Соединение закрыто: {}. Реконнект через {} сек...", status, RECONNECT_DELAY_SECONDS);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        try {
            reconnectScheduler.schedule(this::connect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Реконнект отменён: приложение останавливается");
        }
    }
}
