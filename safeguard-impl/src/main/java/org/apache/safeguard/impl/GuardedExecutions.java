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

import net.jodah.failsafe.Failsafe;
import org.apache.safeguard.impl.retry.FailsafeRetryBuilder;
import org.apache.safeguard.impl.retry.FailsafeRetryDefinition;
import org.apache.safeguard.impl.retry.FailsafeRetryManager;
import org.apache.safeguard.impl.util.AnnotationUtil;
import org.apache.safeguard.impl.util.NamingUtil;
import org.eclipse.microprofile.faulttolerance.Retry;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;

@ApplicationScoped
public class GuardedExecutions {
    private FailsafeRetryManager retryManager;

    GuardedExecutions() {

    }

    @Inject
    public GuardedExecutions(FailsafeRetryManager retryManager) {
        this.retryManager = retryManager;
    }

    public Object execute(InvocationContext invocationContext) {
        Method method = invocationContext.getMethod();
        String name = NamingUtil.createName(method);
        FailsafeRetryDefinition failsafeRetryDefinition = retryManager.getRetryDefinition(name);
        if (failsafeRetryDefinition == null) {
            failsafeRetryDefinition = createDefinition(name, method);
        }
        return Failsafe.with(failsafeRetryDefinition.getRetryPolicy()).get(invocationContext::proceed);
    }

    public <T> T execute(String name, Callable<T> callable) {
        FailsafeRetryDefinition failsafeRetryDefinition = retryManager.getRetryDefinition(name);
        return Failsafe.with(failsafeRetryDefinition.getRetryPolicy()).get(callable);
    }

    private FailsafeRetryDefinition createDefinition(String name, Method method) {
        Retry retry = AnnotationUtil.getAnnotation(method, Retry.class);
        FailsafeRetryBuilder retryBuilder = retryManager.newRetryDefinition(name)
                .withMaxRetries(retry.maxRetries())
                .withRetryOn(retry.retryOn())
                .withAbortOn(retry.abortOn());
        if (retry.delay() > 0L) {
            retryBuilder.withDelay(Duration.of(retry.delay(), retry.delayUnit()));
        }
        if (retry.jitter() > 0L) {
            retryBuilder.withJitter(Duration.of(retry.jitter(), retry.jitterDelayUnit()));
        }
        if(retry.maxDuration() > 0L) {
            retryBuilder.withMaxDuration(Duration.of(retry.maxDuration(), retry.durationUnit()));
        }
        return retryBuilder.build();
    }
}
