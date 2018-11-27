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
package org.apache.safeguard.impl.asynchronous;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.cache.Key;
import org.apache.safeguard.impl.cache.UnwrappedCache;
import org.apache.safeguard.impl.config.ConfigurationMapper;
import org.apache.safeguard.impl.customizable.Safeguard;
import org.apache.safeguard.impl.interceptor.IdGeneratorInterceptor;
import org.eclipse.microprofile.faulttolerance.Asynchronous;

@Interceptor
@Asynchronous
@Priority(Interceptor.Priority.PLATFORM_AFTER + 6)
public class AsynchronousInterceptor extends BaseAsynchronousInterceptor {
    @Inject
    private Cache cache;

    @Override
    protected Executor getExecutor(final InvocationContext context) {
        return cache.getExecutor();
    }

    @AroundInvoke
    public Object async(final InvocationContext context) throws Exception {
        final Map<Key, Boolean> models = cache.getEnabled();
        final Key cacheKey = new Key(context, cache.getUnwrappedCache().getUnwrappedCache());
        Boolean enabled = models.get(cacheKey);
        if (enabled == null) {
            enabled = cache.getMapper().isEnabled(context.getMethod(), Asynchronous.class);
            models.putIfAbsent(cacheKey, enabled);
        }
        if (!enabled) {
            return context.proceed();
        }
        final String key = Asynchronous.class.getName() + ".skip_" +
                context.getContextData().get(IdGeneratorInterceptor.class.getName());
        if (context.getContextData().putIfAbsent(key, Boolean.TRUE) != null) { // bulkhead or so handling threading
            return context.proceed();
        }
        return around(context);
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Key, Boolean> enabled = new ConcurrentHashMap<>();

        @Inject
        @Safeguard
        private Executor executor;

        @Inject
        private ConfigurationMapper mapper;

        @Inject
        private UnwrappedCache unwrappedCache;

        public UnwrappedCache getUnwrappedCache() {
            return unwrappedCache;
        }

        public ConfigurationMapper getMapper() {
            return mapper;
        }

        public Executor getExecutor() {
            return executor;
        }

        public Map<Key, Boolean> getEnabled() {
            return enabled;
        }
    }
}
