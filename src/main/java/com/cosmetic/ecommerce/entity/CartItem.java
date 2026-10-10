package com.cosmetic.ecommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cart_id")
    @JsonIgnore // JSON හදද්දි Infinite loop වීම වළක්වයි
    private Cart cart;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product; // මොකක්ද භාණ්ඩය

    private Integer quantity; // කීයක් ගත්තද
    private Double price; // භාණ්ඩයේ ගාන x කීයක් ගත්තද

    // Getters and Setters (Right Click -> Generate -> Getter and Setter)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}