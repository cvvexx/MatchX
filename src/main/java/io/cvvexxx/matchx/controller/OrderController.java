package io.cvvexxx.matchx.controller;

import io.cvvexxx.matchx.dto.CreateOrderRequest;
import io.cvvexxx.matchx.dto.OrderResponse;
import io.cvvexxx.matchx.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriComponentsBuilder
    ) {
        OrderResponse orderResponse = orderService.create(request);

        return ResponseEntity.created(
                        uriComponentsBuilder
                                .replacePath("/api/v1/orders/{id}")
                                .build(orderResponse.id())
                )
                .body(orderResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getByUserId(@RequestParam UUID userId) {
        return ResponseEntity.ok(orderService.getByUserId(userId));
    }
}
