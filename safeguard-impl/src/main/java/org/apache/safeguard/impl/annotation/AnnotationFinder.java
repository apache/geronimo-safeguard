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
package org.apache.safeguard.impl.annotation;

import static java.util.Optional.ofNullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.cache.UnwrappedCache;

@ApplicationScoped
public class AnnotationFinder {
    @Inject
    private BeanManager manager;

    @Inject
    private UnwrappedCache unwrappedCache;

    public <T extends Annotation> T findAnnotation(final Class<T> type, final AnnotatedType<?> declaringClass,
                                                   final Method method) {
        final Set<AnnotatedMethod<?>> methods = (Set<AnnotatedMethod<?>>) declaringClass.getMethods();

        // first test on the class method directly
        final Optional<AnnotatedMethod<?>> classMethod = getDirectMethod(declaringClass, method, methods);
        if (classMethod.isPresent()) {
            final T methodAnnotation = getMethodAnnotation(type, classMethod);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
        } else { // then on the parent methods
            final Optional<AnnotatedMethod<?>> parentMethod = getParentMethod(declaringClass, method, methods);
            final T annotation = getMethodAnnotation(type, parentMethod);
            if (annotation != null) {
                return annotation;
            }
        }
        { // then on the class
            final T annotation = getFromDirectClass(type, declaringClass);
            if (annotation != null) {
                return annotation;
            }
        }
        { // then on the parent class
            final T annotation = getFromParentClass(type, declaringClass);
            if (annotation != null) {
                return annotation;
            }
        }
        // just a fallback, we should never end up here
        return getByReflection(type, declaringClass, method);
    }

    private <T extends Annotation> T getFromDirectClass(final Class<T> type, final AnnotatedType<?> declaringClass) {
        final T annotation = declaringClass.getAnnotation(type);
        if (annotation != null) {
            return annotation;
        }
        return null;
    }

    private Optional<AnnotatedMethod<?>> getParentMethod(final AnnotatedType<?> declaringClass, final Method method,
                                                         final Set<AnnotatedMethod<?>> methods) {
        return methods.stream()
            .filter(it -> !declaringClass.getJavaClass().equals(it.getJavaMember().getDeclaringClass()) && matches(method, it))
            .findFirst();
    }

    private <T extends Annotation> T getMethodAnnotation(final Class<T> type, final Optional<AnnotatedMethod<?>> classMethod) {
        if (classMethod.isPresent()) {
            final T annotation = classMethod.orElseThrow(IllegalArgumentException::new).getAnnotation(type);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private Optional<AnnotatedMethod<?>> getDirectMethod(final AnnotatedType<?> declaringClass, final Method method,
                                                         final Set<AnnotatedMethod<?>> methods) {
        return methods.stream()
            .filter(it -> declaringClass.getJavaClass().equals(it.getJavaMember().getDeclaringClass()) && matches(method, it))
            .findFirst();
    }

    private <T extends Annotation> T getFromParentClass(final Class<T> type, final AnnotatedType<?> declaringClass) {
        final Class<?> parent = declaringClass.getJavaClass().getSuperclass();
        if (parent != null && parent != Object.class) {
            final AnnotatedType<?> annotatedType = manager.createAnnotatedType(parent);
            final T annotation = annotatedType.getAnnotation(type);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private boolean matches(final Method method, final AnnotatedMethod<?> it) {
        return it.getJavaMember().getName().equals(method.getName()) &&
                Arrays.equals(it.getJavaMember().getParameterTypes(), method.getParameterTypes());
    }

    private <T extends Annotation> T getByReflection(final Class<T> type,
                                                     final AnnotatedType<?> declaringClass,
                                                     final Method method) {
        return ofNullable(method.getAnnotation(type))
                .orElseGet(() -> declaringClass.getAnnotation(type));
    }

    public <T extends Annotation> T findAnnotation(final Class<T> type, final InvocationContext context) {
        // normally we should use target but validations require the fallback
        // todo: don't rely on context but AnnotatedType?
        final Class<?> target = ofNullable(context.getTarget())
                .flatMap(it -> UnwrappedCache.Tool.unwrap(unwrappedCache.getUnwrappedCache(), it))
                .orElseGet(() -> Class.class.cast(context.getMethod().getDeclaringClass()));
        return findAnnotation(type, manager.createAnnotatedType(target), context.getMethod());
    }
}
