package com.doFast.dofastapp.job.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class JobRequest {

    @NotBlank
    @Size(min = 3, max = 160)
    private String title;

    @NotBlank
    @Size(min = 10, max = 4000)
    private String description;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal price;

    @NotNull
    @Positive
    private Long categoryId;

    @NotNull
    private UUID routeQuoteId;

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Long getCategoryId() { return categoryId; }
    public UUID getRouteQuoteId() { return routeQuoteId; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public void setRouteQuoteId(UUID routeQuoteId) { this.routeQuoteId = routeQuoteId; }
}
