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
package org.apache.safeguard.impl.cdi;

import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.DefinitionException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.asynchronous.AsynchronousInterceptor;
import org.apache.safeguard.impl.bulkhead.BulkheadInterceptor;
import org.apache.safeguard.impl.circuitbreaker.CircuitBreakerInterceptor;
import org.apache.safeguard.impl.config.GeronimoFaultToleranceConfig;
import org.apache.safeguard.impl.customizable.Safeguard;
import org.apache.safeguard.impl.fallback.FallbackInterceptor;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
import org.apache.safeguard.impl.retry.AfterRetryInterceptor;
import org.apache.safeguard.impl.retry.BaseRetryInterceptor;
import org.apache.safeguard.impl.retry.BeforeRetryInterceptor;
import org.apache.safeguard.impl.timeout.TimeoutInterceptor;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

public class SafeguardExtension implements Extension {
    private boolean foundExecutor;
    private GeronimoFaultToleranceConfig config;
    private Integer priorityBase;
    private final Collection<Annotated> beansToValidate = new ArrayList<>();

    // for validation and prepopulation of the cache at startup - note: should we enable to deactivate it?
    private TimeoutInterceptor.Cache timeoutCache;
    private BulkheadInterceptor.Cache bulkHeadCache;
    private CircuitBreakerInterceptor.Cache circuitBreakerCache;
    private FallbackInterceptor.Cache fallbackCache;
    private BaseRetryInterceptor.Cache retryCache;

    void grabInterceptorPriority(@Observes final BeforeBeanDiscovery beforeBeanDiscovery) {
        config = GeronimoFaultToleranceConfig.create();
        priorityBase = ofNullable(config.read("mp.fault.tolerance.interceptor.priority"))
                    .map(Integer::parseInt).orElse(null);
    }

    void customizeAsyncPriority(@Observes final ProcessAnnotatedType<AsynchronousInterceptor> interceptor) {
        customizePriority(interceptor);
    }

    void customizeBulkHeadPriority(@Observes final ProcessAnnotatedType<BulkheadInterceptor> interceptor) {
        customizePriority(interceptor);
    }

    void customizeCircuitBreakerPriority(@Observes final ProcessAnnotatedType<CircuitBreakerInterceptor> interceptor) {
        customizePriority(interceptor);
    }

    void customizeFallbackPriority(@Observes final ProcessAnnotatedType<FallbackInterceptor> interceptor) {
        customizePriority(interceptor);
    }

    void customizeBeforeRetryPriority(@Observes final ProcessAnnotatedType<BeforeRetryInterceptor> interceptor) {
        customizePriority(interceptor);
    }

    void customizeAfterRetryPriority(@Observes final ProcessAnnotatedType<AfterRetryInterceptor> interceptor) {
        customizePriority(interceptor);
    }

    void customizeTimeoutPriority(@Observes final ProcessAnnotatedType<TimeoutInterceptor> interceptor) {
        customizePriority(interceptor);
    }

    void addFallbackInterceptor(@Observes final ProcessAnnotatedType<FallbackInterceptor> processAnnotatedType) {
        processAnnotatedType.configureAnnotatedType().add(new FallbackBinding());
    }

    void activateSafeguard(@Observes @WithAnnotations({
            /*Asynchronous.class, */
            Bulkhead.class, CircuitBreaker.class,
            Fallback.class, Retry.class, Timeout.class
    }) final ProcessAnnotatedType<?> processAnnotatedType) {
        if (processAnnotatedType.getAnnotatedType().getJavaClass().getName().startsWith("org.apache.safeguard.impl.")) {
            return;
        }
        if (faultToleranceAnnotations().anyMatch(it -> processAnnotatedType.getAnnotatedType().isAnnotationPresent(it))) {
            processAnnotatedType.configureAnnotatedType().add(SafeguardEnabled.Literal.INSTANCE);
        } else {
            final List<Method> methods = processAnnotatedType.getAnnotatedType().getMethods().stream()
                    .filter(it -> faultToleranceAnnotations().anyMatch(it::isAnnotationPresent))
                    .map(AnnotatedMethod::getJavaMember).collect(toList());
            processAnnotatedType.configureAnnotatedType()
                    .filterMethods(it -> methods.contains(it.getJavaMember()))
                    .forEach(m -> m.add(SafeguardEnabled.Literal.INSTANCE));
        }
    }

    void onBean(@Observes final ProcessBean<?> bean) {
        if (isSafeguardBean(bean)
            && bean.getBean().getTypes().stream().anyMatch(it -> Executor.class.isAssignableFrom(toClass(it)))) {
            foundExecutor = true;
        }

        if (AnnotatedType.class.isInstance(bean.getAnnotated())) {
            final AnnotatedType<?> at = AnnotatedType.class.cast(bean.getAnnotated());
            if (at.getMethods().stream().anyMatch(m -> m.isAnnotationPresent(SafeguardEnabled.class))) {
                beansToValidate.add(bean.getAnnotated());
            }
        }
    }

    void addMissingBeans(@Observes final AfterBeanDiscovery afterBeanDiscovery) {
        final GeronimoFaultToleranceConfig config = GeronimoFaultToleranceConfig.create();
        afterBeanDiscovery.addBean()
                .id("geronimo_safeguard#configuration")
                .types(GeronimoFaultToleranceConfig.class, Object.class)
                .beanClass(GeronimoFaultToleranceConfig.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(ApplicationScoped.class)
                .createWith(c -> config);

        afterBeanDiscovery.addBean()
                .id("geronimo_safeguard#metrics")
                .types(FaultToleranceMetrics.class, Object.class)
                .beanClass(FaultToleranceMetrics.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(ApplicationScoped.class)
                .createWith(c -> FaultToleranceMetrics.create(config));

        if (!foundExecutor) {
            afterBeanDiscovery.addBean()
                    .id("geronimo_safeguard#executor")
                    .types(Executor.class, Object.class)
                    .beanClass(Executor.class)
                    .qualifiers(Safeguard.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .createWith(c -> Executors.newCachedThreadPool(new ThreadFactory() {
                        private final ThreadGroup group = ofNullable(System.getSecurityManager())
                                .map(SecurityManager::getThreadGroup)
                                .orElseGet(() -> Thread.currentThread().getThreadGroup());
                        private final String prefix = "org.apache.geronimo.safeguard.asynchronous@" +
                                System.identityHashCode(this);
                        private final AtomicLong counter = new AtomicLong();

                        @Override
                        public Thread newThread(final Runnable r) {
                            return new Thread(group, r, prefix + counter.incrementAndGet());
                        }
                    }))
                    .scope(ApplicationScoped.class)
                    .destroyWith((e, c) -> ExecutorService.class.cast(e).shutdownNow());
        }
    }

    void addDefinitionErrors(@Observes final AfterDeploymentValidation validation) {
        // TODO: we ignore the hierarchy validation for now - inherited @XXX
        beansToValidate.stream()
                       .map(this::validate)
                       .filter(Objects::nonNull)
                       .forEach(validation::addDeploymentProblem);
        beansToValidate.clear();
    }

    private <T> T getInstance(final Class<T> cache) {
        return CDI.current().select(cache).get();
    }

    private Throwable validate(final Annotated annotated) { // todo: do we want to cache here too or save some memory
        { // timeout
            final Throwable throwable = validate(Timeout.class, annotated, context -> {
                if (timeoutCache == null) {
                    timeoutCache = getInstance(TimeoutInterceptor.Cache.class);
                }
                timeoutCache.create(context);
            });
            if (throwable != null) {
                return throwable;
            }
        }
        { // bulkhead
            final Throwable throwable = validate(Bulkhead.class, annotated, context -> {
                if (bulkHeadCache == null) {
                    bulkHeadCache = getInstance(BulkheadInterceptor.Cache.class);
                }
                bulkHeadCache.create(context);
            });
            if (throwable != null) {
                return throwable;
            }
        }
        { // circuit breaker
            final Throwable throwable = validate(CircuitBreaker.class, annotated, context -> {
                if (circuitBreakerCache == null) {
                    circuitBreakerCache = getInstance(CircuitBreakerInterceptor.Cache.class);
                }
                circuitBreakerCache.create(context);
            });
            if (throwable != null) {
                return throwable;
            }
        }
        { // fallback
            final Throwable throwable = validate(Fallback.class, annotated, context -> {
                if (fallbackCache == null) {
                    fallbackCache = getInstance(FallbackInterceptor.Cache.class);
                }
                fallbackCache.create(context);
            });
            if (throwable != null) {
                return throwable;
            }
        }
        { // retry
            final Throwable throwable = validate(Retry.class, annotated, context -> {
                if (retryCache == null) {
                    retryCache = getInstance(BaseRetryInterceptor.Cache.class);
                }
                retryCache.create(context);
            });
            if (throwable != null) {
                return throwable;
            }
        }
        return null;
    }

    private Throwable validate(final Class<? extends Annotation> marker,
                               final Annotated type,
                               final Consumer<InvocationContext> contextConsumer) {
        final boolean classHasMarker = type.isAnnotationPresent(marker);
        final AnnotatedType<?> annotatedType = AnnotatedType.class.cast(type);
        try {
            annotatedType.getMethods().stream()
                         .filter(it -> classHasMarker || it.isAnnotationPresent(marker))
                         .map(m -> new MockInvocationContext(m.getJavaMember()))
                         .forEach(contextConsumer);
            return null;
        } catch (final RuntimeException re) {
            return new DefinitionException(re);
        }
    }

    private boolean isSafeguardBean(final ProcessBean<?> bean) {
        return bean.getBean().getQualifiers().stream().anyMatch(it -> it.annotationType() == Safeguard.class);
    }

    private void customizePriority(final ProcessAnnotatedType<?> type) {
        if (priorityBase == null) {
            return;
        }
        final int offset = type.getAnnotatedType().getAnnotation(Priority.class).value() - Interceptor.Priority.PLATFORM_AFTER;
        type.configureAnnotatedType()
            .remove(it -> it.annotationType() == Priority.class)
            .add(new PriorityBinding(priorityBase + offset));
    }

    public Class<?> toClass(final Type it) {
        return doToClass(it, 0);
    }

    private Class<?> doToClass(final Type it, final int iterations) {
        if (Class.class.isInstance(it)) {
            return Class.class.cast(it);
        }
        if (iterations > 100) { // with generic it happens we can loop here
            return Object.class;
        }
        if (ParameterizedType.class.isInstance(it)) {
            return doToClass(ParameterizedType.class.cast(it).getRawType(), iterations + 1);
        }
        return Object.class; // will not match any of our test
    }

    private Stream<Class<? extends Annotation>> faultToleranceAnnotations() {
        return Stream.of(Asynchronous.class, Bulkhead.class, CircuitBreaker.class,
                Fallback.class, Retry.class, Timeout.class);
    }

    private static class MockInvocationContext implements InvocationContext {
        private static final Object[] NO_PARAM = new Object[0];

        private final Method method;

        private MockInvocationContext(final Method m) {
            this.method = m;
        }

        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Constructor<?> getConstructor() {
            return null;
        }

        @Override
        public Object[] getParameters() {
            return NO_PARAM;
        }

        @Override
        public void setParameters(final Object[] parameters) {
            // no-op
        }

        @Override
        public Map<String, Object> getContextData() {
            return emptyMap();
        }

        @Override
        public Object proceed() {
            return null;
        }

        @Override
        public Object getTimer() {
            return null;
        }
    }
}
