package uz.salvadore.orderservice.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.salvadore.orderservice.domain.OrderStatus;
import uz.salvadore.orderservice.exception.PaymentChargeException;
import uz.salvadore.orderservice.service.OrderService;
import uz.salvadore.processengine.worker.ExternalTaskHandler;
import uz.salvadore.processengine.worker.TaskContext;
import uz.salvadore.processengine.worker.annotation.JobWorker;

import java.time.Instant;
import java.util.Map;

@Component
public class OrderPaymentChargeTaskHandler implements ExternalTaskHandler {

  private static final Logger log = LoggerFactory.getLogger(OrderPaymentChargeTaskHandler.class);

  private final OrderService orderService;

  public OrderPaymentChargeTaskHandler(OrderService orderService) {
    this.orderService = orderService;
  }

  @Override
  @JobWorker(topic = "order.payment.charge")
  public void execute(TaskContext context) {
    log.info("[order.payment.charge] Processing task, correlationId={}", context.getCorrelationId());
    log.info("[order.payment.charge] Variables: {}", context.getVariables());
    boolean isPaymentSuccess = false;
    try {
      String orderId = (String) context.getVariable("orderId");
      int retryCount = context.getVariable("retryCount") == null ? 0 : (int) context.getVariable("retryCount");
      if (retryCount > 5) {
        throw new RuntimeException("Retry count exceeded");
      }
      if (orderId != null) {
        throw new PaymentChargeException();
      }
      isPaymentSuccess = true;
      orderService.updateOrderStatus(orderId, OrderStatus.PAID);
      context.complete(Map.of("processedAt", Instant.now().toString(), "isPaymentSuccess", isPaymentSuccess));
    } catch (Exception ex) {
      log.error("[order.payment.charge] Error processing task", ex);
      if (ex instanceof PaymentChargeException) {
        context.complete(Map.of("isPaymentSuccess", isPaymentSuccess));
      } else {
        context.error(ex.getClass().getSimpleName(), ex.getMessage());
      }
    }
  }
}
