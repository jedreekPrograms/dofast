package com.doFast.dofastapp.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class LocationRequest {

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    @NotBlank
    @Size(min = 2, max = 120)
    private String publicLabel;

    @Size(max = 200)
    private String privateLabel;

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getPublicLabel() {
        return publicLabel;
    }

    public void setPublicLabel(String publicLabel) {
        this.publicLabel = publicLabel;
    }

    public String getPrivateLabel() {
        return privateLabel;
    }

    public void setPrivateLabel(String privateLabel) {
        this.privateLabel = privateLabel;
    }
}
