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
package org.apache.safeguard.impl.circuitbreaker;

import static java.util.Arrays.asList;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.annotation.AnnotationFinder;
import org.apache.safeguard.impl.config.ConfigurationMapper;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

@CircuitBreaker
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER + 12)
public class CircuitBreakerInterceptor implements Serializable {
    @Inject
    private Cache cache;

    @AroundInvoke
    public Object ifNotOpen(final InvocationContext context) throws Exception {
        final Map<Method, CircuitBreakerImpl> circuitBreakers = cache.getCircuitBreakers();
        CircuitBreakerImpl circuitBreaker = circuitBreakers.get(context.getMethod());
        if (circuitBreaker == null) {
            circuitBreaker = cache.create(context);
            final CircuitBreakerImpl existing = circuitBreakers.putIfAbsent(context.getMethod(), circuitBreaker);
            if (existing != null) {
                circuitBreaker = existing;
            }
        }
        if (circuitBreaker.disabled) {
            return context.proceed();
        }

        final CheckResult state = circuitBreaker.performStateCheck(CheckType.READ_ONLY);
        if (state == CheckResult.OPEN) {
            circuitBreaker.callsPrevented.inc();
            throw new CircuitBreakerOpenException(context.getMethod() + " circuit breaker is open");
        }
        try {
            final Object result = context.proceed();
            if (state != CheckResult.CLOSED_CHANGED) { // a change triggers a reset we want to preserve
                circuitBreaker.onSuccess();
            }
            return result;
        } catch (final Exception e) {
            if (circuitBreaker.failOn.length > 0 &&
                    Stream.of(circuitBreaker.failOn).anyMatch(it -> it.isInstance(e) || it.isInstance(e.getCause()))) {
                circuitBreaker.onFailure();
            } else {
                circuitBreaker.callsSucceeded.inc();
            }
            throw e;
        }
    }

    private static long now() {
        return System.nanoTime();
    }

    private enum CheckType {
        READ_ONLY,
        FAILURE,
        SUCCESS
    }

    private enum State {
        CLOSED {
            @Override
            public State isStateTransition(final CircuitBreakerImpl breaker,
                                           final CheckIntervalData currentData,
                                           final CheckIntervalData nextData) {
                final long now = now();
                final double currentFailureRatio = getCurrentFailureRatio(nextData);
                if (nextData.states.length >= breaker.volumeThreshold && currentFailureRatio >= breaker.failureRatio) {
                    breaker.closedDuration.set(now - currentData.checkIntervalStart);
                    breaker.opened.inc();
                    return OPEN;
                }
                return this;
            }

            private double getCurrentFailureRatio(final CheckIntervalData data) {
                return data.states.length == 0 ?  0 :
                        (Stream.of(data.states).filter(it -> !it).count() / (1. * data.states.length));
            }
        },
        HALF_OPEN {
            @Override
            public State isStateTransition(final CircuitBreakerImpl breaker,
                                           final CheckIntervalData currentData,
                                           final CheckIntervalData nextData) {
                if (Stream.of(nextData.states).anyMatch(it -> !it)) { // a exception was thrown
                    return OPEN;
                }

                final long successes = Stream.of(nextData.states).filter(it -> it).count();
                if (successes == nextData.states.length && successes >= breaker.successThreshold) {
                    breaker.halfOpenDuration.set(now() - currentData.checkIntervalStart);
                    return CLOSED;
                }
                return this;
            }
        },
        OPEN {
            @Override
            public State isStateTransition(final CircuitBreakerImpl breaker,
                                           final CheckIntervalData currentData,
                                           final CheckIntervalData nextData) {
                if (nextData.checkIntervalStart != currentData.checkIntervalStart) {
                    breaker.openDuration.set(now() - currentData.checkIntervalStart);
                    return breaker.successThreshold == 1 ? CLOSED : HALF_OPEN;
                }
                if (Stream.of(nextData.states).filter(it -> it).count() > breaker.successThreshold) {
                    breaker.openDuration.set(now() - currentData.checkIntervalStart);
                    return breaker.successThreshold == 1 ? CLOSED : HALF_OPEN;
                }
                return this;
            }
        };

        private boolean isCheckIntervalFinished(final CircuitBreakerImpl breaker,
                                                final CheckIntervalData currentData,
                                                final long now) {
            return (now - currentData.checkIntervalStart) > breaker.delay;
        }

        public abstract State isStateTransition(CircuitBreakerImpl breaker,
                                                CheckIntervalData currentData,
                                                CheckIntervalData nextData);
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Method, CircuitBreakerImpl> circuitBreakers = new ConcurrentHashMap<>();

        @Inject
        private AnnotationFinder finder;

        @Inject
        private ConfigurationMapper mapper;

        @Inject
        private FaultToleranceMetrics metrics;

        public Map<Method, CircuitBreakerImpl> getCircuitBreakers() {
            return circuitBreakers;
        }

        public CircuitBreakerImpl create(final InvocationContext context) {
            final CircuitBreaker definition = mapper.map(
                    finder.findAnnotation(CircuitBreaker.class, context), context.getMethod(), CircuitBreaker.class);
            final long delay = definition.delayUnit().getDuration().toNanos() * definition.delay();
            if (delay < 0) {
                throw new FaultToleranceDefinitionException("CircuitBreaker delay can't be < 0");
            }

            final Class<? extends Throwable>[] failOn = definition.failOn();

            final double failureRatio = definition.failureRatio();
            if (failureRatio < 0 || failureRatio > 1) {
                throw new FaultToleranceDefinitionException("CircuitBreaker failure ratio can't be < 0 and > 1");
            }

            final int volumeThreshold = definition.requestVolumeThreshold();
            if (volumeThreshold < 1) {
                throw new FaultToleranceDefinitionException("CircuitBreaker volume threshold can't be < 0");
            }

            final int successThreshold = definition.successThreshold();
            if (successThreshold <= 0) {
                throw new FaultToleranceDefinitionException("CircuitBreaker success threshold can't be <= 0");
            }

            final String metricsNameBase = "ft." + context.getMethod().getDeclaringClass().getCanonicalName() + "."
                    + context.getMethod().getName() + ".circuitbreaker.";

            final CircuitBreakerImpl circuitBreaker = new CircuitBreakerImpl(
                    !mapper.isEnabled(context.getMethod(), CircuitBreaker.class),
                    volumeThreshold, delay, successThreshold,
                    failOn, failureRatio, metrics.counter(metricsNameBase + "callsSucceeded.total",
                    "Number of calls allowed to run by the circuit breaker that returned successfully"),
                    metrics.counter(metricsNameBase + "callsFailed.total",
                            "Number of calls allowed to run by the circuit breaker that then failed"),
                    metrics.counter(metricsNameBase + "callsPrevented.total",
                            "Number of calls prevented from running by an open circuit breaker"),
                    metrics.counter(metricsNameBase + "opened.total",
                            "Number of times the circuit breaker has moved from closed state to open state"));
            metrics.gauge(metricsNameBase + "open.total", "Amount of time the circuit breaker has spent in open state", "nanoseconds",
                    circuitBreaker.openDuration::get);
            metrics.gauge(metricsNameBase + "halfOpen.total", "Amount of time the circuit breaker has spent in half-open state", "nanoseconds",
                    circuitBreaker.halfOpenDuration::get);
            metrics.gauge(metricsNameBase + "closed.total", "Amount of time the circuit breaker has spent in closed state", "nanoseconds",
                    circuitBreaker.closedDuration::get);
            return circuitBreaker;
        }
    }

    private enum CheckResult {
        OPEN, CLOSED_CHANGED, CLOSED
    }

    public static class CircuitBreakerImpl {
        private static final Boolean[] EMPTY_ARRAY = new Boolean[0];
        private static final Boolean[] FIRST_SUCCESS_ARRAY = {Boolean.TRUE};
        private static final Boolean[] FIRST_FAILURE_ARRAY = {Boolean.FALSE};

        private final boolean disabled;
        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicReference<CheckIntervalData> checkIntervalData;
        private final int volumeThreshold;
        private final long delay;
        private final int successThreshold;
        private final double failureRatio;
        private final Class<? extends Throwable>[] failOn;

        private final AtomicLong openDuration = new AtomicLong();
        private final FaultToleranceMetrics.Counter callsSucceeded;
        private final FaultToleranceMetrics.Counter callsFailed;
        private final FaultToleranceMetrics.Counter callsPrevented;
        private final AtomicLong halfOpenDuration = new AtomicLong();
        private final AtomicLong closedDuration = new AtomicLong();
        private final FaultToleranceMetrics.Counter opened;

        CircuitBreakerImpl(final boolean disabled,
                           final int volumeThreshold, final long delay, final int successThreshold,
                           final Class<? extends Throwable>[] failOn, final double failureRatio,
                           final FaultToleranceMetrics.Counter callsSucceeded,
                           final FaultToleranceMetrics.Counter callsFailed,
                           final FaultToleranceMetrics.Counter callsPrevented,
                           final FaultToleranceMetrics.Counter opened) {
            this.disabled = disabled;
            this.checkIntervalData = new AtomicReference<>(new CheckIntervalData(volumeThreshold, EMPTY_ARRAY, 0));
            this.volumeThreshold = volumeThreshold;
            this.delay = delay;
            this.successThreshold = successThreshold;
            this.failOn = failOn;
            this.failureRatio = failureRatio;
            this.callsSucceeded = callsSucceeded;
            this.callsFailed = callsFailed;
            this.callsPrevented = callsPrevented;
            this.opened = opened;
        }

        private void onSuccess() {
            performStateCheck(CheckType.SUCCESS);
            callsSucceeded.inc();
        }

        private void onFailure() {
            performStateCheck(CheckType.FAILURE);
            callsFailed.inc();
        }

        private CheckResult performStateCheck(final CheckType type) {
            CheckIntervalData currentData;
            CheckIntervalData nextData;
            State currentState;
            do {
                final long time = now();
                currentState = state.get();
                currentData = checkIntervalData.get();
                nextData = nextCheckIntervalData(type, currentData, currentState, time);
            } while (!updateCheckIntervalData(currentData, nextData));
            final State newState = currentState.isStateTransition(this, currentData, nextData);
            if (newState != currentState) {
                state.compareAndSet(currentState, newState);
                checkIntervalData.set(new CheckIntervalData(volumeThreshold, EMPTY_ARRAY, now()));
                return newState != State.OPEN ? CheckResult.CLOSED_CHANGED : CheckResult.OPEN;
            }
            return newState != State.OPEN ? CheckResult.CLOSED : CheckResult.OPEN;
        }

        private boolean updateCheckIntervalData(final CheckIntervalData currentData,
                                                final CheckIntervalData nextData) {
            return currentData == nextData
                    || checkIntervalData.compareAndSet(currentData, nextData);
        }

        private CheckIntervalData nextCheckIntervalData(final CheckType type,
                                                        final CheckIntervalData currentData,
                                                        final State currentState,
                                                        final long time) {
            if (currentState.isCheckIntervalFinished(this, currentData, time)) {
                return toNewData(type, time);
            } else {
                switch (type) {
                    case FAILURE:
                        return currentData.failure();
                    case SUCCESS:
                        return currentData.success();
                    case READ_ONLY:
                        return currentData;
                    default:
                        throw new IllegalArgumentException("unknown type " + type);
                }
            }
        }

        private CheckIntervalData toNewData(final CheckType type, final long time) {
            switch (type) {
                case FAILURE:
                    return new CheckIntervalData(volumeThreshold, FIRST_FAILURE_ARRAY, time);
                case SUCCESS:
                    return new CheckIntervalData(volumeThreshold, FIRST_SUCCESS_ARRAY, time);
                case READ_ONLY:
                    return new CheckIntervalData(volumeThreshold, EMPTY_ARRAY, time);
                default:
                    throw new IllegalArgumentException("unknown type " + type);
            }
        }
    }

    private static class CheckIntervalData {
        private final int length;
        private final Boolean[] states; // todo: revise that but seems the spec sucks
        private final long checkIntervalStart;

        CheckIntervalData(final int length, final Boolean[] states, final long intervalStart) {
            this.length = length;
            this.states = states;
            this.checkIntervalStart = intervalStart;
        }

        private CheckIntervalData success() {
            return new CheckIntervalData(length, nextArray(true), checkIntervalStart);
        }

        private CheckIntervalData failure() {
            return new CheckIntervalData(length, nextArray(false), checkIntervalStart);
        }

        private Boolean[] nextArray(final boolean value) {
            final Boolean[] array = new Boolean[Math.min(length, states.length + 1)];
            if (this.states.length > 0) {
                if (this.states.length < array.length) {
                    System.arraycopy(this.states, 0, array, 0, this.states.length);
                } else {
                    System.arraycopy(this.states, 1, array, 0, this.states.length - 1);
                }
            }
            array[array.length - 1] = value;
            return array;
        }

        @Override
        public String toString() {
            return "CheckIntervalData{states=" + asList(states) + ", checkIntervalStart=" + checkIntervalStart + '}';
        }
    }
}
