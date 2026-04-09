package uz.salvadore.orderservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.salvadore.orderservice.client.ProcessInstanceClient;
import uz.salvadore.orderservice.domain.DeliveryAddress;
import uz.salvadore.orderservice.domain.Order;
import uz.salvadore.orderservice.domain.OrderStatus;
import uz.salvadore.orderservice.dto.CreateOrderRequest;
import uz.salvadore.orderservice.dto.CreateOrderResponse;
import uz.salvadore.orderservice.repository.OrderRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProcessInstanceClient processInstanceClient;

    public OrderService(OrderRepository orderRepository, ProcessInstanceClient processInstanceClient) {
        this.orderRepository = orderRepository;
        this.processInstanceClient = processInstanceClient;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        DeliveryAddress address = new DeliveryAddress(
                request.deliveryAddress().city(),
                request.deliveryAddress().country(),
                request.deliveryAddress().street(),
                request.deliveryAddress().district(),
                request.deliveryAddress().house(),
                request.deliveryAddress().flat()
        );

        Order order = new Order(
                UUID.randomUUID(),
                request.firstName(),
                request.lastName(),
                request.itemId(),
                request.amount(),
                request.quantity(),
                address
        );

        orderRepository.save(order);
        log.info("Order saved with id={}, status={}", order.getId(), order.getStatus());

        Map<String, Object> variables = Map.of(
                "orderId", order.getId().toString()
        );

        Map<String, Object> processResponse = processInstanceClient.createInstance("order-process", order.getId().toString(), variables);
        String processInstanceId = String.valueOf(processResponse.get("id"));

        order.setProcessInstanceId(processInstanceId);
        orderRepository.save(order);

        log.info("Process instance created: {}", processInstanceId);

        return new CreateOrderResponse(order.getId(), order.getStatus().name(), processInstanceId);
    }

    @Transactional
    public void updateOrderStatus(String orderId, OrderStatus status) {
        UUID id = UUID.fromString(orderId);
        orderRepository.findById(id).ifPresent(order -> {
            order.setStatus(status);
            orderRepository.save(order);
            log.info("Order {} status updated to {}", orderId, status);
        });
    }
}
