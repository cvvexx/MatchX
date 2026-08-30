package io.cvvexxx.matchx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cvvexxx.matchx.dto.BinanceTradeEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class BinanceMarketDataService extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketDataService.class);
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/ws/btcusdt@trade";

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();

    private final BlockingQueue<BinanceTradeEvent> eventQueue = new ArrayBlockingQueue<>(10_000);

    private final AtomicReference<BinanceTradeEvent> latestEvent = new AtomicReference<>();

    public BinanceMarketDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        connect();
    }

    public void connect() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        try {
            client.execute(this, null, URI.create(BINANCE_WS_URL));
        } catch (Exception e) {
            log.error("Ошибка подключения. Реконнект через 5 сек...", e);
            scheduleReconnect();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            BinanceTradeEvent event = objectMapper.readValue(message.getPayload(), BinanceTradeEvent.class);

            eventQueue.offer(event);

            latestEvent.set(event);

        } catch (Exception e) {
            log.error("Ошибка парсинга JSON", e);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void printLatestPriceToConsole() {
        BinanceTradeEvent event = latestEvent.get();
        if (event != null) {
            System.out.printf("🕒 ТИКЕР: %s | ЦЕНА: %s USDT | ОБЪЕМ СДЕЛКИ: %s%n",
                    event.symbol(), event.price(), event.quantity());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        reconnectScheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }
}
