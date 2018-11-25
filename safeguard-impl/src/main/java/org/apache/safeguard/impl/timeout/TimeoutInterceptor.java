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
package org.apache.safeguard.impl.timeout;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.annotation.AnnotationFinder;
import org.apache.safeguard.impl.customizable.Safeguard;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

@Timeout
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER + 20)
public class TimeoutInterceptor implements Serializable {
    @Inject
    private Cache cache;

    @Inject
    @Safeguard
    private Executor executor;

    @AroundInvoke
    public Object withTimeout(final InvocationContext context) throws Exception {
        final Map<Method, Long> timeouts = cache.getTimeouts();
        Long duration = timeouts.get(context.getMethod());
        if (duration == null) {
            duration = cache.create(context);
            timeouts.putIfAbsent(context.getMethod(), duration);
        }
        final FutureTask<Object> task = new FutureTask<>(context::proceed);
        executor.execute(task);
        try {
            return task.get(duration, NANOSECONDS);
        } catch (final ExecutionException ee) {
            cancel(task);
            throw toCause(ee);
        } catch (final TimeoutException te) {
            cancel(task);
            throw new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException(te);
        }
    }

    private void cancel(final FutureTask<Object> task) {
        if (!task.isDone()) {
            task.cancel(true);
        }
    }

    private Exception toCause(final Exception te) throws Exception {
        final Throwable cause = te.getCause();
        if (Exception.class.isInstance(cause)) {
            throw Exception.class.cast(cause);
        }
        if (Error.class.isInstance(cause)) {
            throw Error.class.cast(cause);
        }
        throw te;
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Method, Long> timeouts = new ConcurrentHashMap<>();

        @Inject
        private AnnotationFinder finder;

        public Map<Method, Long> getTimeouts() {
            return timeouts;
        }

        public long create(final InvocationContext context) {
            final Timeout timeout = finder.findAnnotation(Timeout.class, context);
            if (timeout.value() < 0) {
                throw new FaultToleranceDefinitionException("Timeout can't be < 0: " + context.getMethod());
            }
            return timeout.unit().getDuration().toNanos() * timeout.value();
        }
    }
}
