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

import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreaker;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerBuilder;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerManager;
import org.apache.safeguard.impl.retry.FailsafeRetryBuilder;
import org.apache.safeguard.impl.retry.FailsafeRetryDefinition;
import org.apache.safeguard.impl.retry.FailsafeRetryManager;
import org.apache.safeguard.impl.util.AnnotationUtil;
import org.apache.safeguard.impl.util.NamingUtil;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.apache.safeguard.impl.executionPlans.MicroprofileAnnotationMapper.mapCircuitBreaker;
import static org.apache.safeguard.impl.executionPlans.MicroprofileAnnotationMapper.mapRetry;

public class ExecutionPlanFactory {
    private final FailsafeCircuitBreakerManager circuitBreakerManager;
    private final FailsafeRetryManager retryManager;
    private Map<String, ExecutionPlan> executionPlanMap = new HashMap<>();

    public ExecutionPlanFactory(FailsafeCircuitBreakerManager circuitBreakerManager, FailsafeRetryManager retryManager) {
        this.circuitBreakerManager = circuitBreakerManager;
        this.retryManager = retryManager;
    }

    public ExecutionPlan locateExecutionPlan(String name, Duration timeout, boolean async) {
        return executionPlanMap.computeIfAbsent(name, key -> {
            FailsafeCircuitBreaker circuitBreaker = circuitBreakerManager.getCircuitBreaker(key);
            FailsafeRetryDefinition retryDefinition = retryManager.getRetryDefinition(key);
            if (circuitBreaker == null && retryDefinition == null) {
                return null;
            } else {
                return new SyncFailsafeExecutionPlan(retryDefinition, circuitBreaker);
            }
        });
    }

    public ExecutionPlan locateExecutionPlan(Method method) {
        final String name = NamingUtil.createName(method);
        return executionPlanMap.computeIfAbsent(name, key -> {
            FailsafeCircuitBreaker circuitBreaker = circuitBreakerManager.getCircuitBreaker(key);
            if (circuitBreaker == null) {
                circuitBreaker = createCBDefinition(name, method);
            }
            FailsafeRetryDefinition retryDefinition = retryManager.getRetryDefinition(key);
            if (retryDefinition == null) {
                retryDefinition = createDefinition(name, method);
            }
            boolean isAsync = isAsync(method);
            Duration timeout = readTimeout(method);
            if(circuitBreaker == null && retryDefinition == null && isAsync) {
                if(timeout == null) {
                    return new AsyncOnlyExecutionPlan(null);
                }
                else {
                    return new AsyncTimeoutExecutionPlan(timeout, Executors.newFixedThreadPool(5));
                }
            }
            else if(circuitBreaker == null && retryDefinition == null && timeout != null) {
                // then its just timeout
                return new AsyncTimeoutExecutionPlan(timeout, Executors.newFixedThreadPool(5));
            }
            else {
                if (isAsync || timeout != null) {
                    return new AsyncFailsafeExecutionPlan(retryDefinition, circuitBreaker, Executors.newScheduledThreadPool(5), timeout);
                } else {
                    return new SyncFailsafeExecutionPlan(retryDefinition, circuitBreaker);
                }
            }
        });
    }

    private FailsafeRetryDefinition createDefinition(String name, Method method) {
        Retry retry = AnnotationUtil.getAnnotation(method, Retry.class);
        if (retry == null) {
            return null;
        }
        FailsafeRetryBuilder retryBuilder = retryManager.newRetryDefinition(name);
        return mapRetry(retry, retryBuilder);
    }

    private FailsafeCircuitBreaker createCBDefinition(String name, Method method) {
        CircuitBreaker circuitBreaker = AnnotationUtil.getAnnotation(method, CircuitBreaker.class);
        if (circuitBreaker == null) {
            return null;
        }
        FailsafeCircuitBreakerBuilder circuitBreakerBuilder = this.circuitBreakerManager.newCircuitBreaker(name);
        return new FailsafeCircuitBreaker(mapCircuitBreaker(circuitBreaker, circuitBreakerBuilder));
    }

    private boolean isAsync(Method method) {
        return AnnotationUtil.getAnnotation(method, Asynchronous.class) != null;
    }

    private Duration readTimeout(Method method) {
        Timeout timeout = AnnotationUtil.getAnnotation(method, Timeout.class);
        if(timeout == null) {
            return null;
        }
        return Duration.of(timeout.value(), timeout.unit());
    }
}
