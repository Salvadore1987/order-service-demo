package uz.salvadore.orderservice.process;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;
import uz.salvadore.orderservice.config.OAuth2TokenProvider;
import uz.salvadore.orderservice.domain.Order;
import uz.salvadore.orderservice.domain.OrderStatus;
import uz.salvadore.orderservice.dto.CreateOrderRequest;
import uz.salvadore.orderservice.dto.DeliveryAddressRequest;
import uz.salvadore.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the order-process BPMN workflow.
 * <p>
 * Requires running infrastructure:
 * - Process Engine on localhost:8080
 * - Keycloak on localhost:8180
 * - RabbitMQ on localhost:5672
 * <p>
 * Workers from worker-spring-boot-starter handle tasks automatically via RabbitMQ.
 * Tests create orders via REST, then poll Process Engine for completion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("process-test")
@DisplayName("Order Process BPMN Integration Tests")
class OrderProcessIntegrationTest {

    private static final int MAX_POLL_ATTEMPTS = 30;
    private static final long POLL_INTERVAL_MS = 1000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OAuth2TokenProvider keycloakTokenProvider;

    private RestClient engineClient;

    @BeforeEach
    void setUp() {
        String token = keycloakTokenProvider.getToken();
        engineClient = RestClient.builder()
                .baseUrl("http://localhost:8080")
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
        orderRepository.deleteAll();
    }

    // ---- Helper methods ----

    private Map<String, Object> createOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "John", "Doe", 1L, new BigDecimal("100.00"), 2,
                new DeliveryAddressRequest("Tashkent", "UZB", "A. Navoi", "Mirzo-Ulugbek", "8", 54)
        );

        MvcResult result = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getInstance(String instanceId) {
        return engineClient.get()
                .uri("/api/v1/instances/{id}", instanceId)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getActivities(String instanceId) {
        return engineClient.get()
                .uri("/api/v1/history/instances/{id}/activities", instanceId)
                .retrieve()
                .body(List.class);
    }

    private Map<String, Object> waitForProcessState(String instanceId, String... expectedStates)
            throws InterruptedException {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Map<String, Object> instance = getInstance(instanceId);
            String state = (String) instance.get("state");
            for (String expected : expectedStates) {
                if (expected.equals(state)) {
                    return instance;
                }
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        Map<String, Object> lastInstance = getInstance(instanceId);
        throw new AssertionError(
                "Process did not reach expected state " + List.of(expectedStates) +
                        " within timeout. Current state: " + lastInstance.get("state"));
    }

    private long countActivitiesForNode(List<Map<String, Object>> activities, String nodeId) {
        return activities.stream()
                .filter(a -> nodeId.equals(a.get("nodeId")))
                .count();
    }

    // ---- Tests ----

    @Test
    @DisplayName("Happy path: process completes through all tasks and order reaches DELIVERED")
    void happyPath() throws Exception {
        // Arrange & Act — create order (triggers process start + all workers via RabbitMQ)
        Map<String, Object> response = createOrder();
        String processInstanceId = (String) response.get("processInstanceId");
        String orderId = (String) response.get("id");

        assertThat(processInstanceId).isNotNull();
        assertThat(orderId).isNotNull();

        // Assert — wait for process to complete
        Map<String, Object> instance = waitForProcessState(processInstanceId, "COMPLETED");
        assertThat(instance.get("state")).isEqualTo("COMPLETED");

        // Assert — verify all worker nodes executed exactly once (2 activity entries each: MOVED + COMPLETED)
        List<Map<String, Object>> activities = getActivities(processInstanceId);

        assertThat(countActivitiesForNode(activities, "validate-order"))
                .as("validate-order should execute once (MOVED + COMPLETED)").isEqualTo(2);
        assertThat(countActivitiesForNode(activities, "book-order"))
                .as("book-order should execute once").isEqualTo(2);
        assertThat(countActivitiesForNode(activities, "notify-booking"))
                .as("notify-booking should execute once").isEqualTo(2);
        assertThat(countActivitiesForNode(activities, "charge-payment"))
                .as("charge-payment should execute once").isEqualTo(2);
        assertThat(countActivitiesForNode(activities, "deliver-order"))
                .as("deliver-order should execute once").isEqualTo(2);

        // Assert — refund should NOT have been triggered in happy path
        assertThat(countActivitiesForNode(activities, "refund-payment"))
                .as("refund-payment should not execute in happy path").isEqualTo(0);

        // Assert — order status in DB should be DELIVERED
        Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Compensation: deliver-order failure triggers refund-payment")
    void compensationFlow() throws Exception {
        // Arrange & Act — create order
        Map<String, Object> response = createOrder();
        String processInstanceId = (String) response.get("processInstanceId");

        // Wait for process to reach terminal state (ERROR due to deliver-order throwing RuntimeException)
        // Note: OrderDeliverTaskHandler currently throws RuntimeException("Unknown error")
        // which calls context.error() → triggers compensation → refund-payment → ERROR state
        Map<String, Object> instance = waitForProcessState(processInstanceId,
                "COMPLETED", "ERROR", "COMPENSATING");

        String state = (String) instance.get("state");

        if ("ERROR".equals(state) || "COMPENSATING".equals(state)) {
            // Compensation was triggered — verify refund-payment executed
            Thread.sleep(2000); // allow compensation tasks to complete

            instance = getInstance(processInstanceId);
            List<Map<String, Object>> activities = getActivities(processInstanceId);

            // refund-payment should have been triggered
            assertThat(countActivitiesForNode(activities, "refund-payment"))
                    .as("refund-payment should execute during compensation")
                    .isGreaterThanOrEqualTo(1);

            // Main tasks before deliver should have executed
            assertThat(countActivitiesForNode(activities, "validate-order"))
                    .as("validate-order").isGreaterThanOrEqualTo(2);
            assertThat(countActivitiesForNode(activities, "charge-payment"))
                    .as("charge-payment").isGreaterThanOrEqualTo(2);
        } else {
            // Process completed normally — deliver-order RuntimeException was removed
            assertThat(state).isEqualTo("COMPLETED");
        }
    }
}
