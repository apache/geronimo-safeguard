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

import org.apache.safeguard.api.circuitbreaker.CircuitBreakerState;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreaker;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class TimeoutWrappedCallable<T> implements Callable<T> {
    private final Callable<T> delegate;
    private final ScheduledExecutorService executorService;
    private final Duration timeout;
    private final FailsafeCircuitBreaker failsafeCircuitBreaker;
    private boolean timedout = false;

    TimeoutWrappedCallable(Callable<T> delegate, ScheduledExecutorService executorService, Duration timeout, FailsafeCircuitBreaker failsafeCircuitBreaker) {
        this.delegate = delegate;
        this.executorService = executorService;
        this.timeout = timeout;
        this.failsafeCircuitBreaker = failsafeCircuitBreaker;
    }

    @Override
    public T call() throws Exception {
        boolean circuitBreakerOpen = failsafeCircuitBreaker != null && failsafeCircuitBreaker.getState() == CircuitBreakerState.OPEN;
        if(circuitBreakerOpen) {
            throw new CircuitBreakerOpenException();
        }
        ScheduledFuture<?> scheduledFuture = executorService.schedule(new TimerRunnable(Thread.currentThread(), this),
                timeout.toNanos(), TimeUnit.NANOSECONDS);

        T result;
        try {
            result = delegate.call();
        } catch (Exception e) {
            throw e;
        } finally {
            scheduledFuture.cancel(true);
        }
        if(timedout) {
            throw new TimeoutException("Execution timed out after " + timeout);
        }
        return result;
    }

    private class TimerRunnable implements Runnable {
        private final Thread targetThread;
        private final TimeoutWrappedCallable task;

        private boolean doInterrupt = true;
        private TimerRunnable(Thread targetThread, TimeoutWrappedCallable task) {
            this.targetThread = targetThread;
            this.task = task;
        }

        @Override
        public void run() {
            if(doInterrupt) {
                task.timedout = true;
                targetThread.interrupt();
            }
        }
    }
}
