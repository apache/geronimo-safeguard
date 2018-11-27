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
package org.apache.safeguard.impl.asynchronous;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.interceptor.IdGeneratorInterceptor;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

public abstract class BaseAsynchronousInterceptor implements Serializable {
    protected abstract Executor getExecutor(InvocationContext context);

    protected Object around(final InvocationContext context) throws Exception {
        final String key = Asynchronous.class.getName() +
                context.getContextData().get(IdGeneratorInterceptor.class.getName());
        if (context.getContextData().get(key) != null) { // bulkhead or so handling threading
            return context.proceed();
        }

        context.getContextData().put(key, "true");

        final Class<?> returnType = context.getMethod().getReturnType();
        if (CompletionStage.class.isAssignableFrom(returnType)) {
            final CompletableFuture future = new CompletableFuture<>();
            getExecutor(context).execute(() -> {
                try {
                    final Object proceed = context.proceed();
                    final CompletionStage<?> stage = CompletionStage.class.cast(proceed);
                    stage.handle((r, e) -> {
                        if (e != null) {
                            future.completeExceptionally(e);
                        } else {
                            future.complete(r);
                        }
                        return null;
                    });
                } catch (final Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
        if (Future.class.isAssignableFrom(returnType)) {
            final FutureWrapper<Object> facade = new FutureWrapper<>();
            getExecutor(context).execute(() -> {
                final Object proceed;
                try {
                    proceed = context.proceed();
                    facade.setDelegate(Future.class.cast(proceed));
                } catch (final Exception e) {
                    final CompletableFuture<Object> failingFuture = new CompletableFuture<>();
                    failingFuture.completeExceptionally(e);
                    facade.setDelegate(failingFuture);
                }
            });
            return facade;
        }
        throw new FaultToleranceDefinitionException(
                "Unsupported return type: " + returnType + " (from: " + context.getMethod() + ")." +
                        "Should be Future or CompletionStage.");
    }

    private static class FutureWrapper<T> implements Future<T> {
        private final AtomicReference<Future<T>> delegate = new AtomicReference<>();
        private final AtomicReference<Consumer<Future<T>>> cancelled = new AtomicReference<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        private void setDelegate(final Future<T> delegate) {
            final Consumer<Future<T>> cancelledTask = cancelled.get();
            if (cancelledTask != null) {
                cancelledTask.accept(delegate);
            }
            this.delegate.set(delegate);
            this.latch.countDown();
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            cancelled.set(f -> f.cancel(mayInterruptIfRunning));
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() != null;
        }

        @Override
        public boolean isDone() {
            final Future<T> future = delegate.get();
            return future != null && future.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            latch.await();
            return delegate.get().get();
        }

        @Override
        public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            final long latchWaitStart = System.nanoTime();
            final boolean latchWait = latch.await(timeout, unit);
            final long latchWaitDuration = System.nanoTime() - latchWaitStart;
            if (!latchWait) {
                throw new TimeoutException();
            }
            return delegate.get().get(unit.toNanos(timeout) - latchWaitDuration, NANOSECONDS);
        }
    }
}
