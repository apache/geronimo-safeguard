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

package org.apache.safeguard.impl.cdi;

import org.apache.safeguard.exception.SafeguardException;
import org.apache.safeguard.impl.util.AnnotationUtil;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

@Vetoed
final class MicroProfileValidator {
    private List<Throwable> capturedThrowables = new ArrayList<>();

    void parse(AnnotatedType<?> annotatedType) {
        for(AnnotatedMethod<?> method : annotatedType.getMethods()) {
            Retry retry = AnnotationUtil.getAnnotation(method, annotatedType, Retry.class);
            if (retry != null) {
                validateRetry(retry, method);
            }
            Bulkhead bulkhead = AnnotationUtil.getAnnotation(method, annotatedType, Bulkhead.class);
            if (bulkhead != null) {
                validateBulkhead(bulkhead, method);
            }
            Timeout timeout = AnnotationUtil.getAnnotation(method, annotatedType, Timeout.class);
            if (timeout != null) {
                validateTimeout(timeout, method);
            }
            CircuitBreaker circuitBreaker = AnnotationUtil.getAnnotation(method, annotatedType, CircuitBreaker.class);
            if (circuitBreaker != null) {
                validateCircuitBreaker(circuitBreaker, method);
            }
            Fallback fallback = AnnotationUtil.getAnnotation(method, annotatedType, Fallback.class);
            if (fallback != null) {
                validateFallback(fallback, method, annotatedType);
            }
        }
    }

    private void validateFallback(Fallback fallback, AnnotatedMethod<?> method, AnnotatedType<?> annotatedType) {
        if(fallback.fallbackMethod().equals("") && fallback.value().equals(Fallback.DEFAULT.class)) {
            capturedThrowables.add(new SafeguardException("Invalid Fallback definition on method " + method));
        }
        else if(!fallback.fallbackMethod().equals("") && !fallback.value().equals(Fallback.DEFAULT.class)) {
            capturedThrowables.add(new SafeguardException("Invalid Fallback definition on method " + method));
        }
        else if(!fallback.fallbackMethod().equals("")) {
            boolean found = false;
            for(AnnotatedMethod<?> otherMethod : annotatedType.getMethods()) {
                if(otherMethod.getJavaMember().getName().equals(fallback.fallbackMethod())) {
                    found = true;
                    if(!method.getJavaMember().getReturnType().equals(otherMethod.getJavaMember().getReturnType())) {
                        capturedThrowables.add(new SafeguardException("Invalid Fallback definition on method " + method +
                                " wrong return type"));
                    } else if(!Arrays.equals(method.getJavaMember().getParameterTypes(), otherMethod.getJavaMember().getParameterTypes())) {
                        capturedThrowables.add(new SafeguardException("Invalid Fallback definition on method " + method +
                                " wrong parameters"));
                    }
                }
            }
            if (!found) {
                capturedThrowables.add(new SafeguardException("Invalid Fallback definition on method " + method +
                        " fallback method not found"));
            }
        }
        else {
            try {
                Method methodHandle = fallback.value().getMethod("handle", ExecutionContext.class);
                if(!methodHandle.getReturnType().equals(method.getJavaMember().getReturnType())) {
                    capturedThrowables.add(new SafeguardException("Invalid Fallback definition on method " + method +
                            " wrong return type"));
                }
            } catch (NoSuchMethodException e) {
                capturedThrowables.add(new SafeguardException("Invalid Fallback definition on method " + method +
                        " fallback method not found"));
            }
        }
    }

    private void validateTimeout(Timeout timeout, AnnotatedMethod<?> method) {
        if(timeout.value() < 0) {
            capturedThrowables.add(new SafeguardException("Invalid Timeout definition on method " + method));
        }
    }

    private void validateCircuitBreaker(CircuitBreaker circuitBreaker, AnnotatedMethod<?> method) {
        if(circuitBreaker.requestVolumeThreshold() <= 0 || circuitBreaker.delay() < 0 || circuitBreaker.failureRatio() < 0.0
                || circuitBreaker.failureRatio() > 1.0 || circuitBreaker.successThreshold() <= 0) {
            capturedThrowables.add(new SafeguardException("Invalid CircuitBreaker definition on method " + method));
        }
    }

    private void validateRetry(Retry retry, AnnotatedMethod<?> method) {
        if(retry.jitter() < 0 || retry.maxDuration() < 0 || retry.delay() < 0 || retry.maxRetries() < 0) {
            capturedThrowables.add(new SafeguardException("Invalid Retry definition on method " + method));
        }
        Duration delay = Duration.of(retry.delay(), retry.delayUnit());
        Duration maxDuration = Duration.of(retry.maxDuration(), retry.durationUnit());
        if(maxDuration.compareTo(delay) < 0) {
            capturedThrowables.add(new SafeguardException("Invalid Retry definition on method " + method));
        }
    }

    private void validateBulkhead(Bulkhead bulkhead, AnnotatedMethod<?> method) {
        if(bulkhead.value() < 0 || bulkhead.waitingTaskQueue() < 0) {
            capturedThrowables.add(new SafeguardException("Invalid Bulkhead definition on method " + method));
        }
    }

    void forThrowable(Consumer<Throwable> consumer) {
        capturedThrowables.forEach(consumer);
    }
}
