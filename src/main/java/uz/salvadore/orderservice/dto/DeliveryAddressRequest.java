package uz.salvadore.orderservice.dto;

public record DeliveryAddressRequest(
        String city,
        String country,
        String street,
        String district,
        String house,
        Integer flat
) {
}
