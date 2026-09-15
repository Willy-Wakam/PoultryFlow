package com.poultryflow.farm.api;

import com.poultryflow.farm.FarmProfileCommand;
import com.poultryflow.farm.api.validation.IanaTimeZone;
import com.poultryflow.farm.api.validation.IsoCountryCode;
import com.poultryflow.farm.api.validation.IsoCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "FarmProfileRequest")
public record FarmProfileRequest(
        @NotBlank(message = "is required")
        @Size(max = 160, message = "must be at most 160 characters")
        String name,

        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String contactEmail,

        @Size(max = 40, message = "must be at most 40 characters")
        String contactPhone,

        @NotBlank(message = "is required")
        @Size(max = 64, message = "must be at most 64 characters")
        @IanaTimeZone
        String timezone,

        @Size(min = 2, max = 2, message = "must contain 2 characters")
        @IsoCountryCode
        String countryCode,

        @Size(min = 3, max = 3, message = "must contain 3 characters")
        @IsoCurrencyCode
        String currencyCode) {

    FarmProfileCommand toCommand() {
        return new FarmProfileCommand(
                name,
                contactEmail,
                contactPhone,
                timezone,
                countryCode,
                currencyCode);
    }
}
