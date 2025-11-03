package org.masumjia.reactcartecom.coupons;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CouponAssignmentRepository extends JpaRepository<CouponAssignment, Long> {
    List<CouponAssignment> findByCouponId(String couponId);

    @Transactional
    @Modifying
    @Query("delete from CouponAssignment a where a.coupon.id = :couponId")
    void deleteByCouponId(String couponId);
}

