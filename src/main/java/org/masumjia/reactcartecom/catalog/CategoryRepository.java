package org.masumjia.reactcartecom.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findBySlug(String slug);
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySlug(String slug);
    @org.springframework.data.jpa.repository.Query("select c.id from Category c")
    java.util.List<String> findAllIds();
}
