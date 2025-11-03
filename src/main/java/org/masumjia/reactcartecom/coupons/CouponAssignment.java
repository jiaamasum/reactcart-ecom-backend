package org.masumjia.reactcartecom.coupons;

import jakarta.persistence.*;

@Entity
@Table(name = "coupon_assignments")
public class CouponAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_type", nullable = false, length = 20)
    private AssignmentType assignedType; // PRODUCT, CATEGORY, CUSTOMER

    @Column(name = "assigned_id", nullable = false, length = 64)
    private String assignedId;

    public enum AssignmentType { PRODUCT, CATEGORY, CUSTOMER }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Coupon getCoupon() { return coupon; }
    public void setCoupon(Coupon coupon) { this.coupon = coupon; }
    public AssignmentType getAssignedType() { return assignedType; }
    public void setAssignedType(AssignmentType assignedType) { this.assignedType = assignedType; }
    public String getAssignedId() { return assignedId; }
    public void setAssignedId(String assignedId) { this.assignedId = assignedId; }
}

