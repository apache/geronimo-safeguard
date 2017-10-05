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

package org.apache.safeguard.impl.fallback;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

import javax.enterprise.inject.spi.CDI;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class FallbackRunner {
    private final Class<? extends FallbackHandler<?>> handlerClass;
    private final String method;

    public FallbackRunner(Class<? extends FallbackHandler<?>> handlerClass, String method) {
        this.handlerClass = handlerClass;
        this.method = method;
    }

    public Object executeFallback(InvocationContext invocationContext) {
        if(method != null) {
            try {
                Method method = getMethod(invocationContext.getTarget().getClass());
                Parameter[] parameters = method.getParameters();
                if(parameters.length == 0) {
                    return method.invoke(invocationContext.getTarget());
                }
                else {
                    return method.invoke(invocationContext.getTarget(), invocationContext.getParameters());
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        else {
            SafeguardExecutionContext executionContext = new SafeguardExecutionContext(invocationContext.getMethod(),
                    invocationContext.getParameters());
            CDI<Object> cdi = CDI.current();
            FallbackHandler fallbackHandler = null;
            try {
                fallbackHandler = cdi.select(handlerClass).get();
            }
            catch (Exception e) {
                try {
                    fallbackHandler = handlerClass.newInstance();
                } catch (InstantiationException | IllegalAccessException e1) {
                    throw new IllegalArgumentException(e);
                }
            }
            return fallbackHandler.handle(executionContext);
        }
    }

    private Method getMethod(Class<?> aClass) {
        for(Method method : aClass.getMethods()) {
            if(method.getName().equals(this.method)) {
                return method;
            }
        }
        return null;
    }

    private static class SafeguardExecutionContext implements ExecutionContext {

        private final Method method;
        private final Object[] parameters;

        private SafeguardExecutionContext(Method method, Object[] parameters) {
            this.method = method;
            this.parameters = parameters;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getParameters() {
            return parameters;
        }
    }
}
