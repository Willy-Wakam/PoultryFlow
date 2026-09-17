package com.poultryflow.farm.location.api;

import com.poultryflow.farm.location.FarmLocationCommand;
import com.poultryflow.farm.location.FarmLocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFarmLocationRequest(
        @NotBlank(message = "is required")
        @Size(max = 120, message = "must be at most 120 characters")
        String name,

        @NotNull(message = "is required")
        FarmLocationType type) {

    public CreateFarmLocationRequest {
        if (name != null) {
            name = name.trim();
        }
    }

    FarmLocationCommand toCommand() {
        return new FarmLocationCommand(name, type);
    }
}
