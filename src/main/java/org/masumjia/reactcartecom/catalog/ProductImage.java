package org.masumjia.reactcartecom.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private String url;

    @Column
    private Integer position;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}

