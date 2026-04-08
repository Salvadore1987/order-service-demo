package uz.salvadore.orderservice.dto;

import java.util.UUID;

public record CreateOrderResponse(
        UUID id,
        String status,
        String processInstanceId
) {
}
