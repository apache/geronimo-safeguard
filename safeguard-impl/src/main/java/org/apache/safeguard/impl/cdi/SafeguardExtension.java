package org.apache.safeguard.impl.cdi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;

import org.apache.safeguard.impl.config.GeronimoFaultToleranceConfig;
import org.apache.safeguard.impl.customizable.Safeguard;
import org.apache.safeguard.impl.fallback.FallbackInterceptor;

// todo: mp.fault.tolerance.interceptor.priority handling
public class SafeguardExtension implements Extension {
    private boolean foundExecutor;

    void addFallbackInterceptor(@Observes final ProcessAnnotatedType<FallbackInterceptor> processAnnotatedType) {
        processAnnotatedType.configureAnnotatedType().add(new FallbackBinding());
    }

    void onBean(@Observes final ProcessBean<?> bean) {
        if (bean.getBean().getQualifiers().stream().anyMatch(it -> it.annotationType() == Safeguard.class)
            && bean.getBean().getTypes().stream().anyMatch(it -> Executor.class.isAssignableFrom(toClass(it)))) {
            foundExecutor = true;
        }
    }

    void addMissingBeans(@Observes final AfterBeanDiscovery afterBeanDiscovery) {
        final GeronimoFaultToleranceConfig config = GeronimoFaultToleranceConfig.create();
        afterBeanDiscovery.addBean()
                .id("geronimo_safeguard#configuration")
                .types(GeronimoFaultToleranceConfig.class, Object.class)
                .beanClass(GeronimoFaultToleranceConfig.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .createWith(c -> config);

        if (!foundExecutor) {
            afterBeanDiscovery.addBean()
                    .id("geronimo_safeguard#executor")
                    .types(Executor.class, Object.class)
                    .beanClass(Executor.class)
                    .qualifiers(Safeguard.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .createWith(c -> Executors.newCachedThreadPool())
                    .destroyWith((e, c) -> ExecutorService.class.cast(e).shutdownNow());
        }
    }

    public Class<?> toClass(final Type it) {
        return doToClass(it, 0);
    }

    private Class<?> doToClass(final Type it, final int iterations) {
        if (Class.class.isInstance(it)) {
            return Class.class.cast(it);
        }
        if (iterations > 100) { // with generic it happens we can loop here
            return Object.class;
        }
        if (ParameterizedType.class.isInstance(it)) {
            return doToClass(ParameterizedType.class.cast(it).getRawType(), iterations + 1);
        }
        return Object.class; // will not match any of our test
    }
}
