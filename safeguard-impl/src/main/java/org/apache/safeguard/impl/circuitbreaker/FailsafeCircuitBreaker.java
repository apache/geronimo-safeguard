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

import net.jodah.failsafe.CircuitBreaker.State;
import org.apache.safeguard.api.circuitbreaker.CircuitBreaker;
import org.apache.safeguard.api.circuitbreaker.CircuitBreakerState;

public class FailsafeCircuitBreaker implements CircuitBreaker{
    private final FailsafeCircuitBreakerDefinition circuitBreakerDefinition;

    public FailsafeCircuitBreaker(FailsafeCircuitBreakerDefinition circuitBreakerDefinition) {
        this.circuitBreakerDefinition = circuitBreakerDefinition;
    }
    @Override
    public FailsafeCircuitBreakerDefinition getDefinition() {
        return circuitBreakerDefinition;
    }

    @Override
    public CircuitBreakerState getState() {
        State state = circuitBreakerDefinition.getCircuitBreaker().getState();
        switch(state) {
            case OPEN:
                return CircuitBreakerState.OPEN;
            case CLOSED:
                return CircuitBreakerState.CLOSED;
            case HALF_OPEN:
                return CircuitBreakerState.HALF_OPEN;
        }
        throw new RuntimeException("Unknown state "+state);
    }

    public void transitionState(CircuitBreakerState state) {
        switch(state) {
            case OPEN:
                circuitBreakerDefinition.getCircuitBreaker().open();
                break;
            case CLOSED:
                circuitBreakerDefinition.getCircuitBreaker().close();
                break;
            case HALF_OPEN:
                circuitBreakerDefinition.getCircuitBreaker().halfOpen();
                break;
        }
    }
}
