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

import org.apache.safeguard.api.config.ConfigFacade;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Priority;
import java.time.temporal.ChronoUnit;

@Priority(1)
public class MicroProfileConfigFacade extends ConfigFacade {
    private final Config config;

    public MicroProfileConfigFacade() {
        this(ConfigProvider.getConfig());
    }

    public MicroProfileConfigFacade(Config config) {
        this.config = config;
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        return config.getOptionalValue(name, Boolean.class).orElse(defaultValue);
    }

    @Override
    public long getLong(String name, long defaultValue) {
        return config.getOptionalValue(name, Long.class).orElse(defaultValue);
    }

    @Override
    public int getInt(String name, int defaultValue) {
        return config.getOptionalValue(name, Integer.class).orElse(defaultValue);
    }

    @Override
    public double getDouble(String name, double defaultValue) {
        return config.getOptionalValue(name, Double.class).orElse(defaultValue);
    }

    @Override
    public ChronoUnit getChronoUnit(String name, ChronoUnit defaultValue) {
        return config.getOptionalValue(name, ChronoUnit.class).orElse(defaultValue);
    }

    @Override
    public Class<? extends Throwable>[] getThrowableClasses(String name, Class<? extends Throwable>[] defaultValue) {
        return config.getOptionalValue(name, Class[].class).orElse(defaultValue);
    }
}
