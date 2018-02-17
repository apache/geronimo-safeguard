# Apache Safeguard
Apache Safeguard is a library around Fault Tolerance, giving your application resilience in a highly distributed microservices framework. It is designed for use with CDI applications.

## What is Fault Tolerance?

In today's microservices runtimes, Fault Tolerance allows an application to handle the situations where another service it is consuming is unavialable.  

## Types of Fault Tolerance

Fault Tolerance is a broad subject, has multiple areas of support typically found in a framework.

### Fallback

When a given invocation fails, you can declare a Fallback for that method.

### Timeout

Allows method invocations to be bounded to a specific duration.  Once that boundary hits, an exception is thrown or a fallback is invoked.

### Retry

Allows a method to be invoked a number of times, as well as for a given duration.

### Circuit Breaker

Allows invocations to a given method as long as it is returning successfully.  Based on thresholds defined, when a method begins failing invocations will be blocked.  After a duration has passed invocations will begin to attempt again.

### Bulkhead

A bulkhead throttles concurrent access to a method.  The throttling can either by based on a semaphore or a thread pool.  Semaphores are invoked on the caller's thread and are not bound by any limit to pending requests.  Thread pools are used to invoke the component asynchronously and have a finite amount of waiting invocations.

## Getting Started

Apache Safeguard is currently in development; however a 1.0 release was created that passes the MicroProfile Fault Tolerance TCK.  You can add the following dependencies to your project:

```xml
<dependencies>
    <dependency>
        <artifactId>safeguard-api</artifactId>
        <groupId>org.apache.geronimo.safeguard</groupId>
        <version>1.0</version>
    </dependency>
    <dependency>
        <artifactId>safeguard-impl</artifactId>
        <groupId>org.apache.geronimo.safeguard</groupId>
        <version>1.0</version>
    </dependency>
</dependencies>
```

Apache Safeguard implements the [MicroProfile Fault Tolerance v1.0 specification](https://github.com/eclipse/microprofile-fault-tolerance/releases/tag/1.0)

### Integration

The core of Safeguard is wrapped around an `ExecutionManager` which takes care of coordinating and storing the execution state of various methods.  It allows some configurability, but if you want to change it your best solution is to create an alternative of `ExecutionManager` with your customizations.  For instance, in an EE environment you may want to use a `ManagedScheduledExecutorService` which could be done:

```java
@ApplicationScoped
@Specializes
@Priority(100)
public class MyExecutionManagerProvider extends FailsafeExecutionManagerProvider{
    @Resource
    private ManagedScheduledExecutorService executorService;
    @Produces
    @ApplicationScoped
    public ExecutionManager createExecutionManager() {
        FailsafeCircuitBreakerManager circuitBreakerManager = new FailsafeCircuitBreakerManager();
        FailsafeRetryManager retryManager = new FailsafeRetryManager();
        BulkheadManagerImpl bulkheadManager = new BulkheadManagerImpl();
        DefaultExecutorServiceProvider executorServiceProvider = new DefaultExecutorServiceProvider(executorService);
        ExecutionPlanFactory executionPlanFactory = new ExecutionPlanFactory(circuitBreakerManager, retryManager, bulkheadManager, mapper,
                executorServiceProvider);
        return FailsafeExecutionManager(MicroprofileAnnotationMapper.getInstance(), bulkheadManager, circuitBreakerManager, 
                retryManager, executionPlanFactory, executorServiceProvider);
    }
}


```