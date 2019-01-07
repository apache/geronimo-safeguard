/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.safeguard.impl.retry;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.annotation.AnnotationFinder;
import org.apache.safeguard.impl.asynchronous.BaseAsynchronousInterceptor;
import org.apache.safeguard.impl.cache.Key;
import org.apache.safeguard.impl.cache.UnwrappedCache;
import org.apache.safeguard.impl.config.ConfigurationMapper;
import org.apache.safeguard.impl.interceptor.IdGeneratorInterceptor;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;

public abstract class BaseRetryInterceptor implements Serializable {

    @Inject
    private Cache cache;

    @AroundInvoke
    public Object retry(final InvocationContext context) throws Exception {
        final Map<Key, Model> models = cache.getModels();
        final Key cacheKey = new Key(context, cache.getUnwrapped());
        Model model = models.get(cacheKey);
        if (model == null) {
            model = cache.create(context);
            final Model existing = models.putIfAbsent(cacheKey, model);
            if (existing != null) {
                model = existing;
            }
        }
        if (model.disabled) {
            return context.proceed();
        }
        final Map<String, Object> contextData = context.getContextData();
        final Object id = contextData.get(IdGeneratorInterceptor.class.getName());
        final String contextKey = BaseRetryInterceptor.class.getName() + ".context_" + id;
        Context retryContext = Context.class.cast(contextData.get(contextKey));
        if (retryContext == null) {
            retryContext = new Context(System.nanoTime() + model.maxDuration, model.maxRetries);
            contextData.put(contextKey, retryContext);
        }

        while (retryContext.counter >= 0) { // todo: handle async if result is a Future or CompletionStage (weird no?)
            try {
                final Object proceed = context.proceed();
                if (retryContext.counter == model.maxRetries) {
                    executeFinalCounterAction(contextData, model.callsSucceededNotRetried);
                } else {
                    executeFinalCounterAction(contextData, model.callsSucceededRetried);
                }
                if (BaseAsynchronousInterceptor.BaseFuture.class.isInstance(proceed)) {
                    final Model modelRef = model;
                    contextData.put(BaseAsynchronousInterceptor.BaseFuture.class.getName() + ".errorHandler_" + id,
                        (BaseAsynchronousInterceptor.ErrorHandler<Exception, Future<?>>) error -> {
                            handleException(contextData, contextKey, modelRef, error);
                            return Future.class.cast(context.proceed());
                        });
                }
                return proceed;
            } catch (final Exception re) {
                retryContext = handleException(contextData, contextKey, model, re);
            }
        }
        throw new FaultToleranceException("Inaccessible normally, here for compilation");
    }

    private Context handleException(final Map<String, Object> contextData, final String contextKey,
                                    final Model modelRef, final Exception error) throws Exception {
        if (CircuitBreakerOpenException.class.isInstance(error)) {
            throw error;
        }

        // refresh the counter from the other interceptors
        final Context ctx = Context.class.cast(contextData.get(contextKey));

        if (modelRef.abortOn(error) || (--ctx.counter) < 0 || System.nanoTime() >= ctx.maxEnd) {
            executeFinalCounterAction(contextData, modelRef.callsFailed);
            throw error;
        }
        if (!modelRef.retryOn(error)) {
            throw error;
        }
        modelRef.retries.inc();
        final long pause = modelRef.nextPause();
        if (pause > 0) {
            Thread.sleep(pause);
        }
        return ctx;
    }

    protected abstract void executeFinalCounterAction(Map<String, Object> contextData, FaultToleranceMetrics.Counter counter);

    static class Model {

        private final Class<? extends Throwable>[] abortOn;

        private final Class<? extends Throwable>[] retryOn;

        private final long maxDuration;

        private final int maxRetries;

        private final long delay;

        private final long jitter;

        private final FaultToleranceMetrics.Counter callsSucceededNotRetried;

        private final FaultToleranceMetrics.Counter callsSucceededRetried;

        private final FaultToleranceMetrics.Counter callsFailed;

        private final FaultToleranceMetrics.Counter retries;

        private final boolean disabled;

        private Model(final boolean disabled,
                      final Retry retry, final FaultToleranceMetrics.Counter callsSucceededNotRetried,
                      final FaultToleranceMetrics.Counter callsSucceededRetried, final FaultToleranceMetrics.Counter callsFailed,
                      final FaultToleranceMetrics.Counter retries) {
            this.disabled = disabled;
            this.abortOn = retry.abortOn();
            this.retryOn = retry.retryOn();
            this.maxDuration = retry.delayUnit().getDuration().toNanos() * retry.maxDuration();
            this.maxRetries = retry.maxRetries();
            this.delay = retry.delayUnit().getDuration().toNanos() * retry.delay();
            this.jitter = retry.jitterDelayUnit().getDuration().toNanos() * retry.jitter();
            this.callsSucceededNotRetried = callsSucceededNotRetried;
            this.callsSucceededRetried = callsSucceededRetried;
            this.callsFailed = callsFailed;
            this.retries = retries;

            if (maxRetries < 0) {
                throw new FaultToleranceDefinitionException("max retries can't be negative");
            }
            if (delay < 0) {
                throw new FaultToleranceDefinitionException("delay can't be negative");
            }
            if (maxDuration < 0) {
                throw new FaultToleranceDefinitionException("max duration can't be negative");
            }
            if (jitter < 0) {
                throw new FaultToleranceDefinitionException("jitter can't be negative");
            }
            if (delay > maxDuration) {
                throw new FaultToleranceDefinitionException("delay can't be < max duration");
            }
        }

        private boolean abortOn(final Exception re) {
            return matches(abortOn, re);
        }

        private boolean retryOn(final Exception re) {
            return matches(retryOn, re);
        }

        private boolean matches(final Class<? extends Throwable>[] list, final Exception re) {
            return list.length > 0 && Stream.of(list).anyMatch(it -> it.isInstance(re) || it.isInstance(re.getCause()));
        }

        private long nextPause() {
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            return TimeUnit.NANOSECONDS
                    .toMillis(min(maxDuration, max(0, ((random.nextBoolean() ? 1 : -1) * delay) + (jitter == 0 ? 0 : random.nextLong(jitter)))));
        }
    }

    @ApplicationScoped
    public static class Cache {

        private final Map<Key, Model> models = new ConcurrentHashMap<>();

        @Inject
        private UnwrappedCache unwrappedCache;

        @Inject
        private AnnotationFinder finder;

        @Inject
        private ConfigurationMapper configurationMapper;

        @Inject
        private FaultToleranceMetrics metrics;

        public Map<Key, Model> getModels() {
            return models;
        }

        public Model create(final InvocationContext context) {
            final Retry retry = finder.findAnnotation(Retry.class, context);
            final Retry configuredRetry = configurationMapper.map(retry, context.getMethod(), Retry.class);
            final String metricsNameBase = "ft." + context.getMethod().getDeclaringClass().getCanonicalName() + "."
                    + context.getMethod().getName() + ".retry.";
            return new Model(
                    !configurationMapper.isEnabled(context.getMethod(), Retry.class),
                    configuredRetry,
                    metrics.counter(metricsNameBase + "callsSucceededNotRetried.total",
                            "The number of times the method was called and succeeded without retrying"),
                    metrics.counter(metricsNameBase + "callsSucceededRetried.total",
                            "The number of times the method was called and succeeded after retrying at least once"),
                    metrics.counter(metricsNameBase + "callsFailed.total",
                            "The number of times the method was called and ultimately failed after retrying"),
                    metrics.counter(metricsNameBase + "retries.total", "The total number of times the method was retried"));
        }

        public Map<Class<?>, Optional<Class<?>>> getUnwrapped() {
            return unwrappedCache.getUnwrappedCache();
        }
    }

    private static class Context {
        private final long maxEnd;
        private int counter;

        private Context(final long maxEnd, final int maxRetries) {
            this.maxEnd = maxEnd;
            this.counter = maxRetries;
        }
    }
}
