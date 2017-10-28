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
import org.apache.safeguard.impl.bulkhead.BulkheadManagerImpl;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerManager;
import org.apache.safeguard.impl.executionPlans.ExecutionPlanFactory;
import org.apache.safeguard.impl.retry.FailsafeRetryManager;

import javax.enterprise.inject.Vetoed;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;

@Vetoed
public class FailsafeExecutionManager implements ExecutionManager {
    private BulkheadManagerImpl bulkheadManager;
    private FailsafeCircuitBreakerManager circuitBreakerManager;
    private FailsafeRetryManager retryManager;
    private ExecutionPlanFactory executionPlanFactory;

    public FailsafeExecutionManager() {
        this.circuitBreakerManager = new FailsafeCircuitBreakerManager();
        this.retryManager = new FailsafeRetryManager();
        this.bulkheadManager = new BulkheadManagerImpl();
        this.executionPlanFactory = new ExecutionPlanFactory(this.circuitBreakerManager, this.retryManager,
                this.bulkheadManager);
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
    public FailsafeCircuitBreakerManager getCircuitBreakerManager() {
        return circuitBreakerManager;
    }

    @Override
    public FailsafeRetryManager getRetryManager() {
        return retryManager;
    }

    @Override
    public BulkheadManager getBulkheadManager() {
        return bulkheadManager;
    }

}
