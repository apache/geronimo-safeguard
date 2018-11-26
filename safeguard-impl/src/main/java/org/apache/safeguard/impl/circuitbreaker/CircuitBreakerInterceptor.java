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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.EnumMap;
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
@Priority(Interceptor.Priority.PLATFORM_AFTER + 2)
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
        if (!circuitBreaker.checkState()) {
            circuitBreaker.callsPrevented.inc();
            throw new CircuitBreakerOpenException(context.getMethod() + " circuit breaker is open");
        }
        try {
            final Object result = context.proceed();
            circuitBreaker.callsSucceeded.inc();
            return result;
        } catch (final Exception e) {
            if (circuitBreaker.failOn.length > 0 &&
                    Stream.of(circuitBreaker.failOn).anyMatch(it -> it.isInstance(e) || it.isInstance(e.getCause()))) {
                circuitBreaker.callsFailed.inc();
                circuitBreaker.incrementAndCheckState(1);
            } else {
                circuitBreaker.callsSucceeded.inc();
            }
            throw e;
        }
    }

    private enum State {
        CLOSED {
            @Override
            public State oppositeState() {
                return OPEN;
            }
        },

        OPEN {
            @Override
            public State oppositeState() {
                return CLOSED;
            }
        };

        /**
         * Returns the opposite state to the represented state. This is useful
         * for flipping the current state.
         *
         * @return the opposite state
         */
        public abstract State oppositeState();
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
            if (failureRatio < 0) {
                throw new FaultToleranceDefinitionException("CircuitBreaker failure ratio can't be < 0");
            }

            final int volumeThreshold = definition.requestVolumeThreshold();
            if (volumeThreshold < 1) {
                throw new FaultToleranceDefinitionException("CircuitBreaker volume threshold can't be < 0");
            }

            final int successThreshold = definition.successThreshold();
            if (successThreshold < 0) {
                throw new FaultToleranceDefinitionException("CircuitBreaker success threshold can't be < 0");
            }

            final String metricsNameBase = "ft." + context.getMethod().getDeclaringClass().getCanonicalName() + "."
                    + context.getMethod().getName() + ".circuitbreaker.";

            final CircuitBreakerImpl circuitBreaker = new CircuitBreakerImpl(volumeThreshold, delay, successThreshold,
                    delay, failOn, failureRatio, metrics.counter(metricsNameBase + "callsSucceeded.total",
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

    // from commons-lang - todo: refine
    public static class CircuitBreakerImpl {
        private static final Map<State, StateStrategy> STRATEGY_MAP = createStrategyMap();

        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicReference<CheckIntervalData> checkIntervalData;
        private final int openingThreshold;
        private final long openingInterval;
        private final int closingThreshold;
        private final long closingInterval;
        private final double failureRatio;
        private final Class<? extends Throwable>[] failOn;

        private final AtomicLong openDuration = new AtomicLong();
        private final FaultToleranceMetrics.Counter callsSucceeded;
        private final FaultToleranceMetrics.Counter callsFailed;
        private final FaultToleranceMetrics.Counter callsPrevented;
        private final AtomicLong halfOpenDuration = new AtomicLong();
        private final AtomicLong closedDuration = new AtomicLong();
        private final FaultToleranceMetrics.Counter opened;

        CircuitBreakerImpl(final int openingThreshold, final long openingInterval, final int closingThreshold,
                           final long closingInterval, final Class<? extends Throwable>[] failOn,
                           final double failureRatio,
                           final FaultToleranceMetrics.Counter callsSucceeded,
                           final FaultToleranceMetrics.Counter callsFailed,
                           final FaultToleranceMetrics.Counter callsPrevented,
                           final FaultToleranceMetrics.Counter opened) {
            this.checkIntervalData = new AtomicReference<>(new CheckIntervalData(0, 0));
            this.openingThreshold = openingThreshold;
            this.openingInterval = openingInterval;
            this.closingThreshold = closingThreshold;
            this.closingInterval = closingInterval;
            this.failOn = failOn;
            this.failureRatio = failureRatio;
            this.callsSucceeded = callsSucceeded;
            this.callsFailed = callsFailed;
            this.callsPrevented = callsPrevented;
            this.opened = opened;
        }

        protected static boolean isOpen(final State state) {
            return state == State.OPEN;
        }

        protected void changeState(final State newState) {
            state.compareAndSet(newState.oppositeState(), newState);
        }

        public boolean checkState() {
            return performStateCheck(0);
        }

        public boolean incrementAndCheckState(final Integer increment) {
            return performStateCheck(increment);
        }

        private boolean performStateCheck(final int increment) {
            CheckIntervalData currentData;
            CheckIntervalData nextData;
            State currentState;
            do {
                final long time = now();
                currentState = state.get();
                currentData = checkIntervalData.get();
                nextData = nextCheckIntervalData(increment, currentData, currentState, time);
            } while (!updateCheckIntervalData(currentData, nextData));
            if (stateStrategy(currentState).isStateTransition(this, currentData, nextData)) {
                currentState = currentState.oppositeState();
                if (currentState == State.OPEN) {
                    opened.inc();
                }
                changeStateAndStartNewCheckInterval(currentState);
            }
            return !isOpen(currentState);
        }

        private boolean updateCheckIntervalData(final CheckIntervalData currentData,
                                                final CheckIntervalData nextData) {
            return currentData == nextData
                    || checkIntervalData.compareAndSet(currentData, nextData);
        }

        private void changeStateAndStartNewCheckInterval(final State newState) {
            changeState(newState);
            checkIntervalData.set(new CheckIntervalData(0, now()));
        }

        private CheckIntervalData nextCheckIntervalData(final int increment,
                                                        final CheckIntervalData currentData, final State currentState, final long time) {
            CheckIntervalData nextData;
            if (stateStrategy(currentState).isCheckIntervalFinished(this, currentData, time)) {
                nextData = new CheckIntervalData(increment, time);
            } else {
                nextData = currentData.increment(increment);
            }
            return nextData;
        }

        static long now() {
            return System.nanoTime();
        }

        private static StateStrategy stateStrategy(final State state) {
            return STRATEGY_MAP.get(state);
        }

        private static Map<State, StateStrategy> createStrategyMap() {
            final Map<State, StateStrategy> map = new EnumMap<>(State.class);
            map.put(State.CLOSED, new StateStrategyClosed());
            map.put(State.OPEN, new StateStrategyOpen());
            return map;
        }

        private static class CheckIntervalData {
            private final int eventCount;
            private final long checkIntervalStart;

            CheckIntervalData(final int count, final long intervalStart) {
                eventCount = count;
                checkIntervalStart = intervalStart;
            }

            private CheckIntervalData increment(final int delta) {
                return (delta == 0) ? this : new CheckIntervalData(eventCount + delta, checkIntervalStart);
            }
        }

        private abstract static class StateStrategy {
            private boolean isCheckIntervalFinished(final CircuitBreakerImpl breaker,
                                                   final CheckIntervalData currentData, final long now) {
                return now - currentData.checkIntervalStart > fetchCheckInterval(breaker);
            }

            public abstract boolean isStateTransition(CircuitBreakerImpl breaker,
                                                      CheckIntervalData currentData, CheckIntervalData nextData);

            protected abstract long fetchCheckInterval(CircuitBreakerImpl breaker);
        }

        private static class StateStrategyClosed extends StateStrategy {
            @Override
            public boolean isStateTransition(final CircuitBreakerImpl breaker,
                                             final CheckIntervalData currentData, final CheckIntervalData nextData) {
                final long now = now();
                final boolean result =
                        nextData.eventCount >= breaker.openingThreshold || (now != currentData.checkIntervalStart && (currentData.eventCount / (now - currentData.checkIntervalStart)) > breaker.failureRatio);
                if (!result) {
                    breaker.closedDuration.set(now - currentData.checkIntervalStart);
                }
                return result;
            }

            @Override
            protected long fetchCheckInterval(final CircuitBreakerImpl breaker) {
                return breaker.openingInterval;
            }
        }

        private static class StateStrategyOpen extends StateStrategy {
            @Override
            public boolean isStateTransition(final CircuitBreakerImpl breaker,
                                             final CheckIntervalData currentData, final CheckIntervalData nextData) {
                final boolean result =
                        nextData.checkIntervalStart != currentData.checkIntervalStart && currentData.eventCount <= breaker.closingThreshold;
                if (!result) {
                    breaker.openDuration.set(now() - currentData.checkIntervalStart);
                }
                return result;
            }

            @Override
            protected long fetchCheckInterval(final CircuitBreakerImpl breaker) {
                return breaker.closingInterval;
            }
        }
    }
}
