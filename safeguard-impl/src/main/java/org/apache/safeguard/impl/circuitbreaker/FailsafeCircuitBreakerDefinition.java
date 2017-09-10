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
import org.apache.safeguard.api.circuitbreaker.CircuitBreakerDefinition;

import java.time.Duration;
import java.util.Collection;

public class FailsafeCircuitBreakerDefinition implements CircuitBreakerDefinition{
    private final CircuitBreaker circuitBreaker;
    private final Collection<Class<? extends Throwable>> failOnExceptions;

    public FailsafeCircuitBreakerDefinition(CircuitBreaker circuitBreaker, Collection<Class<? extends Throwable>> failOnExceptions) {
        this.circuitBreaker = circuitBreaker;
        this.failOnExceptions = failOnExceptions;
    }

    @Override
    public Collection<Class<? extends Throwable>> getFailOn() {
        return failOnExceptions;
    }

    @Override
    public Duration getDelay() {
        net.jodah.failsafe.util.Duration delay = circuitBreaker.getDelay();
        return Duration.ofMillis(delay.toMillis());
    }

    @Override
    public int getRequestVolumeThreshold() {
        return circuitBreaker.getFailureThreshold().denominator;
    }

    @Override
    public double getFailureRatio() {
        return circuitBreaker.getFailureThreshold().ratio;
    }

    @Override
    public int getSuccessThreshold() {
        return circuitBreaker.getSuccessThreshold().denominator;
    }

    @Override
    public double getSuccessRatio() {
        return circuitBreaker.getSuccessThreshold().ratio;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
