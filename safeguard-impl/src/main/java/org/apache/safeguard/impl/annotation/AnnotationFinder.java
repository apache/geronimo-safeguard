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
import java.util.Arrays;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;

@ApplicationScoped
public class AnnotationFinder {
    @Inject
    private BeanManager manager;

    public <T extends Annotation> T findAnnotation(final Class<T> type, final InvocationContext context) {
        Class<?> target = context.getTarget().getClass();
        while (target.getName().contains("$$")) {
            target = target.getSuperclass();
        }
        return manager.createAnnotatedType(target).getMethods().stream()
                .filter(it -> it.getJavaMember().getName().equals(context.getMethod().getName()) &&
                        Arrays.equals(it.getJavaMember().getParameterTypes(), context.getMethod().getParameterTypes()))
                .findFirst()
                .map(m -> m.getAnnotation(type))
                .orElseGet(() -> ofNullable(context.getMethod().getAnnotation(type))
                        .orElseGet(() -> context.getTarget().getClass().getAnnotation(type)));
    }
}
