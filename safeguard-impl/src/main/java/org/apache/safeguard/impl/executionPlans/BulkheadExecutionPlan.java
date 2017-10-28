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

package org.apache.safeguard.impl.executionPlans;

import org.apache.safeguard.api.bulkhead.Bulkhead;
import org.apache.safeguard.exception.SafeguardException;

import javax.interceptor.InvocationContext;
import java.util.concurrent.Callable;

public class BulkheadExecutionPlan implements ExecutionPlan{
    private final Bulkhead bulkhead;
    private ExecutionPlan child;

    BulkheadExecutionPlan(Bulkhead bulkhead) {
        this.bulkhead = bulkhead;
    }

    void setChild(ExecutionPlan child) {
        this.child = child;
    }

    @Override
    public <T> T execute(Callable<T> callable, InvocationContext invocationContext) {
        if(bulkhead != null && child != null) {
            return child.execute(() -> bulkhead.execute(callable), invocationContext);
        } else if(child != null) {
            return child.execute(callable, invocationContext);
        } else {
            throw new SafeguardException("Neither bulkhead nor child specified");
        }
    }
}
