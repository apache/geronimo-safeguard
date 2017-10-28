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
import org.apache.safeguard.api.bulkhead.BulkheadBuilder;
import org.apache.safeguard.api.bulkhead.BulkheadManager;

import java.util.HashMap;
import java.util.Map;

public class BulkheadManagerImpl implements BulkheadManager{
    private Map<String, Bulkhead> bulkheads = new HashMap<>();;
    @Override
    public BulkheadBuilder newBulkheadBuilder(String name) {
        return new BulkheadBuilderImpl(name, this);
    }

    @Override
    public Bulkhead getBulkhead(String name) {
        return bulkheads.get(name);
    }

    void register(String name, BulkheadDefinitionImpl bulkheadDefinition) {
        Bulkhead bulkhead;
        if (bulkheadDefinition.isAsynchronous()) {
            bulkhead = new ThreadPoolBulkhead(bulkheadDefinition);
        }
        else {
            bulkhead = new SemaphoreBulkhead(bulkheadDefinition);
        }
        bulkheads.put(name, bulkhead);
    }
}
