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
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;

@ApplicationScoped
public class AnnotationFinder {
    @Inject
    private BeanManager manager;

    public <T extends Annotation> T findAnnotation(final Class<T> type, final AnnotatedType<?> declaringClass,
                                                   final Method method) {
        final Set<AnnotatedMethod<?>> methods = (Set<AnnotatedMethod<?>>) declaringClass.getMethods();
        // first the methods of the declaring class, then the class then the other methods (parent)
        return methods.stream()
                      .filter(it -> declaringClass.equals(it.getDeclaringType()) && matches(method, it))
                      .findFirst()
                      .map(m -> m.getAnnotation(type))
                      .orElseGet(() -> ofNullable(declaringClass.getAnnotation(type))
                      .orElseGet(() -> methods.stream()
                      .filter(it -> !declaringClass.equals(it.getDeclaringType()) && matches(method, it))
                      .findFirst()
                      .map(m -> m.getAnnotation(type))
                      .orElseGet(() -> getByReflection(type, declaringClass, method))));
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
        Class<?> target = ofNullable(context.getTarget())
                .map(Object::getClass)
                .orElseGet(() -> Class.class.cast(context.getMethod().getDeclaringClass()));
        while (target.getName().contains("$$")) {
            target = target.getSuperclass();
        }
        return findAnnotation(type, manager.createAnnotatedType(target), context.getMethod());
    }
}
