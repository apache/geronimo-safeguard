/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.safeguard.circuitbreaker.test;

import org.apache.safeguard.api.circuitbreaker.CircuitBreaker;
import org.apache.safeguard.api.circuitbreaker.CircuitBreakerState;
import org.apache.safeguard.impl.FailsafeExecutionManager;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CircuitTest {
    private FailsafeExecutionManager failsafeExecutionManager;
    private static final String name = "MY_CIRCUIT_1";

    @BeforeTest
    public void setupForTest() {
        failsafeExecutionManager = new FailsafeExecutionManager();
    }

    @Test
    public void shouldUseConfiguredCircuit() {
        int failureCount = 5;
        failsafeExecutionManager.getCircuitBreakerManager().newCircuitBreaker(name)
                .withFailureCount(failureCount)
                .withFailOn(Exception.class)
                .build();
        CircuitBreaker circuitBreaker = failsafeExecutionManager.getCircuitBreakerManager().getCircuitBreaker(name);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        for(int i = 0; i<failureCount;i++) {
            try {
                failsafeExecutionManager.execute(name, () -> {
                    throw new RuntimeException("Failing");
                });
            }
            catch (Exception e) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
    }
}
