/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.safeguard.impl.tck;

import javax.inject.Inject;

import org.apache.safeguard.impl.tck.retry.ZeoJitterBean;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.annotations.Test;

public class RetryZeroJitterTest extends Arquillian {
    @Inject
    private ZeoJitterBean bean;

    @Deployment
    public static WebArchive archive() {
        return ShrinkWrap
                .create(WebArchive.class, "RetryZeroJitterTest.war")
                .addAsLibrary(ShrinkWrap
                        .create(JavaArchive.class, "RetryZeroJitterTest.jar")
                        .addClass(ZeoJitterBean.class)
                        .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                        .as(JavaArchive.class));
    }

    @Test
    public void testJitterAtZero() {
        bean.execute(); // just ensure it passes
    }
}
