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
import static java.util.Optional.ofNullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UnwrappedCache {
    private final Map<Class<?>, Optional<Class<?>>> unwrappedCache = new ConcurrentHashMap<>();

    public Map<Class<?>, Optional<Class<?>>> getUnwrappedCache() {
        return unwrappedCache;
    }

    public static final class Tool {
        private Tool() {
            // no-op
        }

        public static Optional<Class<?>> unwrap(final Map<Class<?>, Optional<Class<?>>> unwrappedCache, final Object instance) {
            if (instance == null) {
                return empty();
            }
            final Class<?> raw = instance.getClass();
            final Optional<Class<?>> existing = unwrappedCache.get(raw);
            if (existing != null) {
                return existing;
            }
            Class<?> target = raw;
            while (target.getName()
                         .contains("$$")) {
                target = target.getSuperclass();
            }
            if (target == Object.class) {
                return empty();
            }
            final Optional<Class<?>> out = ofNullable(target);
            unwrappedCache.put(raw, out);
            return out;
        }
    }
}
