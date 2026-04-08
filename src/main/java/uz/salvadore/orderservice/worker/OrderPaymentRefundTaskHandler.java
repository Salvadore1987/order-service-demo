package uz.salvadore.orderservice.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.salvadore.processengine.worker.ExternalTaskHandler;
import uz.salvadore.processengine.worker.TaskContext;
import uz.salvadore.processengine.worker.annotation.JobWorker;

import java.time.Instant;
import java.util.Map;

@Component
public class OrderPaymentRefundTaskHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentRefundTaskHandler.class);

    @Override
    @JobWorker(topic = "order.payment.refund")
    public void execute(TaskContext context) {
        log.info("[order.payment.refund] Processing task, correlationId={}", context.getCorrelationId());
        log.info("[order.payment.refund] Variables: {}", context.getVariables());

        try {
            context.complete(Map.of("processedAt", Instant.now().toString()));
        } catch (Exception ex) {
            log.error("[order.payment.refund] Error processing task", ex);
            context.error(ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
