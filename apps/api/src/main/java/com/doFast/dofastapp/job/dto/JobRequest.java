package com.doFast.dofastapp.job.dto;

import com.doFast.dofastapp.job.assignment.JobAssignmentMode;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
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

    @DecimalMin(value = "0.00")
    @DecimalMax(value = "10000.00")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal expenseBudget = BigDecimal.ZERO.setScale(2);

    @NotNull
    @Positive
    private Long categoryId;

    private UUID routeQuoteId;

    @Valid
    private RoutePointRequest location;

    private JobAssignmentMode assignmentMode = JobAssignmentMode.INSTANT;

    private boolean priceNegotiationEnabled;

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getExpenseBudget() { return expenseBudget == null ? BigDecimal.ZERO.setScale(2) : expenseBudget; }
    public Long getCategoryId() { return categoryId; }
    public UUID getRouteQuoteId() { return routeQuoteId; }
    public RoutePointRequest getLocation() { return location; }
    public JobAssignmentMode getAssignmentMode() { return assignmentMode; }
    public boolean isPriceNegotiationEnabled() { return priceNegotiationEnabled; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setExpenseBudget(BigDecimal expenseBudget) { this.expenseBudget = expenseBudget; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public void setRouteQuoteId(UUID routeQuoteId) { this.routeQuoteId = routeQuoteId; }
    public void setLocation(RoutePointRequest location) { this.location = location; }
    public void setAssignmentMode(JobAssignmentMode assignmentMode) { this.assignmentMode = assignmentMode; }
    public void setPriceNegotiationEnabled(boolean priceNegotiationEnabled) { this.priceNegotiationEnabled = priceNegotiationEnabled; }
}
