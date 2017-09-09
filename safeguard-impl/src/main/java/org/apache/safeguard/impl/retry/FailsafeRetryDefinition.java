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
import org.apache.safeguard.api.retry.RetryDefinition;

import java.time.Duration;
import java.util.Collection;

public class FailsafeRetryDefinition implements RetryDefinition{
    private final RetryPolicy retryPolicy;
    private final Collection<Class<? extends Throwable>> retryExceptions;
    private final Collection<Class<? extends Throwable>> abortExceptions;

    FailsafeRetryDefinition(RetryPolicy retryPolicy, Collection<Class<? extends Throwable>> retryExceptions, Collection<Class<? extends Throwable>> abortExceptions) {
        this.retryPolicy = retryPolicy;
        this.retryExceptions = retryExceptions;
        this.abortExceptions = abortExceptions;
    }

    @Override
    public int getMaxRetries() {
        return retryPolicy.getMaxRetries();
    }

    @Override
    public Duration getDelay() {
        net.jodah.failsafe.util.Duration delay = retryPolicy.getDelay();
        return Duration.ofMillis(delay.toMillis());
    }

    @Override
    public Duration getMaxDuration() {
        net.jodah.failsafe.util.Duration maxDuration = retryPolicy.getMaxDuration();
        return Duration.ofMillis(maxDuration.toMillis());
    }

    @Override
    public Duration getJitter() {
        net.jodah.failsafe.util.Duration jitter = retryPolicy.getJitter();
        return Duration.ofMillis(jitter.toMillis());
    }

    @Override
    public Collection<Class<? extends Throwable>> getRetryExceptions() {
        return this.retryExceptions;
    }

    @Override
    public Collection<Class<? extends Throwable>> getAbortExceptions() {
        return abortExceptions;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
}
