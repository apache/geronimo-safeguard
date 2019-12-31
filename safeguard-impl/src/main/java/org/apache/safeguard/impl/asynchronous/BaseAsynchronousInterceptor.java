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

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.Serializable;
import java.util.Map;
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
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

public abstract class BaseAsynchronousInterceptor implements Serializable {
    public abstract Executor getExecutor(InvocationContext context);

    protected Object around(final InvocationContext context, final String id) {
        final Class<?> returnType = context.getMethod().getReturnType();
        if (CompletionStage.class.isAssignableFrom(returnType)) {
            final ExtendedCompletableFuture<Object> future = newCompletableFuture(context);
            getExecutor(context).execute(() -> {
                try {
                    future.before();
                    final Object proceed = context.proceed();
                    final CompletionStage<?> stage = CompletionStage.class.cast(proceed);
                    stage.handle((r, e) -> {
                        future.after();
                        if (e != null) {
                            ofNullable(getErrorHandler(context.getContextData()))
                                    .map(eh -> {
                                        if (Exception.class.isInstance(e)) {
                                            try {
                                                eh.apply(Exception.class.cast(e));
                                            } catch (final Exception e1) {
                                                future.completeExceptionally(e1);
                                            }
                                        } else {
                                            future.completeExceptionally(e);
                                        }
                                        return true;
                                    })
                                    .orElseGet(() -> future.completeExceptionally(e));
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
            final FutureWrapper<Object> facade = newFuture(context, context.getContextData());
            context.getContextData().put(FutureWrapper.class.getName() + "_" + id, facade);
            getExecutor(context).execute(new AsyncTask(context, facade));
            return facade;
        }
        throw new FaultToleranceDefinitionException(
                "Unsupported return type: " + returnType + " (from: " + context.getMethod() + ")." +
                        "Should be Future or CompletionStage.");
    }

    private static ErrorHandler<Exception, Future<?>> getErrorHandler(final Map<String, Object> contextData) {
        return ErrorHandler.class.cast(
                contextData.get(BaseAsynchronousInterceptor.BaseFuture.class.getName() + ".errorHandler_" +
                        contextData.get(IdGeneratorInterceptor.class.getName())));
    }

    protected FutureWrapper<Object> newFuture(final InvocationContext context,
                                              final Map<String, Object> data) {
        return new FutureWrapper<>(data);
    }

    protected ExtendedCompletableFuture<Object> newCompletableFuture(final InvocationContext context) {
        return new ExtendedCompletableFuture<>();
    }

    @FunctionalInterface
    public interface ErrorHandler<A, B> {
        B apply(A a) throws Exception;
    }

    public interface BaseFuture {
        default void before() {

        }

        default void after() {

        }
    }

    public static class ExtendedCompletableFuture<T> extends CompletableFuture<T> implements BaseFuture {
    }

    public static class FutureWrapper<T> implements Future<T>, BaseFuture {
        private FutureWrapper<?> parent;
        private final AtomicReference<Future<T>> delegate = new AtomicReference<>();
        private final AtomicReference<Consumer<Future<T>>> cancelled = new AtomicReference<>();
        private final CountDownLatch latch = new CountDownLatch(1);
        private final Map<String, Object> data;

        public FutureWrapper(final Map<String, Object> data) {
            this.data = data;
        }

        public FutureWrapper<?> getParent() {
            return parent;
        }

        public void setParent(final FutureWrapper<?> parent) {
            if (this.parent != null) {
                this.parent.setParent(parent);
            }
            this.parent = parent;
        }

        public void setDelegate(final Future<T> delegate) {
            final Consumer<Future<T>> cancelledTask = cancelled.get();
            if (cancelledTask != null) {
                cancelledTask.accept(delegate);
            }
            after();
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
            return future != null && future.isDone() && getErrorHandler(data) == null;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            latch.await();
            final Future<T> future = delegate.get();
            try {
                return future.get();
            } catch (final ExecutionException ee) {
                delegate.set(onException(ee));
                return delegate.get().get();
            }
        }

        @Override
        public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            final long end = System.nanoTime() + unit.toNanos(timeout);
            final boolean latchWait = latch.await(timeout, unit);
            final long readyNs = System.nanoTime();
            if (!latchWait || readyNs > end) {
                throw new TimeoutException();
            }
            try {
                return delegate.get().get(Math.max(0, end - readyNs), NANOSECONDS);
            } catch (final ExecutionException ee) {
                delegate.set(onException(ee));
                final long now = System.nanoTime();
                if (now > end) {
                    throw new TimeoutException();
                }
                return delegate.get().get(Math.max(0, end - now), NANOSECONDS);
            }
        }

        // todo: revise
        protected Future<T> onException(final Throwable cause) {
            if (!Exception.class.isInstance(cause)) {
                if (Error.class.isInstance(cause)) {
                    throw Error.class.cast(cause);
                }
                throw new IllegalStateException(cause);
            }
            final Exception ex = Exception.class.cast(cause);
            final ErrorHandler<Exception, Future<?>> handler = getErrorHandler(data);
            if (handler != null) {
                try {
                    return (Future<T>) handler.apply(ex);
                } catch (final Exception e) {
                    if (RuntimeException.class.isInstance(e)) {
                        throw RuntimeException.class.cast(e);
                    }
                    if (Error.class.isInstance(e)) {
                        throw Error.class.cast(e);
                    }
                    if (ExecutionException.class.isInstance(cause) && cause.getCause() != cause) {
                        return failAsFuture(cause);
                    }
                    throw new IllegalStateException(e);
                }
            }
            if (ExecutionException.class.isInstance(cause) && cause.getCause() != cause) {
                final Throwable throwable = ExecutionException.class.cast(cause).getCause();
                return onException(throwable);
            }
            if (RuntimeException.class.isInstance(cause)) {
                throw RuntimeException.class.cast(cause);
            }
            throw new IllegalStateException(cause); // unreachable - just for compiler
        }

        private Future<T> failAsFuture(final Throwable cause) {
            final Throwable throwable = ExecutionException.class.cast(cause).getCause();
            final ExtendedCompletableFuture<T> future = new ExtendedCompletableFuture<>();
            future.completeExceptionally(throwable);
            return future;
        }

        public void failed(final Exception exception) {
            final ExtendedCompletableFuture<T> delegate = new ExtendedCompletableFuture<>();
            delegate.completeExceptionally(exception);
            setDelegate(delegate);
            latch.countDown();
        }
    }

    public static class AsyncTask implements Runnable {
        private final InvocationContext context;
        private final FutureWrapper<Object> facade;

        public AsyncTask(final InvocationContext context, final FutureWrapper<Object> facade) {
            this.context = context;
            this.facade = facade;
        }

        public FutureWrapper<Object> getFacade() {
            return facade;
        }

        @Override
        public void run() {
            final Object proceed;
            try {
                facade.before();
                proceed = context.proceed();
                facade.setDelegate(Future.class.cast(proceed));
            } catch (final Exception e) {
                final CompletableFuture<Object> failingFuture = new CompletableFuture<>();
                failingFuture.completeExceptionally(e);
                facade.setDelegate(failingFuture);
            }
        }
    }
}
