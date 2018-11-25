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
package org.apache.safeguard.impl.retry;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.annotation.AnnotationFinder;
import org.apache.safeguard.impl.config.ConfigurationMapper;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;

public class BaseRetryInterceptor implements Serializable {
    @Inject
    private Cache cache;

    @AroundInvoke
    public Object retry(final InvocationContext context) throws Exception {
        final Map<Method, Model> models = cache.getModels();
        Model model = models.get(context.getMethod());
        if (model == null) {
            model = cache.create(context);
            models.putIfAbsent(context.getMethod(), model);
        }
        for (int i = 0; i < model.maxRetries + 1; i++) {
            try {
                return context.proceed();
            } catch (final RuntimeException re) {
                if (model.abortOn(re)) {
                    throw new FaultToleranceException(re);
                }
                if (model.maxRetries == i) {
                    throw re;
                }
                if (model.retryOn(re)) {
                    Thread.sleep(model.nextPause());
                }
            }
        }
        throw new IllegalStateException("Inaccessible but needed to compile");
    }

    static class Model {
        private final Class<? extends Throwable>[] abortOn;
        private final Class<? extends Throwable>[] retryOn;
        private final long maxDuration;
        private final int maxRetries;
        private final long delay;
        private final long jitter;

        private Model(final Retry retry) {
            abortOn = retry.abortOn();
            retryOn = retry.retryOn();
            maxDuration = retry.delayUnit().getDuration().toNanos() * retry.maxDuration();
            maxRetries = retry.maxRetries();
            delay = retry.delayUnit().getDuration().toNanos() * retry.delay();
            jitter = retry.jitterDelayUnit().getDuration().toNanos() * retry.jitter();
        }

        private boolean abortOn(final RuntimeException re) {
            return matches(abortOn, re);
        }

        private boolean retryOn(final RuntimeException re) {
            return matches(retryOn, re);
        }

        private boolean matches(final Class<? extends Throwable>[] list, final RuntimeException re) {
            return list.length > 0 &&
                    Stream.of(list).anyMatch(it -> it.isInstance(re) || it.isInstance(re.getCause()));
        }

        private long nextPause() {
            final ThreadLocalRandom random = ThreadLocalRandom.current();
            return TimeUnit.NANOSECONDS.toMillis(
                    min(maxDuration, max(0, ((random.nextBoolean() ? 1 : -1) * delay) + random.nextLong(jitter))));
        }
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Method, Model> models = new ConcurrentHashMap<>();

        @Inject
        private AnnotationFinder finder;

        @Inject
        private ConfigurationMapper configurationMapper;

        public Map<Method, Model> getModels() {
            return models;
        }

        public Model create(final InvocationContext context) {
            final Retry retry = finder.findAnnotation(Retry.class, context);
            final Retry configuredRetry = configurationMapper.map(retry, context.getMethod(), Retry.class);
            return new Model(configuredRetry);
        }
    }
}
