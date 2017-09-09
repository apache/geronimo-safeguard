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

package org.apache.safeguard.retry.test;

import org.apache.safeguard.impl.GuardedExecutions;
import org.apache.safeguard.impl.retry.FailsafeRetryManager;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RetryTest {
    private GuardedExecutions guardedExecutions;
    private static final String name = "GUARDED_RETRIES";
    private FailsafeRetryManager retryManager;

    @BeforeTest
    public void setupForTest() {
        this.retryManager = new FailsafeRetryManager();
        retryManager.init();
        guardedExecutions = new GuardedExecutions(retryManager);
    }

    @Test
    public void testRetryWithManualBuild() {
        int expectedCalls = 3;
        retryManager.newRetryDefinition(name)
                .withMaxRetries(expectedCalls)
                .build();
        RetryBean retryBean = new RetryBean();

        guardedExecutions.execute(name, retryBean);

        assertThat(retryBean.getCalls()).isEqualTo(expectedCalls);
    }
}
