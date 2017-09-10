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

import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerManager;
import org.apache.safeguard.impl.retry.FailsafeRetryManager;

import javax.enterprise.inject.Vetoed;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

@Vetoed
public class FailsafeExecutionManager {
    private FailsafeCircuitBreakerManager circuitBreakerManager;
    private FailsafeRetryManager retryManager;
    private ExecutionPlanFactory executionPlanFactory;

    public FailsafeExecutionManager() {
        this.circuitBreakerManager = new FailsafeCircuitBreakerManager();
        this.retryManager = new FailsafeRetryManager();
        this.executionPlanFactory = new ExecutionPlanFactory(this.circuitBreakerManager, this.retryManager);
    }

    public Object execute(InvocationContext invocationContext) {
        Method method = invocationContext.getMethod();
        return executionPlanFactory.locateExecutionPlan(method).execute(invocationContext::proceed);
//        String name = NamingUtil.createName(method);
//        FailsafeRetryDefinition failsafeRetryDefinition = retryManager.getRetryDefinition(name);
//        if (failsafeRetryDefinition == null) {
//            failsafeRetryDefinition = createDefinition(name, method);
//        }
//        return Failsafe.with(failsafeRetryDefinition.getRetryPolicy()).get(invocationContext::proceed);
    }

    public <T> T execute(String name, Callable<T> callable) {
        return executionPlanFactory.locateExecutionPlan(name).execute(callable);
//        FailsafeRetryDefinition failsafeRetryDefinition = retryManager.getRetryDefinition(name);
//        return Failsafe.with(failsafeRetryDefinition.getRetryPolicy()).get(callable);
    }

    public ExecutionPlanFactory getExecutionPlanFactory() {
        return executionPlanFactory;
    }

    public FailsafeCircuitBreakerManager getCircuitBreakerManager() {
        return circuitBreakerManager;
    }

    public FailsafeRetryManager getRetryManager() {
        return retryManager;
    }

    //    private FailsafeRetryDefinition createDefinition(String name, Method method) {
//        Retry retry = AnnotationUtil.getAnnotation(method, Retry.class);
//        if(retry == null) {
//            return null;
//        }
//        FailsafeRetryBuilder retryBuilder = retryManager.newRetryDefinition(name);
//        return mapRetry(retry, retryBuilder);
//    }

//    private FailsafeCircuitBreakerDefinition createCBDefinition(String name, Method method) {
//        CircuitBreaker circuitBreaker = AnnotationUtil.getAnnotation(method, CircuitBreaker.class);
//        if (circuitBreaker == null) {
//            return null;
//        }
//        FailsafeCircuitBreakerBuilder circuitBreakerBuilder = this.circuitBreakerManager.newCircuitBreaker(name);
//        return mapCircuitBreaker(circuitBreaker, circuitBreakerBuilder);
//    }
}
