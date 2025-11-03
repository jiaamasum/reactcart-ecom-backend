package org.masumjia.reactcartecom.orders;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    java.util.List<OrderItem> findByOrder_Id(String orderId);
}
