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

import static org.eclipse.microprofile.metrics.MetricType.COUNTER;
import static org.eclipse.microprofile.metrics.MetricType.GAUGE;
import static org.eclipse.microprofile.metrics.MetricType.HISTOGRAM;

import java.util.function.Supplier;

import javax.enterprise.inject.Vetoed;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;

@Vetoed
class MicroprofileMetricsImpl implements FaultToleranceMetrics {
    private final MetricRegistry registry;

    MicroprofileMetricsImpl(final MetricRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Counter counter(final String name, final String description) {
        final org.eclipse.microprofile.metrics.Counter delegate = registry.counter(
                reusable(new Metadata(name, name, description, COUNTER, "none")));
        return new Counter() {
            @Override
            public void inc() {
                delegate.inc();
            }

            @Override
            public void dec() {
                delegate.dec();
            }
        };
    }

    @Override
    public void gauge(final String name, final String description, final String unit,
                       final Supplier<Long> supplier) {
        registry.register(reusable(new Metadata(name, name, description, GAUGE, unit)),
                (org.eclipse.microprofile.metrics.Gauge<Long>) supplier::get);
    }

    @Override
    public Histogram histogram(final String name, final String description) {
        final org.eclipse.microprofile.metrics.Histogram histogram = registry.histogram(
                reusable(new Metadata(name, name, description, HISTOGRAM, "none")));
        return histogram::update;
    }

    private Metadata reusable(final Metadata metadata) {
        metadata.setReusable(true);
        return metadata;
    }
}
