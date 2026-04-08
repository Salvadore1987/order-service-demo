package uz.salvadore.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uz.salvadore.orderservice.client.ProcessInstanceClient;
import uz.salvadore.orderservice.config.OAuth2TokenProvider;
import uz.salvadore.orderservice.domain.Order;
import uz.salvadore.orderservice.dto.CreateOrderRequest;
import uz.salvadore.orderservice.dto.DeliveryAddressRequest;
import uz.salvadore.orderservice.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OrderController integration tests")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ProcessInstanceClient processInstanceClient;

    @MockitoBean
    private OAuth2TokenProvider oAuth2TokenProvider;

    @Nested
    @DisplayName("POST /orders")
    class CreateOrder {

        @Test
        @DisplayName("should create order, persist it to DB, and return 201 with response body")
        void shouldCreateOrderSuccessfully() throws Exception {
            // Arrange
            String processInstanceId = "process-instance-42";
            given(processInstanceClient.createInstance(eq("order-process"), anyMap()))
                    .willReturn(Map.of("id", processInstanceId));

            DeliveryAddressRequest addressRequest = new DeliveryAddressRequest(
                    "Tashkent",
                    "Uzbekistan",
                    "Amir Temur Avenue",
                    "Yakkasaray",
                    "15A",
                    12
            );

            CreateOrderRequest request = new CreateOrderRequest(
                    "John",
                    "Doe",
                    100L,
                    new BigDecimal("250.00"),
                    3,
                    addressRequest
            );

            String requestJson = objectMapper.writeValueAsString(request);

            // Act & Assert (HTTP response)
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.processInstanceId").value(processInstanceId));

            // Assert (database state)
            List<Order> orders = orderRepository.findAll();
            assertThat(orders).hasSize(1);

            Order savedOrder = orders.get(0);
            assertThat(savedOrder.getFirstName()).isEqualTo("John");
            assertThat(savedOrder.getLastName()).isEqualTo("Doe");
            assertThat(savedOrder.getItemId()).isEqualTo(100L);
            assertThat(savedOrder.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
            assertThat(savedOrder.getQuantity()).isEqualTo(3);
            assertThat(savedOrder.getProcessInstanceId()).isEqualTo(processInstanceId);
            assertThat(savedOrder.getDeliveryAddress().getCity()).isEqualTo("Tashkent");
            assertThat(savedOrder.getDeliveryAddress().getCountry()).isEqualTo("Uzbekistan");

            // Assert (ProcessInstanceClient was called with correct arguments)
            verify(processInstanceClient).createInstance(eq("order-process"), anyMap());
        }

        @Test
        @DisplayName("should pass correct process variables to ProcessInstanceClient")
        void shouldPassCorrectVariablesToProcessEngine() throws Exception {
            // Arrange
            given(processInstanceClient.createInstance(eq("order-process"), anyMap()))
                    .willReturn(Map.of("id", "process-99"));

            CreateOrderRequest request = new CreateOrderRequest(
                    "Jane",
                    "Smith",
                    200L,
                    new BigDecimal("500.00"),
                    5,
                    new DeliveryAddressRequest("Samarkand", "Uzbekistan", "Registan", "Center", "1", 1)
            );

            String requestJson = objectMapper.writeValueAsString(request);

            // Act
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated());

            // Assert (verify the variables map sent to the process engine)
            verify(processInstanceClient).createInstance(eq("order-process"), org.mockito.ArgumentMatchers.argThat(variables ->
                    variables.containsKey("orderId") && variables.get("orderId") != null
            ));
        }

        @Test
        @DisplayName("should return 400 when request body is missing")
        void shouldReturn400WhenRequestBodyMissing() throws Exception {
            // Arrange -- no body

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should propagate exception when ProcessInstanceClient fails")
        void shouldPropagateExceptionWhenProcessEngineFails() throws Exception {
            // Arrange
            given(processInstanceClient.createInstance(eq("order-process"), anyMap()))
                    .willThrow(new RuntimeException("Process Engine unavailable"));

            CreateOrderRequest request = new CreateOrderRequest(
                    "John",
                    "Doe",
                    100L,
                    new BigDecimal("250.00"),
                    3,
                    new DeliveryAddressRequest("Tashkent", "Uzbekistan", "Street", "District", "1", 1)
            );

            String requestJson = objectMapper.writeValueAsString(request);

            // Act & Assert
            assertThatThrownBy(() ->
                    mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
            ).rootCause()
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Process Engine unavailable");
        }
    }
}
