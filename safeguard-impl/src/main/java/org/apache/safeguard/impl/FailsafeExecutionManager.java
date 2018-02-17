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

package org.apache.safeguard.impl;

import org.apache.safeguard.api.ExecutionManager;
import org.apache.safeguard.api.bulkhead.BulkheadManager;
import org.apache.safeguard.api.circuitbreaker.CircuitBreakerManager;
import org.apache.safeguard.api.retry.RetryManager;
import org.apache.safeguard.impl.bulkhead.BulkheadManagerImpl;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerManager;
import org.apache.safeguard.impl.config.MicroprofileAnnotationMapper;
import org.apache.safeguard.impl.executionPlans.ExecutionPlanFactory;
import org.apache.safeguard.impl.executorService.DefaultExecutorServiceProvider;
import org.apache.safeguard.impl.executorService.ExecutorServiceProvider;
import org.apache.safeguard.impl.retry.FailsafeRetryManager;

import javax.enterprise.inject.Vetoed;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

@Vetoed
public class FailsafeExecutionManager implements ExecutionManager {
    private final MicroprofileAnnotationMapper mapper;
    private final BulkheadManager bulkheadManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private final RetryManager retryManager;
    private final ExecutionPlanFactory executionPlanFactory;
    private final ExecutorServiceProvider executorServiceProvider;

    public FailsafeExecutionManager() {
        FailsafeCircuitBreakerManager circuitBreakerManager = new FailsafeCircuitBreakerManager();
        FailsafeRetryManager retryManager = new FailsafeRetryManager();
        BulkheadManagerImpl bulkheadManager = new BulkheadManagerImpl();
        this.mapper = MicroprofileAnnotationMapper.getInstance();
        this.executorServiceProvider = new DefaultExecutorServiceProvider(Executors.newScheduledThreadPool(5));
        this.executionPlanFactory = new ExecutionPlanFactory(circuitBreakerManager, retryManager, bulkheadManager, mapper,
                executorServiceProvider);
        this.circuitBreakerManager = circuitBreakerManager;
        this.retryManager = retryManager;
        this.bulkheadManager = bulkheadManager;
    }

    public FailsafeExecutionManager(MicroprofileAnnotationMapper mapper, BulkheadManagerImpl bulkheadManager,
                                    FailsafeCircuitBreakerManager circuitBreakerManager, FailsafeRetryManager retryManager,
                                    ExecutionPlanFactory executionPlanFactory, ExecutorServiceProvider executorServiceProvider) {
        this.mapper = mapper;
        this.bulkheadManager = bulkheadManager;
        this.circuitBreakerManager = circuitBreakerManager;
        this.retryManager = retryManager;
        this.executionPlanFactory = executionPlanFactory;
        this.executorServiceProvider = executorServiceProvider;
    }

    public Object execute(InvocationContext invocationContext) {
        Method method = invocationContext.getMethod();
        return executionPlanFactory.locateExecutionPlan(method).execute(invocationContext::proceed, invocationContext);
    }

    @Override
    public <T> T execute(String name, Callable<T> callable) {
        return executionPlanFactory.locateExecutionPlan(name, null, false).execute(callable, null);
    }

    public <T> T executeAsync(String name, Callable<T> callable) {
        return executionPlanFactory.locateExecutionPlan(name, null, true).execute(callable, null);
    }

    public <T> T executeAsync(String name, Callable<T> callable, Duration timeout) {
        return executionPlanFactory.locateExecutionPlan(name, timeout, true).execute(callable, null);
    }

    public ExecutionPlanFactory getExecutionPlanFactory() {
        return executionPlanFactory;
    }

    @Override
    public CircuitBreakerManager getCircuitBreakerManager() {
        return circuitBreakerManager;
    }

    @Override
    public RetryManager getRetryManager() {
        return retryManager;
    }

    @Override
    public BulkheadManager getBulkheadManager() {
        return bulkheadManager;
    }

}
