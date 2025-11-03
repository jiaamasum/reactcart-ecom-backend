package org.masumjia.reactcartecom.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, String> {
    List<ProductImage> findByProductIdOrderByPositionAsc(String productId);
    void deleteByProductId(String productId);
}

