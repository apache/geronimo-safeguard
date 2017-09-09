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

package org.apache.safeguard.impl.retry;

import net.jodah.failsafe.RetryPolicy;
import org.apache.safeguard.api.retry.RetryBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FailsafeRetryBuilder implements RetryBuilder{
    private final List<Class<? extends Throwable>> retryOn;
    private final List<Class<? extends Throwable>> abortOn;
    private final RetryPolicy retryPolicy;
    private final String name;
    private final FailsafeRetryManager failsafeRetryManager;

    FailsafeRetryBuilder(String name, FailsafeRetryManager failsafeRetryManager) {
        this.name = name;
        this.failsafeRetryManager = failsafeRetryManager;
        this.retryOn = new ArrayList<>();
        this.abortOn = new ArrayList<>();
        this.retryPolicy = new RetryPolicy();
    }
    @Override
    public FailsafeRetryBuilder withMaxRetries(int maxRetries) {
        retryPolicy.withMaxRetries(maxRetries);
        return this;
    }

    @Override
    public FailsafeRetryBuilder withDelay(Duration delay) {
        retryPolicy.withDelay(delay.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    public FailsafeRetryBuilder withMaxDuration(Duration maxDuration) {
        retryPolicy.withMaxDuration(maxDuration.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    public FailsafeRetryBuilder withJitter(Duration jitter) {
        retryPolicy.withJitter(jitter.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    public FailsafeRetryBuilder withAbortOn(Class<? extends Throwable>... abortOn) {
        this.abortOn.addAll(Arrays.asList(abortOn));
        return this;
    }

    @Override
    public FailsafeRetryBuilder withRetryOn(Class<? extends Throwable>... retryOn) {
        this.retryOn.addAll(Arrays.asList(retryOn));
        return this;
    }

    @Override
    public FailsafeRetryDefinition build() {
        if(!this.abortOn.isEmpty()) {
            retryPolicy.abortOn(this.abortOn);
        }
        if(!this.retryOn.isEmpty()) {
            retryPolicy.retryOn(this.retryOn);
        }
        FailsafeRetryDefinition definition = new FailsafeRetryDefinition(retryPolicy, retryOn, abortOn);
        failsafeRetryManager.register(name, definition);
        return definition;
    }
}
