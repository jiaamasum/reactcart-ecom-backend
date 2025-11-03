package org.masumjia.reactcartecom.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Order> {
    List<Order> findByUser_Id(String userId);
    java.util.Optional<Order> findByOrderNumber(Integer orderNumber);

    long countByUser_Id(String userId);
    long countByUser_IdAndStatus(String userId, OrderStatus status);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(o.total), 0) from Order o where o.user.id = :userId and o.status = :status")
    java.math.BigDecimal sumTotalByUserAndStatus(@org.springframework.data.repository.query.Param("userId") String userId,
                                                 @org.springframework.data.repository.query.Param("status") OrderStatus status);

    long countByStatus(OrderStatus status);
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(o.total), 0) from Order o where o.status = :status")
    java.math.BigDecimal sumTotalByStatus(@org.springframework.data.repository.query.Param("status") OrderStatus status);

    @org.springframework.data.jpa.repository.Query("select YEAR(o.createdAt) as y, MONTH(o.createdAt) as m, coalesce(sum(o.total),0) as s from Order o where o.status = :status and o.createdAt is not null group by YEAR(o.createdAt), MONTH(o.createdAt) order by y, m")
    java.util.List<Object[]> monthlyTotalsByStatus(@org.springframework.data.repository.query.Param("status") OrderStatus status);
}
