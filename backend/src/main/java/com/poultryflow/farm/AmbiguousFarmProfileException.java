package com.poultryflow.farm;

public class AmbiguousFarmProfileException extends RuntimeException {

    public AmbiguousFarmProfileException() {
        super("The current farm cannot be resolved safely.");
    }
}
