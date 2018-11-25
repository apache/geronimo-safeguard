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

import java.util.concurrent.Executor;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.customizable.Safeguard;
import org.eclipse.microprofile.faulttolerance.Asynchronous;

@Interceptor
@Asynchronous
@Priority(Interceptor.Priority.PLATFORM_AFTER + 1)
public class AsynchronousInterceptor extends BaseAsynchronousInterceptor {
    @Inject
    @Safeguard
    private Executor executor;

    @Override
    protected Executor getExecutor(final InvocationContext context) {
        return executor;
    }

    @AroundInvoke
    public Object async(final InvocationContext context) throws Exception {
        return around(context);
    }
}
