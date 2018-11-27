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
package org.apache.safeguard.impl.bulkhead;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.annotation.AnnotationFinder;
import org.apache.safeguard.impl.asynchronous.BaseAsynchronousInterceptor;
import org.apache.safeguard.impl.config.ConfigurationMapper;
import org.apache.safeguard.impl.interceptor.IdGeneratorInterceptor;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

@Bulkhead
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER + 5)
public class BulkheadInterceptor extends BaseAsynchronousInterceptor {
    @Inject
    private Cache cache;

    @AroundInvoke
    public Object bulkhead(final InvocationContext context) throws Exception {
        final Map<Method, Model> models = cache.getModels();
        Model model = models.get(context.getMethod());
        if (model == null) {
            model = cache.create(context);
            final Model existing = models.putIfAbsent(context.getMethod(), model);
            if (existing != null) {
                model = existing;
            } else {
                cache.postCreate(model, context);
            }
        }
        if (model.disabled) {
            return context.proceed();
        }
        if (model.useThreads) {
            final Map<String, Object> data = context.getContextData();
            final Object id = data.get(IdGeneratorInterceptor.class.getName());
            data.put(BulkheadInterceptor.class.getName() + ".model_" + id, model);
            data.put(BulkheadInterceptor.class.getName() + "_" + id, model.pool);
            data.put(Asynchronous.class.getName() + ".skip_" + id, Boolean.TRUE);
            return around(context);
        } else {
            if (!model.semaphore.tryAcquire()) {
                model.callsRejected.inc();
                throw new BulkheadException("No more permission available");
            }
            model.callsAccepted.inc();
            model.concurrentCalls.incrementAndGet();
            final long start = System.nanoTime();
            try {
                return context.proceed();
            } finally {
                model.executionDuration.update(System.nanoTime() - start);
                model.semaphore.release();
                model.concurrentCalls.decrementAndGet();
            }
        }
    }

    private Model getModel(final InvocationContext context) {
        return Model.class.cast(context.getContextData().get(
                BulkheadInterceptor.class.getName() + ".model_" +
                context.getContextData().get(IdGeneratorInterceptor.class.getName())));
    }

    @Override
    protected FutureWrapper<Object> newFuture(final InvocationContext context, final Map<String, Object> data) {
        return new ContextualFutureWrapper<>(getModel(context), context.getContextData());
    }

    @Override
    protected ExtendedCompletableFuture<Object> newCompletableFuture(final InvocationContext context) {
        return new ContextualCompletableFuture<>(getModel(context));
    }

    @Override
    protected Executor getExecutor(final InvocationContext context) {
        return Executor.class.cast(context.getContextData()
                  .get(BulkheadInterceptor.class.getName() + "_" + context.getContextData().get(IdGeneratorInterceptor.class.getName())));
    }

    private static class ContextualCompletableFuture<T> extends ExtendedCompletableFuture<T> {
        private final Model model;

        private ContextualCompletableFuture(final Model model) {
            this.model = model;
        }

        @Override
        public void before() {
            model.concurrentCalls.incrementAndGet();
        }

        @Override
        public void after() {
            model.concurrentCalls.decrementAndGet();
        }
    }

    private static class ContextualFutureWrapper<T> extends FutureWrapper<T> {
        private final Model model;

        private ContextualFutureWrapper(final Model model, final Map<String, Object> data) {
            super(data);
            this.model = model;
        }

        @Override
        public void before() {
            model.concurrentCalls.incrementAndGet();
        }

        @Override
        public void after() {
            model.concurrentCalls.decrementAndGet();
        }
    }

    static class Model {
        private final boolean disabled;
        private final int value;
        private final int waitingQueue;
        private final boolean useThreads;
        private final ThreadPoolExecutor pool;
        private final Semaphore semaphore;
        private final AtomicLong concurrentCalls = new AtomicLong();
        private final ArrayBlockingQueue<Runnable> workQueue;
        private final FaultToleranceMetrics.Counter callsAccepted;
        private final FaultToleranceMetrics.Counter callsRejected;
        private final FaultToleranceMetrics.Histogram executionDuration;
        private final FaultToleranceMetrics.Histogram waitingDuration;

        private Model(final boolean disabled, final InvocationContext context,
                      final Bulkhead bulkhead, final boolean useThreads,
                      final FaultToleranceMetrics.Counter callsAccepted,
                      final FaultToleranceMetrics.Counter callsRejected,
                      final FaultToleranceMetrics.Histogram executionDuration,
                      final FaultToleranceMetrics.Histogram waitingDuration) {
            this.disabled = disabled;
            this.value = bulkhead.value();
            if (this.value <= 0) {
                throw new FaultToleranceDefinitionException("Invalid value in @Bulkhead: " + value);
            }

            this.waitingQueue = bulkhead.waitingTaskQueue();
            if (this.waitingQueue <= 0) {
                throw new FaultToleranceDefinitionException("Invalid value in @Bulkhead: " + value);
            }

            this.callsAccepted = callsAccepted;
            this.callsRejected = callsRejected;
            this.executionDuration = executionDuration;
            this.waitingDuration = waitingDuration;

            this.useThreads = useThreads;
            if (this.useThreads) { // important: use a pool dedicated for that concern and not a reusable one
                this.workQueue = new ArrayBlockingQueue<>(waitingQueue);
                this.pool = new ThreadPoolExecutor(value, value, 0L, MILLISECONDS, workQueue, new ThreadFactory() {
                    private final ThreadGroup group = ofNullable(System.getSecurityManager())
                            .map(SecurityManager::getThreadGroup)
                            .orElseGet(() -> Thread.currentThread().getThreadGroup());
                    private final String prefix = "org.apache.geronimo.safeguard.bulkhead@" +
                            System.identityHashCode(this) + "[" + context.getMethod() + "]-";
                    private final AtomicLong counter = new AtomicLong();

                    @Override
                    public Thread newThread(final Runnable r) {
                        return new Thread(group, r, prefix + counter.incrementAndGet());
                    }
                }, (r, executor) -> {
                    callsRejected.inc();
                    throw new BulkheadException("Can't accept task " + r);
                }) {
                    @Override
                    public void execute(final Runnable command) {
                        final long submitted = System.nanoTime();
                        super.execute(() -> {
                            final long start = System.nanoTime();
                            waitingDuration.update(start - submitted);
                            try {
                                command.run();
                            } finally {
                                executionDuration.update(System.nanoTime() - start);
                            }
                        });
                        callsAccepted.inc();
                    }
                };
                this.semaphore = null;
            } else {
                this.workQueue = null;
                this.pool = null;
                this.semaphore = new Semaphore(value);
            }
        }
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Method, Model> models = new ConcurrentHashMap<>();

        @Inject
        private AnnotationFinder finder;

        @Inject
        private FaultToleranceMetrics metrics;

        @Inject
        private ConfigurationMapper configurationMapper;

        @PreDestroy
        private void destroy() {
            models.values().stream().filter(m -> m.pool != null).forEach(m -> m.pool.shutdownNow());
        }

        public Map<Method, Model> getModels() {
            return models;
        }

        public Model create(final InvocationContext context) {
            final boolean useThreads = finder.findAnnotation(Asynchronous.class, context) != null;

            final String metricsNameBase = getMetricsNameBase(context);
            final FaultToleranceMetrics.Counter callsAccepted = metrics.counter(metricsNameBase + "callsAccepted.total",
                    "Number of calls accepted by the bulkhead");
            final FaultToleranceMetrics.Counter callsRejected = metrics.counter(metricsNameBase + "callsRejected.total",
                    "Number of calls rejected by the bulkhead");
            final FaultToleranceMetrics.Histogram executionDuration = metrics.histogram(metricsNameBase + "executionDuration",
                    "Histogram of method execution times. This does not include any time spent waiting in the bulkhead queue.");
            final FaultToleranceMetrics.Histogram waitingDuration;
            if (useThreads) {
                waitingDuration = metrics.histogram(metricsNameBase + "waiting.duration",
                        "Histogram of the time executions spend waiting in the queue");
            } else {
                waitingDuration = null;
            }

            return new Model(
                    !configurationMapper.isEnabled(context.getMethod(), Bulkhead.class), context,
                    configurationMapper.map(finder.findAnnotation(Bulkhead.class, context), context.getMethod(), Bulkhead.class),
                    useThreads, callsAccepted, callsRejected, executionDuration, waitingDuration);
        }

        private String getMetricsNameBase(InvocationContext context) {
            return "ft." + context.getMethod().getDeclaringClass().getCanonicalName() + "."
                    + context.getMethod().getName() + ".bulkhead.";
        }

        public void postCreate(final Model model, final InvocationContext context) {
            final String metricsNameBase = getMetricsNameBase(context);
            metrics.gauge(metricsNameBase + "concurrentExecutions", "Number of currently running executions",
                    "none", model.concurrentCalls::get);
            if (model.workQueue != null) {
                metrics.gauge(metricsNameBase + "waitingQueue.population",
                        "Number of executions currently waiting in the queue", "none", () -> (long) model.workQueue.size());
            }
        }
    }
}
