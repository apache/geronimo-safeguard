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

package org.apache.safeguard.impl.config;

import static java.util.Optional.ofNullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.eclipse.microprofile.faulttolerance.Fallback;

@ApplicationScoped
public class ConfigurationMapper {
    @Inject
    private GeronimoFaultToleranceConfig config;

    @Inject
    private BeanManager beanManager;

    public <T extends Annotation> T map(final T instance, final Method sourceMethod, final Class<T> api) {
        return api.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{api, Enabled.class}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(instance, args);
            }
            return findConfiguredValue(instance, api, sourceMethod, method, args);
        }));
    }

    public <T extends Annotation> boolean isEnabled(final Method method, final Class<T> api) {
        final boolean methodLevel = isDefinedAtMethodLevel(method, api);
        final Supplier<Boolean> globalEvaluator = () ->
            ofNullable(findClassConfiguration(api, method, "enabled"))
                    .map(Boolean::parseBoolean)
                    .orElseGet(() -> ofNullable(config.read(String.format("%s/%s", api.getSimpleName(), "enabled")))
                        .map(Boolean::parseBoolean)
                        .orElseGet(() -> Fallback.class == api ?
                            true :
                            ofNullable(config.read("MP_Fault_Tolerance_NonFallback_Enabled"))
                                    .map(Boolean::parseBoolean)
                                    .orElse(true)));
        if (methodLevel) {
            return ofNullable(findMethodConfiguration(api, method, "enabled"))
                    .map(Boolean::parseBoolean)
                    .orElseGet(globalEvaluator);
        }
        return globalEvaluator.get();
    }

    private <T extends Annotation> Object findConfiguredValue(final T instance, final Class<T> api,
                                                              final Method sourceMethod,
                                                              final Method proxyMethod, final Object[] args) {
        final boolean methodLevel = isDefinedAtMethodLevel(sourceMethod, api);
        final Supplier<Object> classEvaluator = () ->
            ofNullable(findDefaultConfiguration(proxyMethod, proxyMethod.getName()))
                    .map(v -> coerce(v, proxyMethod.getReturnType()))
                    .orElseGet(() -> ofNullable(findClassConfiguration(api, sourceMethod, proxyMethod.getName()))
                            .map(v -> coerce(v, proxyMethod.getReturnType()))
                            .orElseGet(() -> getReflectionConfig(instance, proxyMethod, args)));
        if (methodLevel) {
            return ofNullable(findMethodConfiguration(api, sourceMethod, proxyMethod.getName()))
                        .map(v -> coerce(v, proxyMethod.getReturnType()))
                        .orElseGet(classEvaluator::get);
        }
        return classEvaluator.get();
    }

    private <T extends Annotation> boolean isDefinedAtMethodLevel(final Method method, final Class<T> api) {
        final AnnotatedType<?> selected = beanManager.createAnnotatedType(method.getDeclaringClass());
        return selected.getMethods().stream()
                       .filter(it -> it.getJavaMember().getName().equals(method.getName()) &&
                               Arrays.equals(it.getJavaMember().getParameterTypes(), method.getParameterTypes()))
                       .anyMatch(it -> it.isAnnotationPresent(api));
    }

    private <T extends Annotation> Object getReflectionConfig(final T instance,
                                                              final Method proxyMethod,
                                                              final Object[] args) {
        try {
            return proxyMethod.invoke(instance, args);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (final InvocationTargetException e) {
            throw new IllegalStateException(e.getTargetException());
        }
    }

    private String findDefaultConfiguration(final Method api, final String methodName) {
        return config.read(String.format("%s/%s", api.getDeclaringClass().getSimpleName(), methodName));
    }

    private <T extends Annotation> String findClassConfiguration(final Class<T> api, final Method beanMethod, final String apiMethod) {
        return config.read(String.format("%s/%s/%s",
                beanMethod.getDeclaringClass().getName(), api.getSimpleName(), apiMethod));
    }

    private <T extends Annotation> String findMethodConfiguration(final Class<T> api, final Method beanMethod, final String apiMethod) {
        return config.read(String.format("%s/%s/%s/%s",
                beanMethod.getDeclaringClass().getName(), beanMethod.getName(), api.getSimpleName(), apiMethod));
    }

    private Object coerce(final String raw, final Class<?> expected) {
        if (expected == long.class || expected == Long.class) {
            return Long.valueOf(raw);
        }
        if (expected == double.class || expected == Double.class) {
            return Double.valueOf(raw);
        }
        if (expected == int.class || expected == Integer.class) {
            return Integer.valueOf(raw);
        }
        if (expected == boolean.class || expected == Boolean.class) {
            return Boolean.valueOf(raw);
        }
        if (expected == ChronoUnit.class) {
            return ChronoUnit.valueOf(raw);
        }
        if (expected == String.class) {
            return raw;
        }
        if (expected == Class[].class) {
            return Stream.of(raw.split(","))
                    .map(String::trim)
                    .filter(it -> !it.isEmpty())
                    .map(it -> {
                        try {
                            return Thread.currentThread().getContextClassLoader().loadClass(it);
                        } catch (final ClassNotFoundException e) {
                            throw new IllegalArgumentException(e);
                        }
                    })
                    .toArray();
        }
        throw new IllegalArgumentException("Unsupported: " + expected);
    }
}
