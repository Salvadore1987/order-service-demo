package uz.salvadore.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    private String firstName;

    private String lastName;

    private Long itemId;

    private BigDecimal amount;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String processInstanceId;

    private LocalDateTime createdAt;

    @Embedded
    private DeliveryAddress deliveryAddress;

    protected Order() {
    }

    public Order(UUID id, String firstName, String lastName, Long itemId,
                 BigDecimal amount, Integer quantity, DeliveryAddress deliveryAddress) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.itemId = itemId;
        this.amount = amount;
        this.quantity = quantity;
        this.deliveryAddress = deliveryAddress;
        this.status = OrderStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Long getItemId() {
        return itemId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public DeliveryAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
