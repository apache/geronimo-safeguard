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

package org.apache.safeguard.circuitbreaker.test;

import org.apache.safeguard.SafeguardCDITest;
import org.apache.safeguard.api.ExecutionManager;
import org.apache.safeguard.api.circuitbreaker.CircuitBreaker;
import org.apache.safeguard.api.circuitbreaker.CircuitBreakerState;
import org.apache.safeguard.impl.util.NamingUtil;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class CDICircuitTest extends SafeguardCDITest{
    @Deployment
    public static Archive<?> createDeployment() {
        return SafeguardCDITest.create(CDICircuitBean.class);
    }

    @Inject
    private CDICircuitBean cdiCircuitBean;

    @Inject
    private ExecutionManager executionManager;

    @Test
    public void shouldTriggerCircuitOpen() throws Exception {
        Method method = CDICircuitBean.class.getMethod("sayHello");
        String name = NamingUtil.createName(method);

        for(int i = 0;i<5;i++) {
            try {
                cdiCircuitBean.sayHello();
                CircuitBreaker circuitBreaker = executionManager.getCircuitBreakerManager().getCircuitBreaker(name);
                if (i < 4) {
                    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
                }
                else {
                    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
                }
            }
            catch (Exception e){ }
        }

        CircuitBreaker circuitBreaker = executionManager.getCircuitBreakerManager().getCircuitBreaker(name);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
    }
}
