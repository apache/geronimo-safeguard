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

import org.apache.safeguard.api.bulkhead.Bulkhead;
import org.apache.safeguard.api.bulkhead.BulkheadDefinition;
import org.apache.safeguard.exception.SafeguardException;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

public class SemaphoreBulkhead implements Bulkhead {
    private final Semaphore semaphore;
    private final BulkheadDefinition bulkheadDefinition;

    public SemaphoreBulkhead(BulkheadDefinition bulkheadDefinition) {
        this.semaphore = new Semaphore(bulkheadDefinition.getMaxConcurrentExecutions(), true);
        this.bulkheadDefinition = bulkheadDefinition;
    }

    @Override
    public BulkheadDefinition getBulkheadDefinition() {
        return this.bulkheadDefinition;
    }

    @Override
    public int getCurrentQueueDepth() {
        return semaphore.getQueueLength();
    }

    @Override
    public int getCurrentExecutions() {
        return bulkheadDefinition.getMaxConcurrentExecutions() - semaphore.availablePermits();
    }

    @Override
    public <T> T execute(Callable<T> callable) {
        try {
            String name = Thread.currentThread().getName();
            this.semaphore.acquire();
            return callable.call();
        } catch (Exception e) {
            throw new SafeguardException(e);
        } finally {
            this.semaphore.release();
        }
    }
}
