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
public class OrderBookTaskHandler implements ExternalTaskHandler {

  private static final Logger log = LoggerFactory.getLogger(OrderBookTaskHandler.class);

  private final OrderService orderService;

  public OrderBookTaskHandler(OrderService orderService) {
    this.orderService = orderService;
  }

  @Override
  @JobWorker(topic = "order.book")
  public void execute(TaskContext context) {
    try {
      Thread.sleep(5000);
      log.info("[order.book] Processing task, correlationId={}", context.getCorrelationId());
      log.info("[order.book] Variables: {}", context.getVariables());
      String orderId = (String) context.getVariable("orderId");
      orderService.updateOrderStatus(orderId, OrderStatus.BOOKED);
      context.complete(Map.of("processedAt", Instant.now().toString()));
    } catch (Exception ex) {
      log.error("[order.book] Error processing task", ex);
      context.error(ex.getClass().getSimpleName(), ex.getMessage());
    }
  }
}
