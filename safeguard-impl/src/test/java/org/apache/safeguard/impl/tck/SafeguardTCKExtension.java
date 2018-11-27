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

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import javax.enterprise.inject.spi.CDI;

import org.apache.safeguard.impl.circuitbreaker.CircuitBreakerInterceptor;
import org.apache.webbeans.spi.ContainerLifecycle;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.jboss.arquillian.container.spi.client.deployment.DeploymentDescription;
import org.jboss.arquillian.container.test.impl.client.deployment.AnnotationDeploymentScenarioGenerator;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentScenarioGenerator;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.event.suite.After;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

public class SafeguardTCKExtension implements LoadableExtension {

    @Override
    public void register(final ExtensionBuilder extensionBuilder) {
        extensionBuilder
                .service(ApplicationArchiveProcessor.class, ArchiveAppender.class)
                .override(DeploymentScenarioGenerator.class, AnnotationDeploymentScenarioGenerator.class, EnrichableGenerator.class)
                .observer(LeakingTCKWorkaround.class);
    }

    public static class ArchiveAppender implements ApplicationArchiveProcessor {

        private Archive<?> createAuxiliaryArchive() {
            return ShrinkWrap.create(JavaArchive.class, "safeguard-impl.jar")
                             .addPackages(true, "org.apache.safeguard")
                             .addAsManifestResource(new FileAsset(new File("src/main/resources/META-INF/beans.xml")), "beans.xml");
        }

        @Override
        public void process(final Archive<?> auxiliaryArchive, final TestClass testClass) {
            if (auxiliaryArchive.getName().endsWith(".war")) {
                auxiliaryArchive.as(WebArchive.class).addAsLibrary(createAuxiliaryArchive());
            }
        }
    }

    public static class LeakingTCKWorkaround {

        @Inject
        private Instance<ContainerLifecycle> lifecycle;

        public void clearCircuitBreakerCache(@Observes(precedence = -1) final After event) {
            final CDI<Object> cdi = CDI.current();
            final MetricRegistry registry = cdi.select(MetricRegistry.class).get();
            cdi.select(CircuitBreakerInterceptor.Cache.class).get().getCircuitBreakers().clear();
            Stream.concat(registry.getGauges().keySet().stream(), registry.getCounters().keySet().stream())
               .filter(it -> it.startsWith("ft.") && it.startsWith(".circuitbreaker."))
               .forEach(registry::remove);
        }
    }

    public static class EnrichableGenerator extends AnnotationDeploymentScenarioGenerator {
        @Override
        public List<DeploymentDescription> generate(final TestClass testClass) {
            return super.generate(testClass).stream().peek(it -> it.shouldBeTestable(true)).collect(toList());
        }
    }
}
