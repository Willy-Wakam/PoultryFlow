package com.poultryflow.farm.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class FarmProfileExceptionHandlerTests {

    @Test
    void mapsOptimisticLockingToSafeConflictProblem() {
        var request = new MockHttpServletRequest("PUT", "/api/v1/farms/current");
        var response = new FarmProfileExceptionHandler().concurrentModification(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "CONCURRENT_MODIFICATION");
        assertThat(response.getBody().toString())
                .doesNotContain(OptimisticLockingFailureException.class.getName());
    }
}
