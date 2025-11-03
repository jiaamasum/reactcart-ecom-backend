package org.masumjia.reactcartecom.cart;

import jakarta.persistence.*;
import org.masumjia.reactcartecom.catalog.Product;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    private String id; // aligns with DB char(36)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
