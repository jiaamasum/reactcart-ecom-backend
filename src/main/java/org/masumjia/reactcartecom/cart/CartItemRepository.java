package org.masumjia.reactcartecom.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, String> {
    // Use nested property traversal for association: cart.id
    java.util.List<CartItem> findByCart_Id(String cartId);
    java.util.Optional<CartItem> findByCart_IdAndProduct_Id(String cartId, String productId);
    void deleteByCart_Id(String cartId);
}
