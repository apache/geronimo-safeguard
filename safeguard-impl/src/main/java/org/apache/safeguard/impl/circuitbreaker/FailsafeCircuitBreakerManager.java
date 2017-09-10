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

package org.apache.safeguard.impl.circuitbreaker;

import org.apache.safeguard.api.circuitbreaker.CircuitBreakerManager;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Vetoed;
import java.util.HashMap;
import java.util.Map;

@Vetoed
public class FailsafeCircuitBreakerManager implements CircuitBreakerManager{
    private Map<String, FailsafeCircuitBreaker> circuitBreakers = new HashMap<>();;

    @Override
    public FailsafeCircuitBreakerBuilder newCircuitBreaker(String name) {
        return new FailsafeCircuitBreakerBuilder(name, this);
    }

    @Override
    public FailsafeCircuitBreaker getCircuitBreaker(String name) {
        return circuitBreakers.get(name);
    }

    void register(String name, FailsafeCircuitBreakerDefinition failsafeCircuitBreakerDefinition) {
        this.circuitBreakers.put(name, new FailsafeCircuitBreaker(failsafeCircuitBreakerDefinition));
    }
}
