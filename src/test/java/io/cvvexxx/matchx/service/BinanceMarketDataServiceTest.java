package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.BinanceTradeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper; // Используем импорт из вашего кода

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinanceMarketDataServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebSocketSession webSocketSession;

    private BinanceMarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        marketDataService = new BinanceMarketDataService(objectMapper, "wss://dummy.binance.url");
    }

    @Test
    void handleTextMessage_ValidJson_AddsEventToQueue() {
        // given
        String jsonPayload = "{\"e\":\"trade\",\"s\":\"BTCUSDT\",\"p\":\"50000.00\",\"q\":\"1.5\"}";
        TextMessage textMessage = new TextMessage(jsonPayload);

        BinanceTradeEvent expectedEvent = new BinanceTradeEvent(
                "trade", 123456789L, "BTCUSDT", 1L, "50000.00", "1.5", 123456789L, false
        );

        when(objectMapper.readValue(eq(jsonPayload), eq(BinanceTradeEvent.class)))
                .thenReturn(expectedEvent);

        // when
        marketDataService.handleTextMessage(webSocketSession, textMessage);

        // then
        assertEquals(1, marketDataService.getEventQueue().size(), "Event should be added to the queue");
        assertEquals(expectedEvent, marketDataService.getEventQueue().poll());
    }

    @Test
    void handleTextMessage_InvalidJson_DoesNotThrowAndDoesNotQueue() {
        // given
        String invalidJson = "invalid json";
        TextMessage textMessage = new TextMessage(invalidJson);

        when(objectMapper.readValue(eq(invalidJson), eq(BinanceTradeEvent.class)))
                .thenThrow(new RuntimeException("JSON Parse error"));

        // when
        marketDataService.handleTextMessage(webSocketSession, textMessage);

        // then
        assertTrue(marketDataService.getEventQueue().isEmpty(), "Queue should be empty on parsing error");
    }

    @Test
    void handleTextMessage_QueueFull_HandlesGracefully() {
        // given
        BinanceTradeEvent dummyEvent = new BinanceTradeEvent(
                "trade", 1L, "BTCUSDT", 1L, "100.0", "1.0", 1L, false
        );

        when(objectMapper.readValue(any(String.class), eq(BinanceTradeEvent.class)))
                .thenReturn(dummyEvent);

        for (int i = 0; i < 10_000; i++) {
            marketDataService.getEventQueue().offer(dummyEvent);
        }
        assertEquals(10_000, marketDataService.getEventQueue().size());

        // when
        TextMessage textMessage = new TextMessage("{}");
        marketDataService.handleTextMessage(webSocketSession, textMessage);

        // then
        assertEquals(10_000, marketDataService.getEventQueue().size(), "Queue size should not exceed capacity");
    }
}