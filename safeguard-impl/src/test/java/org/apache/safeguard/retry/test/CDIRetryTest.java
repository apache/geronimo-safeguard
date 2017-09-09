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
import org.apache.safeguard.impl.cdi.SafeguardExtension;
import org.apache.safeguard.impl.cdi.SafeguardInterceptor;
import org.apache.safeguard.impl.retry.FailsafeRetryManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.testng.annotations.Test;

import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class CDIRetryTest extends Arquillian{
    @Deployment
    public static Archive<?> create() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClasses(GuardedExecutions.class, FailsafeRetryManager.class,
                        SafeguardInterceptor.class, CDIRetryBean.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsServiceProviderAndClasses(Extension.class, SafeguardExtension.class);
    }

    @Inject
    private CDIRetryBean cdiRetryBean;

    @Test
    public void shouldExecuteThreeTimesWithAnnotations() {
        cdiRetryBean.doCall();

        assertThat(cdiRetryBean.getCalls()).isEqualTo(3);
    }
}
