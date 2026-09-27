package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.CreateOrderRequest;
import io.cvvexxx.matchx.dto.OrderResponse;
import io.cvvexxx.matchx.entity.Order;
import io.cvvexxx.matchx.entity.OrderSide;
import io.cvvexxx.matchx.entity.OrderStatus;
import io.cvvexxx.matchx.entity.OrderType;
import io.cvvexxx.matchx.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void create_LimitOrderValid_Success() {
        // given
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                userId, "BTCUSDT", OrderSide.BUY, OrderType.LIMIT, BigDecimal.valueOf(50000), BigDecimal.ONE
        );

        Order savedOrder = createMockOrder(request);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // when
        OrderResponse response = orderService.create(request);

        // then
        assertNotNull(response);
        assertEquals(OrderStatus.NEW, response.status());
        assertEquals(BigDecimal.valueOf(50000), response.price());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();

        assertEquals(request.userId(), capturedOrder.getUserId());
        assertEquals(request.symbol(), capturedOrder.getSymbol());
        assertEquals(request.price(), capturedOrder.getPrice());
        assertEquals(BigDecimal.ZERO, capturedOrder.getFilledQuantity());
    }

    @Test
    void create_MarketOrderValid_Success() {
        // given
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(
                userId, "ETHUSDT", OrderSide.SELL, OrderType.MARKET, null, BigDecimal.TEN
        );

        Order savedOrder = createMockOrder(request);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // when
        OrderResponse response = orderService.create(request);

        // then
        assertNotNull(response);
        assertNull(response.price());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertNull(orderCaptor.getValue().getPrice());
    }

    @Test
    void create_LimitOrderMissingPrice_ThrowsException() {
        // given
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(), "BTCUSDT", OrderSide.BUY, OrderType.LIMIT, null, BigDecimal.ONE
        );

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.create(request);
        });
        assertEquals("price is required for LIMIT order", exception.getMessage());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getById_OrderExists_ReturnsOrderResponse() {
        // given
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(UUID.randomUUID());
        order.setSymbol("BTCUSDT");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // when
        OrderResponse response = orderService.getById(orderId);

        // then
        assertNotNull(response);
        assertEquals(orderId, response.id());
        assertEquals("BTCUSDT", response.symbol());
    }

    @Test
    void getById_OrderNotFound_ThrowsNoSuchElementException() {
        // given
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(NoSuchElementException.class, () -> orderService.getById(orderId));
    }

    @Test
    void getByUserId_ReturnsOrderList() {
        // given
        UUID userId = UUID.randomUUID();
        Order order1 = new Order();
        order1.setId(UUID.randomUUID());
        Order order2 = new Order();
        order2.setId(UUID.randomUUID());

        when(orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(order1, order2));

        // when
        List<OrderResponse> responses = orderService.getByUserId(userId);

        // then
        assertEquals(2, responses.size());
        verify(orderRepository).findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    private Order createMockOrder(CreateOrderRequest request) {
        return Order.builder()
                .id(UUID.randomUUID())
                .userId(request.userId())
                .symbol(request.symbol())
                .side(request.side())
                .type(request.type())
                .price(request.price())
                .quantity(request.quantity())
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}