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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.annotation.AnnotationFinder;
import org.apache.safeguard.impl.asynchronous.BaseAsynchronousInterceptor;
import org.apache.safeguard.impl.interceptor.IdGeneratorInterceptor;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

// todo: metrics
@Bulkhead
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER + 5)
public class BulkheadInterceptor extends BaseAsynchronousInterceptor {
    private static final String EXECUTOR_KEY = BulkheadInterceptor.class.getName() + ".executor";

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
            }
        }
        if (model.useThreads) {
            context.getContextData().put(
                    EXECUTOR_KEY + context.getContextData().get(IdGeneratorInterceptor.class.getName()), model.pool);
            return around(context);
        } else {
            if (!model.semaphore.tryAcquire()) {
                throw new BulkheadException("No more permission available");
            }
            try {
                return context.proceed();
            } finally {
                model.semaphore.release();
            }
        }
    }

    @Override
    protected Executor getExecutor(final InvocationContext context) {
        return Executor.class.cast(context.getContextData()
                  .get(EXECUTOR_KEY + context.getContextData().get(IdGeneratorInterceptor.class.getName())));
    }

    static class Model {
        private final int value;
        private final int waitingQueue;
        private final boolean useThreads;
        private final ThreadPoolExecutor pool;
        private final Semaphore semaphore;

        private Model(final Bulkhead bulkhead, final boolean useThreads) {
            this.value = bulkhead.value();
            if (this.value <= 0) {
                throw new FaultToleranceDefinitionException("Invalid value in @Bulkhead: " + value);
            }

            this.waitingQueue = bulkhead.waitingTaskQueue();
            if (this.waitingQueue <= 0) {
                throw new FaultToleranceDefinitionException("Invalid value in @Bulkhead: " + value);
            }

            this.useThreads = useThreads;
            if (this.useThreads) {
                this.pool = new ThreadPoolExecutor(value, value, 0L, MILLISECONDS, new ArrayBlockingQueue<>(waitingQueue));
                this.pool.setRejectedExecutionHandler((r, executor) -> {
                    throw new BulkheadException("Can't accept task " + r);
                });
                this.semaphore = null;
            } else {
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

        @PreDestroy
        private void destroy() {
            models.values().stream().filter(m -> m.pool != null).forEach(m -> m.pool.shutdownNow());
        }

        public Map<Method, Model> getModels() {
            return models;
        }

        public Model create(final InvocationContext context) {
            return new Model(finder.findAnnotation(Bulkhead.class, context),
                    finder.findAnnotation(Asynchronous.class, context) != null);
        }
    }
}
