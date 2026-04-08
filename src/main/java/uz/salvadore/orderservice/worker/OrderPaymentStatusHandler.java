package uz.salvadore.orderservice.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.salvadore.orderservice.service.OrderService;
import uz.salvadore.processengine.worker.ExternalTaskHandler;
import uz.salvadore.processengine.worker.TaskContext;
import uz.salvadore.processengine.worker.annotation.JobWorker;

import java.time.Instant;
import java.util.Map;

@Component
public class OrderPaymentStatusHandler implements ExternalTaskHandler {

  private static final Logger log = LoggerFactory.getLogger(OrderBookTaskHandler.class);

  private final OrderService orderService;

  public OrderPaymentStatusHandler(OrderService orderService) {
    this.orderService = orderService;
  }

  @Override
  @JobWorker(topic = "order.payment.status")
  public void execute(TaskContext context) {
    try {
      log.info("[order.payment.status] Processing task, correlationId={}", context.getCorrelationId());
      log.info("[order.payment.status] Variables: {}", context.getVariables());
      String orderId = (String) context.getVariable("orderId");
      int retryCount = context.getVariable("retryCount") == null ? 0 : Integer.parseInt(context.getVariable("retryCount").toString());
      context.complete(Map.of("processedAt", Instant.now().toString(), "retryCount", retryCount + 1));
    } catch (Exception ex) {
      log.error("[order.payment.status] Error processing task", ex);
      context.error(ex.getClass().getSimpleName(), ex.getMessage());
    }
  }
}
