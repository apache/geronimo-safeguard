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

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.annotation.AnnotationFinder;
import org.apache.safeguard.impl.cdi.SafeguardExtension;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

// @Fallback - added through the extension since the @Target doesnt allow it
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER)
public class FallbackInterceptor implements Serializable {
    @Inject
    private Cache cache;

    @AroundInvoke
    public Object withFallback(final InvocationContext context) {
        final Map<Method, FallbackHandler<?>> handlers = cache.getHandlers();
        FallbackHandler<?> handler = handlers.get(context.getMethod());
        if (handler == null) {
            handler = cache.create(context);
            handlers.putIfAbsent(context.getMethod(), handler);
        }
        try {
            return context.proceed();
        } catch (final Throwable e) {
            return handler.handle(new EnrichedExecutionContext() {
                @Override
                public Object getTarget() {
                    return context.getTarget();
                }

                @Override
                public Method getMethod() {
                    return context.getMethod();
                }

                @Override
                public Object[] getParameters() {
                    return context.getParameters();
                }

                @Override
                public Throwable getFailure() {
                    return e;
                }
            });
        }
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Method, FallbackHandler<?>> handlers = new ConcurrentHashMap<>();

        @Inject
        private AnnotationFinder finder;

        @Inject
        private SafeguardExtension extension;

        @Inject
        private BeanManager beanManager;

        @Inject
        private FaultToleranceMetrics metrics;

        private final Collection<CreationalContext<?>> contexts = new ArrayList<>();

        @PreDestroy
        private void release() {
            contexts.forEach(CreationalContext::release);
        }

        public Map<Method, FallbackHandler<?>> getHandlers() {
            return handlers;
        }

        public FallbackHandler<?> create(final InvocationContext context) {
            final Fallback fallback = finder.findAnnotation(Fallback.class, context);
            final Class<? extends FallbackHandler<?>> value = fallback.value();
            final String method = fallback.fallbackMethod();
            if (!method.isEmpty() && value != Fallback.DEFAULT.class) {
                throw new FaultToleranceDefinitionException("You can't set a method and handler as fallback on " + context.getMethod());
            }

            FallbackHandler<?> handler;
            if (value != Fallback.DEFAULT.class) {
                Stream.of(value.getInterfaces()).filter(it -> it == FallbackHandler.class)
                        .findFirst()
                        .map(it -> ParameterizedType.class.cast(it))
                        .filter(it -> it.getActualTypeArguments().length == 1)
                        .map(it -> extension.toClass(context.getMethod().getReturnType()).isAssignableFrom(extension.toClass(it.getActualTypeArguments()[0])))
                        .orElseThrow(() -> new FaultToleranceDefinitionException("handler does not match method: " + context.getMethod()));
                final Set<Bean<?>> beans = beanManager.getBeans(value);
                final Bean<?> handlerBean = beanManager.resolve(beans);
                final CreationalContext<Object> creationalContext = beanManager.createCreationalContext(null);
                if (!beanManager.isNormalScope(handlerBean.getScope())) {
                    contexts.add(creationalContext);
                }
                final FallbackHandler fallbackHandler = FallbackHandler.class.cast(
                        beanManager.getReference(handlerBean, FallbackHandler.class, creationalContext));
                handler = fallbackHandler;
            } else {
                try {
                    final Method fallbackMethod = context.getTarget()
                                                         .getClass()
                                                         .getMethod(method);
                    if (!extension.toClass(context.getMethod()
                                                  .getReturnType())
                                  .isAssignableFrom(extension.toClass(fallbackMethod.getReturnType())) || !Arrays.equals(
                            context.getMethod()
                                   .getParameterTypes(), fallbackMethod.getParameterTypes())) {
                        throw new FaultToleranceDefinitionException("handler method does not match method: " + context.getMethod());
                    }
                    if (!fallbackMethod.isAccessible()) {
                        fallbackMethod.setAccessible(true);
                    }
                    handler = (FallbackHandler<Object>) context1 -> {
                        try {
                            return fallbackMethod.invoke(EnrichedExecutionContext.class.cast(context1)
                                                                                       .getTarget(),
                                    context1.getParameters());
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        } catch (final InvocationTargetException e) {
                            throw new IllegalStateException(e.getTargetException());
                        }
                    };
                } catch (final NoSuchMethodException e) {
                    throw new FaultToleranceDefinitionException("No method " + method + " in " + context.getTarget());
                }
            }

            final String metricsName = "ft." + context.getMethod().getDeclaringClass().getCanonicalName() + "."
                    + context.getMethod().getName() + ".fallback.calls.total";
            final FaultToleranceMetrics.Counter counter = metrics.counter(metricsName,
                    "Number of times the fallback handler or method was called");
            return (FallbackHandler<Object>) context12 -> {
                counter.inc();
                return handler.handle(context12);
            };
        }
    }

    private interface EnrichedExecutionContext extends ExecutionContext {
        Object getTarget();
    }
}
