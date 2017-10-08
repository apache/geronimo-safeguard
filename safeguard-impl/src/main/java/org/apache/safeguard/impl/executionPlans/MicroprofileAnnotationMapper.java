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

package org.apache.safeguard.impl.executionPlans;

import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerBuilder;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerDefinition;
import org.apache.safeguard.impl.retry.FailsafeRetryBuilder;
import org.apache.safeguard.impl.retry.FailsafeRetryDefinition;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

class MicroprofileAnnotationMapper {
    static FailsafeRetryDefinition mapRetry(Retry retry, FailsafeRetryBuilder retryBuilder) {
        retryBuilder.withMaxRetries(retry.maxRetries())
                .withRetryOn(retry.retryOn())
                .withRetryOn(TimeoutException.class, org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class)
                .withAbortOn(retry.abortOn());
        if (retry.delay() > 0L) {
            retryBuilder.withDelay(Duration.of(retry.delay(), retry.delayUnit()));
        }
        if (retry.jitter() > 0L) {
            retryBuilder.withJitter(Duration.of(retry.jitter(), retry.jitterDelayUnit()));
        }
        if (retry.maxDuration() > 0L) {
            retryBuilder.withMaxDuration(Duration.of(retry.maxDuration(), retry.durationUnit()));
        }
        return retryBuilder.build();
    }

    static FailsafeCircuitBreakerDefinition mapCircuitBreaker(CircuitBreaker circuitBreaker,
                                                              FailsafeCircuitBreakerBuilder builder) {
        int failureCount = (int) (circuitBreaker.failureRatio() * circuitBreaker.requestVolumeThreshold());
        FailsafeCircuitBreakerBuilder failsafeCircuitBreakerBuilder = builder
                .withFailOn(circuitBreaker.failOn())
                .withFailOn(TimeoutException.class, org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class)
                .withDelay(Duration.of(circuitBreaker.delay(), circuitBreaker.delayUnit()))
                .withSuccessCount(circuitBreaker.successThreshold());
        if (failureCount > 0) {
            failsafeCircuitBreakerBuilder.withFailures(failureCount, circuitBreaker.requestVolumeThreshold());
        }
        return failsafeCircuitBreakerBuilder.build();
    }
}
