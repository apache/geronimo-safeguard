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
package org.apache.safeguard.impl.metrics;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.annotation.Priority;
import javax.enterprise.inject.spi.CDI;

public interface FaultToleranceMetrics {
    Counter counter(String name, String description);
    void gauge(String name, String description, String unit, Supplier<Long> supplier);
    Histogram histogram(String name, String description);


    interface Histogram {
        void update(long duration);
    }
    interface Counter {
        void inc();
        void dec();
    }
    interface Gauge extends Supplier<Long> {
    }

    static FaultToleranceMetrics create() {
        try {
            final Optional<FaultToleranceMetrics> iterator = StreamSupport.stream(
                    ServiceLoader.load(FaultToleranceMetrics.class).spliterator(), false)
                    .min(comparing(it -> ofNullable(it.getClass().getAnnotation(Priority.class)).map(Priority::value).orElse(0)));
            if (iterator.isPresent()) {
                return iterator.orElseThrow(IllegalStateException::new);
            }
            return new MicroprofileMetricsImpl(CDI.current().select(org.eclipse.microprofile.metrics.MetricRegistry.class).get());
        } catch (final Exception e) {
            // no-op
        }
        return new NoMetrics();
    }
}

