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

package org.apache.safeguard.impl.retry;

import org.apache.safeguard.api.retry.RetryManager;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class FailsafeRetryManager implements RetryManager{
    private Map<String, FailsafeRetryDefinition> retries;

    @PostConstruct
    public void init() {
        retries = new HashMap<>();
    }

    @Override
    public FailsafeRetryBuilder newRetryDefinition(String name) {
        return new FailsafeRetryBuilder(name, this);
    }

    @Override
    public FailsafeRetryDefinition getRetryDefinition(String name) {
        return retries.get(name);
    }

    void register(String name, FailsafeRetryDefinition failsafeRetryDefinition) {
        this.retries.put(name, failsafeRetryDefinition);
    }
}
