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
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ConfigurationMapper {
    @Inject
    private GeronimoFaultToleranceConfig config;

    public <T extends Annotation> T map(final T instance, final Method sourceMethod, final Class<T> api) {
        return api.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{api, Enabled.class}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(instance, args);
            }
            return findConfiguredValue(instance, api, sourceMethod, method, args);
        }));
    }

    private <T extends Annotation> Object findConfiguredValue(final T instance, final Class<T> api,
                                                              final Method sourceMethod,
                                                              final Method proxyMethod, final Object[] args) {
        return ofNullable(ofNullable(findDefaultConfiguration(proxyMethod))
                .orElseGet(() -> ofNullable(findMethodConfiguration(api, sourceMethod, proxyMethod))
                        .orElseGet(() -> ofNullable(findClassConfiguration(api, sourceMethod, proxyMethod)).orElse(null))))
                .map(v -> coerce(v, proxyMethod.getReturnType()))
                .orElseGet(() -> {
                    try {
                        return proxyMethod.invoke(instance, args);
                    } catch (final IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalStateException(e.getTargetException());
                    }
                });
    }

    private String findDefaultConfiguration(final Method api) {
        return config.read(String.format("%s/%s", api.getDeclaringClass().getSimpleName(), api.getName()));
    }

    private <T extends Annotation> String findClassConfiguration(final Class<T> api, final Method beanMethod, final Method apiMethod) {
        return config.read(String.format("%s/%s/%s",
                beanMethod.getDeclaringClass().getName(), api.getSimpleName(), apiMethod.getName()));
    }

    private <T extends Annotation> String findMethodConfiguration(final Class<T> api, final Method beanMethod, final Method apiMethod) {
        return config.read(String.format("%s/%s/%s/%s",
                beanMethod.getDeclaringClass().getName(), beanMethod.getName(), api.getSimpleName(), apiMethod.getName()));
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
