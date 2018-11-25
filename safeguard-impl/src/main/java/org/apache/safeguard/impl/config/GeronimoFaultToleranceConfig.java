/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.safeguard.impl.config;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import javax.annotation.Priority;
import javax.enterprise.inject.Vetoed;

@Vetoed
class DefaultFaultToleranceConfig implements GeronimoFaultToleranceConfig {
    private final Map<String, String> configuration = new HashMap<>();

    DefaultFaultToleranceConfig() {
        System.getProperties().stringPropertyNames()
              .forEach(k -> configuration.put(k, System.getProperty(k)));
        try (final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/geronimo/microprofile/fault-tolerance.properties")) {
            if (stream != null) {
                final Properties properties = new Properties();
                properties.load(stream);
                properties.stringPropertyNames().forEach(k -> configuration.put(k, properties.getProperty(k)));
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String read(final String value) {
        return configuration.get(value);
    }
}

@FunctionalInterface
public interface GeronimoFaultToleranceConfig {

    String read(String value);

    static GeronimoFaultToleranceConfig create() {
        try {
            final Optional<GeronimoFaultToleranceConfig> iterator = StreamSupport.stream(
                    ServiceLoader.load(GeronimoFaultToleranceConfig.class).spliterator(), false)
                    .min(comparing(it -> ofNullable(it.getClass().getAnnotation(Priority.class)).map(Priority::value).orElse(0)));
            if (iterator.isPresent()) {
                return new WrappedConfig(iterator.orElseThrow(IllegalStateException::new));
            }
            return new WrappedConfig(new FaultToleranceConfigMpConfigImpl());
        } catch (final ServiceConfigurationError | ExceptionInInitializerError | NoClassDefFoundError | Exception e) {
            // no-op
        }
        return new WrappedConfig(new DefaultFaultToleranceConfig());
    }
}
