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

package org.apache.safeguard.impl.circuitbreaker;

import net.jodah.failsafe.CircuitBreaker;
import org.apache.safeguard.api.circuitbreaker.CircuitBreakerBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

public class FailsafeCircuitBreakerBuilder implements CircuitBreakerBuilder {
    private final String name;
    private final FailsafeCircuitBreakerManager failsafeCircuitBreakerManager;
    private final CircuitBreaker circuitBreaker;
    private final Collection<Class<? extends Throwable>> failOns;

    public FailsafeCircuitBreakerBuilder(String name, FailsafeCircuitBreakerManager failsafeCircuitBreakerManager) {
        this.name = name;
        this.failsafeCircuitBreakerManager = failsafeCircuitBreakerManager;
        this.circuitBreaker = new CircuitBreaker();
        this.failOns = new ArrayList<>();
    }

    @Override
    public FailsafeCircuitBreakerBuilder withDelay(Duration delay) {
        circuitBreaker.withDelay(delay.toNanos(), TimeUnit.NANOSECONDS);
        return this;
    }

    @Override
    public FailsafeCircuitBreakerBuilder withFailureCount(int failureCount) {
        circuitBreaker.withFailureThreshold(failureCount);
        return this;
    }

    @Override
    public FailsafeCircuitBreakerBuilder withFailures(int failureCount, int requestCount) {
        circuitBreaker.withFailureThreshold(failureCount, requestCount);
        return this;
    }

    @Override
    public FailsafeCircuitBreakerBuilder withSuccessCount(int successCount) {
        circuitBreaker.withSuccessThreshold(successCount);
        return this;
    }

    @Override
    public FailsafeCircuitBreakerBuilder withSuccesses(int successCount, int requestCount) {
        circuitBreaker.withSuccessThreshold(successCount, requestCount);
        return this;
    }

    @Override
    public FailsafeCircuitBreakerBuilder withFailOn(Class<? extends Throwable>... failOn) {
        this.failOns.addAll(asList(failOn));
        circuitBreaker.failOn(failOn);
        return this;
    }

    @Override
    public FailsafeCircuitBreakerDefinition build() {
        if(failOns.isEmpty()) {
            throw new IllegalStateException("At least one exception must be registered for failure detection");
        }
        FailsafeCircuitBreakerDefinition failsafeCircuitBreakerDefinition = new FailsafeCircuitBreakerDefinition(circuitBreaker, failOns);
        this.failsafeCircuitBreakerManager.register(name, failsafeCircuitBreakerDefinition);
        return failsafeCircuitBreakerDefinition;
    }
}
