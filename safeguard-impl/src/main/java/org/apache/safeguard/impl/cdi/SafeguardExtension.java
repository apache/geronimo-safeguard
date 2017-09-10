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

import org.apache.safeguard.api.SafeguardEnabled;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.apache.safeguard.api.SafeguardEnabled.INSTANCE;

public class SafeguardExtension implements Extension {
    public void findFaultTolerantBeans(@Observes @WithAnnotations({Retry.class, CircuitBreaker.class})
                                               ProcessAnnotatedType<?> pat) {
        if (!pat.getAnnotatedType().isAnnotationPresent(SafeguardEnabled.class)) {
            pat.setAnnotatedType(new SafeguardAnnotatedTypeWrapper(pat.getAnnotatedType()));
        }
    }

    private static class SafeguardAnnotatedTypeWrapper<X> implements AnnotatedType<X> {

        private final AnnotatedType<X> delegate;
        private final Set<Annotation> annotations;

        private SafeguardAnnotatedTypeWrapper(AnnotatedType<X> delegate) {
            this.delegate = delegate;
            Set<Annotation> annotations = delegate.getAnnotations();
            Set<Annotation> allAnotations = new LinkedHashSet<>();
            allAnotations.add(INSTANCE);
            allAnotations.addAll(annotations);
            this.annotations = allAnotations;
        }

        @Override
        public Class<X> getJavaClass() {
            return delegate.getJavaClass();
        }

        @Override
        public Set<AnnotatedConstructor<X>> getConstructors() {
            return delegate.getConstructors();
        }

        @Override
        public Set<AnnotatedMethod<? super X>> getMethods() {
            return delegate.getMethods();
        }

        @Override
        public Set<AnnotatedField<? super X>> getFields() {
            return delegate.getFields();
        }

        @Override
        public Type getBaseType() {
            return delegate.getBaseType();
        }

        @Override
        public Set<Type> getTypeClosure() {
            return delegate.getTypeClosure();
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            return SafeguardEnabled.class.equals(annotationType) ? (T) INSTANCE : delegate.getAnnotation(annotationType);
        }

        @Override
        public Set<Annotation> getAnnotations() {
            return Collections.unmodifiableSet(annotations);
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return SafeguardEnabled.class.equals(annotationType) || delegate.isAnnotationPresent(annotationType);
        }
    }
}
