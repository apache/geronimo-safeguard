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
import org.apache.safeguard.impl.GuardedExecutions;
import org.apache.safeguard.impl.util.AnnotationUtil;
import org.eclipse.microprofile.faulttolerance.Retry;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;

@Interceptor
@SafeguardEnabled
@Priority(400)
public class SafeguardInterceptor {
    @Inject
    private GuardedExecutions guardedExecutions;

    @AroundInvoke
    public Object runSafeguards(InvocationContext invocationContext) throws Exception{
        if(isMethodSafeguarded(invocationContext.getMethod())) {
            return guardedExecutions.execute(invocationContext);
        }
        else {
            return invocationContext.proceed();
        }
    }

    private boolean isMethodSafeguarded(Method method) {
        return AnnotationUtil.getAnnotation(method, Retry.class) != null;
    }
}
