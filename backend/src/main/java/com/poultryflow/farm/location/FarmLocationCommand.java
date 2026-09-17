package com.poultryflow.farm.location;

public record FarmLocationCommand(
        String name,
        FarmLocationType type) {
}
