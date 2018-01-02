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

import javax.annotation.Priority;

import org.apache.safeguard.api.config.ConfigFacade;

@Priority(1)
public class ConfigFacadeFacade extends ConfigFacade {
    private final ConfigFacade delegate = loadDelegate();

    private ConfigFacade loadDelegate() {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            final Class<?> loadClass = loader.loadClass("org.eclipse.microprofile.config.ConfigProvider");
            loadClass.getMethod("getConfig").invoke(null);
            return new MicroProfileConfigFacade();
        } catch (final Throwable notHere) {
            return new DefaultConfigFacade();
        }
    }

    @Override
    public boolean getBoolean(final String name, final boolean defaultValue) {
        return delegate.getBoolean(name, defaultValue);
    }

    @Override
    public long getLong(final String name, final long defaultValue) {
        return delegate.getLong(name, defaultValue);
    }

    @Override
    public int getInt(final String name, final int defaultValue) {
        return delegate.getInt(name, defaultValue);
    }

    @Override
    public double getDouble(final String name, final double defaultValue) {
        return delegate.getDouble(name, defaultValue);
    }

    @Override
    public ChronoUnit getChronoUnit(final String name, final ChronoUnit defaultValue) {
        return delegate.getChronoUnit(name, defaultValue);
    }

    @Override
    public Class<? extends Throwable>[] getThrowableClasses(final String name, final Class<? extends Throwable>[] defaultValue) {
        return delegate.getThrowableClasses(name, defaultValue);
    }
}
