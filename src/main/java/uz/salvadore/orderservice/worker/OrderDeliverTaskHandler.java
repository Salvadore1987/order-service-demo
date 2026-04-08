package uz.salvadore.orderservice.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.salvadore.orderservice.domain.OrderStatus;
import uz.salvadore.orderservice.service.OrderService;
import uz.salvadore.processengine.worker.ExternalTaskHandler;
import uz.salvadore.processengine.worker.TaskContext;
import uz.salvadore.processengine.worker.annotation.JobWorker;

import java.time.Instant;
import java.util.Map;

@Component
public class OrderDeliverTaskHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliverTaskHandler.class);

    private final OrderService orderService;

    public OrderDeliverTaskHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    @JobWorker(topic = "order.deliver")
    public void execute(TaskContext context) {
        log.info("[order.deliver] Processing task, correlationId={}", context.getCorrelationId());
        log.info("[order.deliver] Variables: {}", context.getVariables());

        try {
            String orderId = (String) context.getVariable("orderId");
            if (orderId == null) {
              throw new RuntimeException("Unknown error");
            }
            orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED);

            context.complete(Map.of("processedAt", Instant.now().toString()));
        } catch (Exception ex) {
            log.error("[order.deliver] Error processing task", ex);
            context.error(ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
