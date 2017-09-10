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

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

import javax.enterprise.context.ApplicationScoped;
import java.util.concurrent.Callable;

@ApplicationScoped
public class CDISimpleCallable implements Callable<Object> {
    private int counter = 0;

    @Override
    @Retry(maxRetries = 7, retryOn = RuntimeException.class)
    @CircuitBreaker(successThreshold = 2, failureRatio = 0.75, requestVolumeThreshold = 4, delay = 50000)
    public Object call() throws Exception {
        counter++;
        throw new RuntimeException("Invalid state");
    }

    public int getCounter() {
        return counter;
    }
}
