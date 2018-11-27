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
        <groupId>org.apache.geronimo.safeguard</groupId>
        **<artifactId>safeguard-impl</artifactId>**
        <version>1.0</version>
    </dependency>
</dependencies>
```

Apache Safeguard implements the [MicroProfile Fault Tolerance v1.0 specification](https://github.com/eclipse/microprofile-fault-tolerance/releases/tag/1.0)

### Integration

For `@Asynchronous` executor customization you can use:

```java
@ApplicationScoped
public class MyExecutionManagerProvider {
    @Resource
    @Produces
    @Safeguard
    private ManagedScheduledExecutorService executor;
}


```

## Dev tip

To find the interceptor priority you can use this shell command:

`find . -name *Interceptor.java | xargs grep '@Priority'  | sed 's/\([^:]*\):\(.*\)/\2 : \1/g' | sed 's/@Priority(Interceptor.Priority.PLATFORM_AFTER + \([0-9]*\))/priority = \1/' | sort`
