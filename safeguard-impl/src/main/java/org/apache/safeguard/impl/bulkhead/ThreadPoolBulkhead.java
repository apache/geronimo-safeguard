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

package org.apache.safeguard.impl.bulkhead;

import org.apache.safeguard.api.bulkhead.Bulkhead;
import org.apache.safeguard.api.bulkhead.BulkheadDefinition;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ThreadPoolBulkhead implements Bulkhead {
    private final BulkheadDefinition bulkheadDefinition;
    private final BlockingQueue<Runnable> workQueue;
    private final ThreadPoolExecutor threadPoolExecutor;

    public ThreadPoolBulkhead(BulkheadDefinition bulkheadDefinition) {
        this.bulkheadDefinition = bulkheadDefinition;
        this.workQueue = new LinkedBlockingQueue<>(bulkheadDefinition.getMaxWaitingExecutions());
        this.threadPoolExecutor = new ThreadPoolExecutor(
                bulkheadDefinition.getMaxConcurrentExecutions(),
                bulkheadDefinition.getMaxConcurrentExecutions(),
                0L, TimeUnit.MILLISECONDS,
                workQueue,
                Executors.defaultThreadFactory());
    }

    @Override
    public BulkheadDefinition getBulkheadDefinition() {
        return bulkheadDefinition;
    }

    @Override
    public int getCurrentQueueDepth() {
        return workQueue.size();
    }

    @Override
    public int getCurrentExecutions() {
        return threadPoolExecutor.getActiveCount();
    }

    @Override
    public <T> T execute(Callable<T> callable) {
        try {
            return (T)new DelegatingFuture<T>((Future<Future<T>>) threadPoolExecutor.submit(callable));
        } catch (RejectedExecutionException e) {
            throw new BulkheadException(e);
        }
    }

    private class DelegatingFuture<R> implements Future<R>{

        private final Future<Future<R>> child;

        public DelegatingFuture(Future<Future<R>> child) {
            this.child = child;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return child.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return child.isCancelled();
        }

        @Override
        public boolean isDone() {
            return child.isDone();
        }

        @Override
        public R get() throws InterruptedException, ExecutionException {
            return child.get().get();
        }

        @Override
        public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return child.get().get(timeout, unit);
        }
    }
}
