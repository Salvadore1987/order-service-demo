package uz.salvadore.orderservice.dto;

import java.math.BigDecimal;

public record CreateOrderRequest(
        String firstName,
        String lastName,
        Long itemId,
        BigDecimal amount,
        Integer quantity,
        DeliveryAddressRequest deliveryAddress
) {
}
