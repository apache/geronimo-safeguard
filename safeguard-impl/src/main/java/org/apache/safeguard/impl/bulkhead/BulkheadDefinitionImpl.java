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

package org.apache.safeguard.impl.bulkhead;

import org.apache.safeguard.api.bulkhead.BulkheadDefinition;

public class BulkheadDefinitionImpl implements BulkheadDefinition{
    private final int maxConcurrentExecutions;
    private final int maxWaitingExecutions;
    private final boolean asynchronous;

    public BulkheadDefinitionImpl(int maxConcurrentExecutions, int maxWaitingExecutions, boolean asynchronous) {
        this.maxConcurrentExecutions = maxConcurrentExecutions;
        this.maxWaitingExecutions = maxWaitingExecutions;
        this.asynchronous = asynchronous;
    }

    @Override
    public int getMaxConcurrentExecutions() {
        return maxConcurrentExecutions;
    }

    @Override
    public int getMaxWaitingExecutions() {
        return maxWaitingExecutions;
    }

    @Override
    public boolean isAsynchronous() {
        return asynchronous;
    }
}
