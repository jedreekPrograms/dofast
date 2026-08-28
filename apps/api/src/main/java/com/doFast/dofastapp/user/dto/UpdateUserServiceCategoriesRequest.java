package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserServiceCategoriesRequest(
        @NotNull(message = "Lista specjalizacji jest wymagana")
        @Size(max = 10, message = "Możesz wybrać maksymalnie 10 specjalizacji")
        List<@NotNull @Positive Long> categoryIds
) {}
