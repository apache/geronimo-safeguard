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
import org.apache.safeguard.impl.cache.Key;
import org.apache.safeguard.impl.cache.UnwrappedCache;
import org.apache.safeguard.impl.config.ConfigurationMapper;
import org.apache.safeguard.impl.customizable.Safeguard;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
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
        final Map<Key, Model> timeouts = cache.getTimeouts();
        final Key key = new Key(context, cache.getUnwrappedCache().getUnwrappedCache());
        Model model = timeouts.get(key);
        if (model == null) {
            model = cache.create(context);
            timeouts.putIfAbsent(key, model);
        }
        if (model.disabled) {
            return context.proceed();
        }

        final FutureTask<Object> task = new FutureTask<>(context::proceed);
        final long start = System.nanoTime();
        executor.execute(task);
        try {
            final Object result = task.get(model.timeout, NANOSECONDS);
            model.successes.inc();
            return result;
        } catch (final ExecutionException ee) {
            cancel(task);
            throw toCause(ee);
        } catch (final TimeoutException te) {
            model.timeouts.inc();
            cancel(task);
            throw new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException(te);
        } finally {
            final long end = System.nanoTime();
            model.executionDuration.update(end - start);
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

    private static class Model {
        private final boolean disabled;
        private final long timeout;
        private final FaultToleranceMetrics.Histogram executionDuration;
        private final FaultToleranceMetrics.Counter timeouts;
        private final FaultToleranceMetrics.Counter successes;

        private Model(final boolean disabled, final long timeout, final FaultToleranceMetrics.Histogram executionDuration,
                      final FaultToleranceMetrics.Counter timeouts, final FaultToleranceMetrics.Counter successes) {
            this.disabled = disabled;
            this.timeout = timeout;
            this.executionDuration = executionDuration;
            this.timeouts = timeouts;
            this.successes = successes;
        }
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Key, Model> timeouts = new ConcurrentHashMap<>();

        @Inject
        private AnnotationFinder finder;

        @Inject
        private FaultToleranceMetrics metrics;

        @Inject
        private ConfigurationMapper mapper;

        @Inject
        private UnwrappedCache unwrappedCache;

        public UnwrappedCache getUnwrappedCache() {
            return unwrappedCache;
        }

        public Map<Key, Model> getTimeouts() {
            return timeouts;
        }

        public Model create(final InvocationContext context) {
            final Timeout timeout = mapper.map(finder.findAnnotation(Timeout.class, context), context.getMethod(), Timeout.class);
            if (timeout.value() < 0) {
                throw new FaultToleranceDefinitionException("Timeout can't be < 0: " + context.getMethod());
            }
            final String metricsNameBase = "ft." + context.getMethod().getDeclaringClass().getCanonicalName() + "." +
                    context.getMethod().getName() + ".timeout.";
            return new Model(
                    !mapper.isEnabled(context.getMethod(), Timeout.class),
                    timeout.unit().getDuration().toNanos() * timeout.value(),
                    metrics.histogram(metricsNameBase + "executionDuration", "Histogram of execution times for the method"),
                    metrics.counter(metricsNameBase + "callsTimedOut.total", "The number of times the method timed out"),
                    metrics.counter(metricsNameBase + "callsNotTimedOut.total", "The number of times the method completed without timing out"));
        }
    }
}
