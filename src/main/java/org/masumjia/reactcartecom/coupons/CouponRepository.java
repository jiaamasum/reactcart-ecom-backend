package org.masumjia.reactcartecom.coupons;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CouponRepository extends JpaRepository<Coupon, String>, JpaSpecificationExecutor<Coupon> {
    boolean existsByCode(String code);

    @Query("select c.id from Coupon c")
    java.util.List<String> findAllIds();

    java.util.Optional<Coupon> findByCodeIgnoreCase(String code);
    long countByActiveTrue();
    @Query("select count(c) from Coupon c where c.active = true and (c.expiryDate is null or c.expiryDate > CURRENT_TIMESTAMP)")
    long countActiveAndNotExpired();
}
