package io.cvvexxx.matchx.service;

import io.cvvexxx.matchx.dto.CreateOrderRequest;
import io.cvvexxx.matchx.dto.OrderResponse;
import io.cvvexxx.matchx.entity.order.Order;
import io.cvvexxx.matchx.entity.order.OrderStatus;
import io.cvvexxx.matchx.entity.order.OrderType;
import io.cvvexxx.matchx.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        if (request.type() == OrderType.LIMIT && request.price() == null) {
            throw new IllegalArgumentException("price is required for LIMIT order");
        }

        Order order = Order.builder()
                .userId(request.userId())
                .symbol(request.symbol().toUpperCase())
                .side(request.side())
                .type(request.type())
                .price(request.type() == OrderType.MARKET ? null : request.price())
                .quantity(request.quantity())
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.NEW)
                .build();

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NoSuchElementException("order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getByUserId(UUID userId) {
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }
}
