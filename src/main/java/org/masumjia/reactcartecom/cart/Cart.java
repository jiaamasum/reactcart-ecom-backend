package org.masumjia.reactcartecom.cart;

import jakarta.persistence.*;
import org.masumjia.reactcartecom.user.User;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "carts")
public class Cart {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // nullable for guests

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "subtotal", precision = 12, scale = 2)
    private java.math.BigDecimal subtotal;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private java.math.BigDecimal discountAmount;

    @Column(name = "total", precision = 12, scale = 2)
    private java.math.BigDecimal total;
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public java.math.BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(java.math.BigDecimal subtotal) { this.subtotal = subtotal; }
    public java.math.BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(java.math.BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public java.math.BigDecimal getTotal() { return total; }
    public void setTotal(java.math.BigDecimal total) { this.total = total; }
}
