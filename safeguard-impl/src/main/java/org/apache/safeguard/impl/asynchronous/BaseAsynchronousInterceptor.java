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

import javax.interceptor.AroundInvoke;
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
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Future<?>> ref = new AtomicReference<>();
            getExecutor(context).execute(() -> {
                final Object proceed;
                try {
                    proceed = context.proceed();
                    ref.set(Future.class.cast(proceed));
                } catch (final Exception e) {
                    final CompletableFuture<Object> failingFuture = new CompletableFuture<>();
                    failingFuture.completeExceptionally(e);
                    ref.set(failingFuture);
                } finally {
                    latch.countDown();
                }
            });
            return new Future() {
                private void await() {
                    try {
                        latch.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public boolean cancel(final boolean mayInterruptIfRunning) {
                    await();
                    return ref.get().cancel(mayInterruptIfRunning);
                }

                @Override
                public boolean isCancelled() {
                    await();
                    return ref.get().isCancelled();
                }

                @Override
                public boolean isDone() {
                    await();
                    return ref.get().isDone();
                }

                @Override
                public Object get() throws InterruptedException, ExecutionException {
                    await();
                    return ref.get().get();
                }

                @Override
                public Object get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    await();
                    return ref.get().get(timeout, unit);
                }
            };
        }
        throw new FaultToleranceDefinitionException(
                "Unsupported return type: " + returnType + " (from: " + context.getMethod() + ")." +
                        "Should be Future or CompletionStage.");
    }
}
