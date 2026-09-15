package com.poultryflow.farm;

public class FarmProfileNotConfiguredException extends RuntimeException {

    public FarmProfileNotConfiguredException() {
        super("The farm profile has not been configured.");
    }
}
