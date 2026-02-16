package com.doFast.dofastapp.job.dto;

import java.math.BigDecimal;

public class JobResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private String status;

    public JobResponse(Long id, String title, String description, BigDecimal price, String status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getStatus() {
        return status;
    }
}
