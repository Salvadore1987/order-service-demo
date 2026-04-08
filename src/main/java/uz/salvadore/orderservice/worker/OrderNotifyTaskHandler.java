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
public class OrderNotifyTaskHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifyTaskHandler.class);

    @Override
    @JobWorker(topic = "order.notify")
    public void execute(TaskContext context) {
        log.info("[order.notify] Processing task, correlationId={}", context.getCorrelationId());
        log.info("[order.notify] Variables: {}", context.getVariables());

        try {
            context.complete(Map.of("processedAt", Instant.now().toString()));
        } catch (Exception ex) {
            log.error("[order.notify] Error processing task", ex);
            context.error(ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
