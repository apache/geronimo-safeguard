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
package org.apache.safeguard.impl.cache;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.interceptor.InvocationContext;

public class Key {
    private final Class<?> declaringClass;
    private final Method method;
    private final int hash;

    public Key(final InvocationContext context, final Map<Class<?>, Class<?>> unwrappedCache) {
        this(unwrap(unwrappedCache, context.getTarget()).orElseGet(() -> context.getMethod().getDeclaringClass()), context.getMethod());
    }

    public Key(final Class<?> declaringClass, final Method method) {
        this.declaringClass = declaringClass;
        this.method = method;
        this.hash = Objects.hash(declaringClass, method);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Key key = Key.class.cast(o);
        return Objects.equals(declaringClass, key.declaringClass) && Objects.equals(method, key.method);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private static Optional<Class<?>> unwrap(final Map<Class<?>, Class<?>> unwrappedCache,
                                             final Object instance) {
        if (instance == null) {
            return empty();
        }
        final Class<?> raw = instance.getClass();
        final Class<?> existing = unwrappedCache.get(raw);
        if (existing != null) {
            return of(existing);
        }
        Class<?> target = raw;
        while (target.getName().contains("$$")) {
            target = target.getSuperclass();
        }
        if (target == Object.class) {
            return empty();
        }
        unwrappedCache.put(raw, target);
        return ofNullable(target);
    }
}
