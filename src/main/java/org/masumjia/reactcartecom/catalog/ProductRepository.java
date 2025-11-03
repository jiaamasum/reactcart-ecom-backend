package org.masumjia.reactcartecom.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {
    @org.springframework.data.jpa.repository.Query("select p.id from Product p")
    java.util.List<String> findAllIds();

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int decrementIfAvailable(String id, int qty);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int increment(String id, int qty);

    long countByStockLessThanEqual(Integer stock);
}
