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

package org.apache.safeguard.impl.executionPlans;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class TimeoutWrappedCallable<T> implements Callable<T> {
    private final Callable<T> delegate;
    private final ExecutorService executorService;
    private final Duration timeout;

    TimeoutWrappedCallable(Callable<T> delegate, ExecutorService executorService, Duration timeout) {
        this.delegate = delegate;
        this.executorService = executorService;
        this.timeout = timeout;
    }

    @Override
    public T call() throws Exception {
        try {
            return executorService.submit(delegate).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e) {
            throw new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException(e);
        }
    }
}
