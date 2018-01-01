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

package org.apache.safeguard.api.config;

import javax.annotation.Priority;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.SortedSet;
import java.util.TreeSet;

public abstract class ConfigFacade {
    private static ConfigFacade INSTANCE;

    public abstract boolean getBoolean(String name, boolean defaultValue);
    public abstract long getLong(String name, long defaultValue);
    public abstract int getInt(String name, int defaultValue);
    public abstract double getDouble(String name, double defaultValue);
    public abstract ChronoUnit getChronoUnit(String name, ChronoUnit defaultValue);
    public abstract Class<? extends Throwable>[] getThrowableClasses(String name, Class<? extends Throwable>[] defaultValue);

    public static ConfigFacade getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static void setInstance(ConfigFacade configFacade) {
        INSTANCE = configFacade;
    }

    private static ConfigFacade load() {
        ServiceLoader<ConfigFacade> configFacades = ServiceLoader.load(ConfigFacade.class);
        SortedSet<ConfigFacade> configFacedSet = new TreeSet<>(Comparator.comparingInt(c -> {
            Priority p = c.getClass().getAnnotation(Priority.class);
            return p == null ? 1 : p.value();
        }));
        for(ConfigFacade facade : configFacades) {
            configFacedSet.add(facade);
        }
        return configFacedSet.first();
    }
}
