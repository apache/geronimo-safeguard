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

package org.apache.safeguard.circuitretry.test;

import org.apache.safeguard.SafeguardCDITest;
import org.apache.safeguard.api.circuitbreaker.CircuitBreakerState;
import org.apache.safeguard.impl.FailsafeExecutionManager;
import org.apache.safeguard.impl.circuitbreaker.FailsafeCircuitBreaker;
import org.apache.safeguard.impl.util.NamingUtil;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class CDIRetryCircuitTest extends SafeguardCDITest{

    @Deployment
    public static Archive<?> createDeployment() {
        return SafeguardCDITest.create(CDISimpleCallable.class);
    }

    @Inject
    private CDISimpleCallable simpleCallable;

    @Inject
    private FailsafeExecutionManager executionManager;

    @Test
    public void shouldExecuteSevenTimes() throws Exception{
        try {
            simpleCallable.call();
        } catch (Exception e) {
        }

        Method method = CDISimpleCallable.class.getMethod("call");
        String name = NamingUtil.createName(method);
        FailsafeCircuitBreaker circuitBreaker = executionManager.getCircuitBreakerManager().getCircuitBreaker(name);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(simpleCallable.getCounter()).isEqualTo(4);
    }

}
