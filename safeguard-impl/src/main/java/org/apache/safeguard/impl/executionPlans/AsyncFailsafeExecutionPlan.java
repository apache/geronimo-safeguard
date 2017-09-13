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
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreaker;
import org.apache.safeguard.impl.retry.FailsafeRetryDefinition;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncFailsafeExecutionPlan extends SyncFailsafeExecutionPlan {
    private final ScheduledExecutorService executorService;
    private final Duration timeout;

    public AsyncFailsafeExecutionPlan(FailsafeRetryDefinition retryDefinition,
                                      FailsafeCircuitBreaker failsafeCircuitBreaker,
                                      ScheduledExecutorService executorService,
                                      Duration timeout) {
        super(retryDefinition, failsafeCircuitBreaker);
        this.executorService = executorService;
        this.timeout = timeout;
    }

    @Override
    public <T> T execute(Callable<T> callable) {
        AsyncFailsafe<?> asyncFailsafe = getSyncFailsafe().with(executorService);
        try {
            if (this.timeout == null) {
                return asyncFailsafe.get(callable).get();
            } else {
                return asyncFailsafe.get(callable).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException(e);
        }
    }
}