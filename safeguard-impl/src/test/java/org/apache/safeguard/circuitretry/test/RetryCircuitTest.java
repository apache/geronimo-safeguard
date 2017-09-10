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

package org.apache.safeguard.circuitretry.test;

import org.apache.safeguard.api.circuitbreaker.CircuitBreakerState;
import org.apache.safeguard.impl.FailsafeExecutionManager;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreaker;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

public class RetryCircuitTest {
    @Test
    public void shouldExecuteBothRetryAndCircuitBreaker() {
        String name = "retryCircuitTest";
        FailsafeExecutionManager failsafeExecutionManager = new FailsafeExecutionManager();
        int maxRetries = 7;
        failsafeExecutionManager.getRetryManager()
                .newRetryDefinition(name)
                .withMaxRetries(maxRetries)
                .withRetryOn(RuntimeException.class).build();
        failsafeExecutionManager.getCircuitBreakerManager()
                .newCircuitBreaker(name)
                .withSuccessCount(2)
                .withFailures(3, 4)
                .withDelay(Duration.ofMillis(5000))
                .withFailOn(RuntimeException.class)
                .build();
        FailsafeCircuitBreaker circuitBreaker = failsafeExecutionManager.getCircuitBreakerManager().getCircuitBreaker(name);

        SimpleCallable simpleCallable = new SimpleCallable();
        try {
            failsafeExecutionManager.execute(name, simpleCallable);
        }catch (RuntimeException e) {
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(simpleCallable.counter).isEqualTo(4);
    }

    private static class SimpleCallable implements Callable<Object> {
        private int counter = 0;

        @Override
        public Object call() throws Exception {
            counter++;
            throw new RuntimeException("Invalid state");
        }
    }
}
