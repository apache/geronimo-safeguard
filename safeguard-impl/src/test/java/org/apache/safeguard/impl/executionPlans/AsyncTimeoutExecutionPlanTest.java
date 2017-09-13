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

import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AsyncTimeoutExecutionPlanTest {
    @Test
    public void shouldExecuteSimpleCallable() {
        AsyncTimeoutExecutionPlan asyncTimeoutExecutionPlan = new AsyncTimeoutExecutionPlan(Duration.ofMillis(1000), Executors.newSingleThreadExecutor());
        DelayedCaller callable = new DelayedCaller(200);

        asyncTimeoutExecutionPlan.execute(callable);

        String myThreadName = Thread.currentThread().getName();
        assertThat(callable.executedThread).isNotEqualTo(myThreadName);
    }

    @Test
    public void shouldThrowTimeoutWhenDelayHit() {
        AsyncTimeoutExecutionPlan asyncTimeoutExecutionPlan = new AsyncTimeoutExecutionPlan(Duration.ofMillis(100), Executors.newSingleThreadExecutor());
        DelayedCaller callable = new DelayedCaller(200);

        assertThatThrownBy(() -> asyncTimeoutExecutionPlan.execute(callable)).isInstanceOf(TimeoutException.class);
    }

    private static class DelayedCaller implements Callable<Object> {

        private final long delay;
        private String executedThread;

        public DelayedCaller(long delay) {
            this.delay = delay;
        }

        @Override
        public Object call() throws Exception {
            this.executedThread = Thread.currentThread().getName();
            Thread.sleep(delay);
            return new Object();
        }
    }
}