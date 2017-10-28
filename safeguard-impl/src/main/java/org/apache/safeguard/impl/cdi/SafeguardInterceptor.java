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

package org.apache.safeguard.impl.cdi;

import org.apache.safeguard.api.SafeguardEnabled;
import org.apache.safeguard.impl.FailsafeExecutionManager;
import org.apache.safeguard.impl.util.AnnotationUtil;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;

@Interceptor
@SafeguardEnabled
@Priority(400)
@Dependent
public class SafeguardInterceptor {
    @Inject
    private FailsafeExecutionManager failsafeExecutionManager;

    @AroundInvoke
    public Object runSafeguards(InvocationContext invocationContext) throws Exception{
        if(isMethodSafeguarded(invocationContext.getMethod())) {
            return failsafeExecutionManager.execute(invocationContext);
        }
        else {
            return invocationContext.proceed();
        }
    }

    private boolean isMethodSafeguarded(Method method) {
        return AnnotationUtil.getAnnotation(method, Retry.class) != null ||
                AnnotationUtil.getAnnotation(method, CircuitBreaker.class) != null ||
                AnnotationUtil.getAnnotation(method, Timeout.class) != null ||
                AnnotationUtil.getAnnotation(method, Fallback.class) != null ||
                AnnotationUtil.getAnnotation(method, Bulkhead.class) != null;
    }
}
