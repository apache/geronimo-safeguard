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

package org.apache.safeguard.ft.tck;

import org.apache.safeguard.api.SafeguardEnabled;
import org.apache.safeguard.impl.cdi.FailsafeExecutionManagerProvider;
import org.apache.safeguard.impl.cdi.SafeguardExtension;
import org.apache.safeguard.impl.cdi.SafeguardInterceptor;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import javax.enterprise.inject.spi.Extension;

public class ArchiveAppender implements ApplicationArchiveProcessor {
    private static final StringAsset BEANS_XML = new StringAsset("<beans version=\"1.1\" bean-discovery-mode=\"all\"/>");

    @Override
    public void process(Archive<?> archive, TestClass testClass) {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "safeguard.jar")
                .addClasses(SafeguardEnabled.class, SafeguardExtension.class, FailsafeExecutionManagerProvider.class, SafeguardInterceptor.class)
                .addAsServiceProvider(Extension.class, SafeguardExtension.class)
                .addAsManifestResource(BEANS_XML, "beans.xml");
        archive.as(WebArchive.class).addAsLibrary(jar);
    }
}
