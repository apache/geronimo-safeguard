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

package org.apache.safeguard.impl.config;

import org.apache.safeguard.api.config.ConfigFacade;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerBuilder;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreakerDefinition;
import org.apache.safeguard.impl.retry.FailsafeRetryBuilder;
import org.apache.safeguard.impl.retry.FailsafeRetryDefinition;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

import javax.enterprise.inject.Vetoed;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

@Vetoed
public class MicroprofileAnnotationMapper {
    private static MicroprofileAnnotationMapper INSTANCE = new MicroprofileAnnotationMapper();
    private final ConfigFacade configFacade;

    private MicroprofileAnnotationMapper() {
        this(ConfigFacade.getInstance());
    }
    public MicroprofileAnnotationMapper(ConfigFacade configFacade) {
        this.configFacade = configFacade;
    }

    private static final String RETRY_CLASS_FORMAT = "%s/Retry/%s";
    private static final String RETRY_METHOD_FORMAT = "%s/%s/Retry/%s";
    private static final String CIRCUIT_BREAKER_CLASS_FORMAT = "%s/CircuitBreaker/%s";
    private static final String CIRCUIT_BREAKER_METHOD_FORMAT = "%s/%s/CircuitBreaker/%s";
    public FailsafeRetryDefinition mapRetry(Method method, Retry retry, FailsafeRetryBuilder retryBuilder) {
        boolean methodLevel = method.isAnnotationPresent(Retry.class);
        int maxRetries = getRetryValue(method, "maxRetries", methodLevel, retry.maxRetries());
        Class[] retryOn = getRetryValue(method, "retryOn", methodLevel, retry.retryOn());
        Class[] abortOn = getRetryValue(method, "abortOn", methodLevel, retry.abortOn());

        long delay = getRetryValue(method, "delay", methodLevel, retry.delay());
        ChronoUnit delayUnit = getRetryValue(method, "delayUnit", methodLevel, retry.delayUnit());

        long jitter = getRetryValue(method, "jitter", methodLevel, retry.jitter());
        ChronoUnit jitterUnit = getRetryValue(method, "jitterDelayUnit", methodLevel, retry.jitterDelayUnit());

        long maxDuration = getRetryValue(method, "maxDuration", methodLevel, retry.maxDuration());
        ChronoUnit maxDurationUnit = getRetryValue(method, "durationUnit", methodLevel, retry.durationUnit());

        retryBuilder.withMaxRetries(maxRetries)
                .withRetryOn(retryOn)
                .withRetryOn(TimeoutException.class, org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class)
                .withAbortOn(abortOn);
        if (delay > 0L) {
            retryBuilder.withDelay(Duration.of(delay, delayUnit));
        }
        if (jitter > 0L) {
            retryBuilder.withJitter(Duration.of(jitter, jitterUnit));
        }
        if (maxDuration > 0L) {
            retryBuilder.withMaxDuration(Duration.of(maxDuration, maxDurationUnit));
        }
        return retryBuilder.build();
    }

    public FailsafeCircuitBreakerDefinition mapCircuitBreaker(Method method, CircuitBreaker circuitBreaker,
                                                              FailsafeCircuitBreakerBuilder builder) {
        boolean methodLevel = method.isAnnotationPresent(CircuitBreaker.class);

        double failureRatio = getCBValue(method, "failureRatio", methodLevel, circuitBreaker.failureRatio());
        int requestVolumeThreshold = getCBValue(method, "requestVolumeThreshold", methodLevel, circuitBreaker.requestVolumeThreshold());
        Class[] failOn = getCBValue(method, "failOn", methodLevel, circuitBreaker.failOn());
        long delay = getCBValue(method, "delay", methodLevel, circuitBreaker.delay());
        ChronoUnit delayUnit = getCBValue(method, "delayUnit", methodLevel, circuitBreaker.delayUnit());
        int successThreshold = getCBValue(method, "successThreshold", methodLevel, circuitBreaker.successThreshold());

        int failureCount = (int) (failureRatio * requestVolumeThreshold);
        FailsafeCircuitBreakerBuilder failsafeCircuitBreakerBuilder = builder
                .withFailOn(failOn)
                .withFailOn(TimeoutException.class, org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class)
                .withDelay(Duration.of(delay, delayUnit))
                .withSuccessCount(successThreshold);
        if (failureCount > 0) {
            failsafeCircuitBreakerBuilder.withFailures(failureCount, requestVolumeThreshold);
        }
        return failsafeCircuitBreakerBuilder.build();
    }

    public static MicroprofileAnnotationMapper getInstance() {
        return INSTANCE;
    }

    public static void setInstance(MicroprofileAnnotationMapper microprofileAnnotationMapper) {
        INSTANCE = microprofileAnnotationMapper;
    }

    // retry config
    private int getRetryValue(Method method, String name, boolean isMethod, int defaultValue) {
        String methodKey = String.format(RETRY_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        int value = configFacade.getInt(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(RETRY_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getInt(classKey, defaultValue);
    }

    private long getRetryValue(Method method, String name, boolean isMethod, long defaultValue) {
        String methodKey = String.format(RETRY_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        long value = configFacade.getLong(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(RETRY_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getLong(classKey, defaultValue);
    }

    private ChronoUnit getRetryValue(Method method, String name, boolean isMethod, ChronoUnit defaultValue) {
        String methodKey = String.format(RETRY_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        ChronoUnit value = configFacade.getChronoUnit(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(RETRY_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getChronoUnit(classKey, defaultValue);
    }

    private Class[] getRetryValue(Method method, String name, boolean isMethod, Class[] defaultValue) {
        String methodKey = String.format(RETRY_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        Class[] value = configFacade.getThrowableClasses(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(RETRY_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getThrowableClasses(classKey, defaultValue);
    }
    // circuit breaker config
    private int getCBValue(Method method, String name, boolean isMethod, int defaultValue) {
        String methodKey = String.format(CIRCUIT_BREAKER_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        int value = configFacade.getInt(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(CIRCUIT_BREAKER_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getInt(classKey, defaultValue);
    }

    private long getCBValue(Method method, String name, boolean isMethod, long defaultValue) {
        String methodKey = String.format(CIRCUIT_BREAKER_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        long value = configFacade.getLong(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(CIRCUIT_BREAKER_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getLong(classKey, defaultValue);
    }

    private double getCBValue(Method method, String name, boolean isMethod, double defaultValue) {
        String methodKey = String.format(CIRCUIT_BREAKER_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        double value = configFacade.getDouble(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(CIRCUIT_BREAKER_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getDouble(classKey, defaultValue);
    }

    private ChronoUnit getCBValue(Method method, String name, boolean isMethod, ChronoUnit defaultValue) {
        String methodKey = String.format(CIRCUIT_BREAKER_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        ChronoUnit value = configFacade.getChronoUnit(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(CIRCUIT_BREAKER_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getChronoUnit(classKey, defaultValue);
    }

    private Class[] getCBValue(Method method, String name, boolean isMethod, Class[] defaultValue) {
        String methodKey = String.format(CIRCUIT_BREAKER_METHOD_FORMAT, method.getDeclaringClass().getName(), method.getName(), name);
        Class[] value = configFacade.getThrowableClasses(methodKey, defaultValue);
        if(value != defaultValue || isMethod) {
            return value;
        }
        String classKey = String.format(CIRCUIT_BREAKER_CLASS_FORMAT, method.getDeclaringClass().getName(), name);
        return configFacade.getThrowableClasses(classKey, defaultValue);
    }
}
