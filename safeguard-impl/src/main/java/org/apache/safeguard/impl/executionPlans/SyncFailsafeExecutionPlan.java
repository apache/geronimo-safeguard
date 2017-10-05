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

import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.SyncFailsafe;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreaker;
import org.apache.safeguard.impl.fallback.FallbackRunner;
import org.apache.safeguard.impl.retry.FailsafeRetryDefinition;

import javax.interceptor.InvocationContext;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class SyncFailsafeExecutionPlan implements ExecutionPlan {
    private final FailsafeRetryDefinition retryDefinition;
    private final FailsafeCircuitBreaker failsafeCircuitBreaker;
    private final FallbackRunner fallback;

    SyncFailsafeExecutionPlan(FailsafeRetryDefinition retryDefinition, FailsafeCircuitBreaker failsafeCircuitBreaker, FallbackRunner fallback) {
        this.retryDefinition = retryDefinition;
        this.failsafeCircuitBreaker = failsafeCircuitBreaker;
        this.fallback = fallback;
        validateConfig();
    }

    private void validateConfig() {
        if(retryDefinition == null && failsafeCircuitBreaker == null) {
            throw new IllegalStateException("For non-async invocations, must have at least one of RetryDefintion or CircuitBreaker defined");
        }
    }

    @Override
    public <T> T execute(Callable<T> callable, InvocationContext invocationContext) {
        SyncFailsafe<?> syncFailsafe = getSyncFailsafe(invocationContext);
        try {
            return syncFailsafe.get(callable);
        } catch (CircuitBreakerOpenException e) {
            throw new org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException(e);
        }
    }

    SyncFailsafe<?> getSyncFailsafe(InvocationContext invocationContext) {
        SyncFailsafe<?> syncFailsafe;
        Callable callable = () -> fallback.executeFallback(invocationContext);
        if(retryDefinition == null) {
            syncFailsafe = Failsafe.with(failsafeCircuitBreaker.getDefinition().getCircuitBreaker());
        }
        else {
            if(failsafeCircuitBreaker == null) {
                syncFailsafe = Failsafe.with(retryDefinition.getRetryPolicy());
            }
            else {
                syncFailsafe = Failsafe.with(retryDefinition.getRetryPolicy())
                        .with(failsafeCircuitBreaker.getDefinition().getCircuitBreaker());
            }
        }
        if(this.fallback != null) {
            syncFailsafe = syncFailsafe.withFallback(callable);
        }
        return syncFailsafe;
    }
}
