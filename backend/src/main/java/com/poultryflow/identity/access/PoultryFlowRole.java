package com.poultryflow.identity.access;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PoultryFlowRole {
    OWNER,
    MANAGER,
    STAFF,
    ACCOUNTANT,
    VIEWER;

    private static final Map<String, PoultryFlowRole> BY_TOKEN_VALUE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(
                    PoultryFlowRole::name,
                    Function.identity()));

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<PoultryFlowRole> fromTokenValue(String tokenValue) {
        return Optional.ofNullable(BY_TOKEN_VALUE.get(tokenValue));
    }
}
