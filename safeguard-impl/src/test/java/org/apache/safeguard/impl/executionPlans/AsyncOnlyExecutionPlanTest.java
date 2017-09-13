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

import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncOnlyExecutionPlanTest {

    @Test
    public void shouldExecuteAsncWithoutTimeout() {
        AsyncOnlyExecutionPlan asyncOnlyExecutionPlan = new AsyncOnlyExecutionPlan(Executors.newFixedThreadPool(2));
        MyCallable callable = new MyCallable();
        asyncOnlyExecutionPlan.execute(callable);
        assertThat(callable.calledThread).isNotEqualTo(Thread.currentThread().getName());
    }

    private static class MyCallable implements Callable<Object> {

        private String calledThread;

        @Override
        public Object call() throws Exception {
            this.calledThread = Thread.currentThread().getName();
            return "";
        }
    }

}