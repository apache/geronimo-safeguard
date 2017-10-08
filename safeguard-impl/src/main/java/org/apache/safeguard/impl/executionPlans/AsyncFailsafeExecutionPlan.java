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

import net.jodah.failsafe.AsyncFailsafe;
import net.jodah.failsafe.CircuitBreakerOpenException;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreaker;
import org.apache.safeguard.impl.fallback.FallbackRunner;
import org.apache.safeguard.impl.retry.FailsafeRetryDefinition;

import javax.interceptor.InvocationContext;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

public class AsyncFailsafeExecutionPlan extends SyncFailsafeExecutionPlan {
    private final ScheduledExecutorService executorService;
    private final Duration timeout;

    public AsyncFailsafeExecutionPlan(FailsafeRetryDefinition retryDefinition,
                                      FailsafeCircuitBreaker failsafeCircuitBreaker,
                                      FallbackRunner fallback,
                                      ScheduledExecutorService executorService,
                                      Duration timeout) {
        super(retryDefinition, failsafeCircuitBreaker, fallback);
        this.executorService = executorService;
        this.timeout = timeout;
    }

    @Override
    public <T> T execute(Callable<T> callable, InvocationContext invocationContext) {
        AsyncFailsafe<?> asyncFailsafe = getSyncFailsafe(invocationContext).with(executorService);
        try {
            if (this.timeout == null) {
                return asyncFailsafe.get(callable).get();
            } else {
                return asyncFailsafe
                        .get(new TimeoutWrappedCallable<>(callable, executorService, timeout,
                                super.failsafeCircuitBreaker))
                        .get();
            }
        } catch (CircuitBreakerOpenException e) {
            throw new org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException(e);
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause();
            if(cause == null) {
                throw new RuntimeException(e);
            }
            if (cause instanceof CircuitBreakerOpenException) {
                throw new org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException(cause);
            }
            else if(cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            else {
                throw new RuntimeException(e);
            }
        }
    }
}