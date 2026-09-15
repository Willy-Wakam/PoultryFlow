package com.poultryflow.farm;

public record FarmProfileCommand(
        String name,
        String contactEmail,
        String contactPhone,
        String timezone,
        String countryCode,
        String currencyCode) {
}
