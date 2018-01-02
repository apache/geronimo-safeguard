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

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Priority;

import org.apache.safeguard.api.config.ConfigFacade;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

class DefaultConfigFacade extends ConfigFacade {
    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        return getOptionalValue(name).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    @Override
    public long getLong(String name, long defaultValue) {
        return getOptionalValue(name).map(Long::parseLong).orElse(defaultValue);
    }

    @Override
    public int getInt(String name, int defaultValue) {
        return getOptionalValue(name).map(Integer::parseInt).orElse(defaultValue);
    }

    @Override
    public double getDouble(String name, double defaultValue) {
        return getOptionalValue(name).map(Double::parseDouble).orElse(defaultValue);
    }

    @Override
    public ChronoUnit getChronoUnit(String name, ChronoUnit defaultValue) {
        return getOptionalValue(name).map(ChronoUnit::valueOf).orElse(defaultValue);
    }

    @Override
    public Class<? extends Throwable>[] getThrowableClasses(String name, Class<? extends Throwable>[] defaultValue) {
        return getOptionalValue(name).map(value -> {
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            return Stream.of(name.split(",")).map(clazz -> {
                try {
                    return loader.loadClass(clazz.trim());
                } catch (final ClassNotFoundException e) {
                    throw new IllegalArgumentException(e);
                }
            }).toArray(Class[]::new);
        }).orElse(defaultValue);
    }

    private Optional<String> getOptionalValue(final String name) {
        return Optional.ofNullable(Optional.ofNullable(System.getenv(name)).orElseGet(() -> System.getProperty(name)));
    }
}
