package uz.salvadore.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.salvadore.orderservice.domain.Order;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
