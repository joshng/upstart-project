```
  ___ _    _ ___            _             _   
 |  _| |  | |_  |          | |           | |  
 | | | |  | | | | _ __  ___| |_ __ _ _ __| |_ 
 | | | |  | | | || '_ \/ __| __/ _` | '__| __|
 | | | |__| | | || |_) \__ \ || (_| | |  | |_ 
 | |_ \____/ _| || .__/|___/\__\__,_|_|   \__|
 |___|      |___||_|                          
                                             
```

A lightweight framework for managing sophisticated applications

# Table of Contents
   
* [Big Picture](#big-picture)
    - [To construct an `UpstartApplication`...](#to-construct-an--upstartapplication-)
* [Development Process](#development-process)
* [Modules](#modules)
* [Dependency-injection and Lifecycles with Guice and Services](#dependency-injection-and-lifecycles-with-guice-and-services)
  + [Handling Unexpected Exceptions](#handling-unexpected-exceptions)
* [hojack, and @ConfigPath: configuration with HOCON, Jackson, and @Inject](#hojack--and--configpath--configuration-with-hocon--jackson--and--inject)
* [Deployment Stages](#deployment-stages)
* [Configuration Resolution: Files and Precedence](#configuration-resolution--files-and-precedence)
* [Compose configuration with `include` and `${variable.interpolation}`](#compose-configuration-with--include--and----variableinterpolation--)
* [Logging configuration](#logging-configuration)
* [Testing with upstart](#testing-with-upstart)
* [javalin/pippo web-server configuration with WebInitializers](#javalin-pippo-web-server-configuration-with-webinitializers)
* [UpstartStaticInitializers](#upstartstaticinitializers)
* [AOP interception and InterceptorBinders](#aop-interception-and-interceptorbinders)
* [Metrics](#metrics)

## Big Picture

Upstart provides a group of features that work well together as a scaffolding
for building sustainable applications. These features include:

- a Service-Lifecycle Manager to automate the coordinated startup and shutdown of a graph of interconnected components  
- a configuration-wiring utility to assemble structured config values from a variety of layered sources, map them
into POJO instances for convenient consumption, and inject them directly into the classes that need them
- a `ServiceSupervisor` utility to simplify the implementation of `main`-methods, by arranging to shutdown cleanly in
response to external signals or unexpected exceptions
- a test-scaffold with utilities to simplify testing of Upstart-based components and applications

Upstart is implemented as utility classes that enhance the awesome [Guice dependency injection framework](https://github.com/google/guice/wiki) and
[Guava utility library](https://github.com/google/guava/wiki) (thank you, Google!) with simple-to-use APIs for setting up the features
listed above.

#### To construct an `UpstartApplication`...
 ... you implement your business logic as a set of [Services](https://github.com/google/guava/wiki/ServiceExplained),
and other classes to integrate them together. 

- A _`Service`_ is a JVM class that implements `start` and/or `stop`. More on this in a moment.

... and you write all of your configuration definitions in [HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset)
files, and define matching java POJOs to hold the data at runtime.

... you wire your Services, supporting code, and configuration together using guice `Modules`. Upstart provides
the utility `UpstartModule` base-class, which extends guice's `AbstractModule` with convenient methods
to register services (`serviceManager().manage(MyService.class)`) and inject configuration
(`bindConfig(MyConfigurationClass.class)`).

... finally, you'll usually start your application in a main-method in a class that extends `UpstartApplication`,
by calling `new MyApplication().runSupervised()`
   
## Development Process

Here's a high-level survey of the development process for constructing a `UpstartApplication`. More detailed
descriptions of the Upstart concepts and facilities are covered in the sections below, and full documentation is
available in the javadocs.

Think about your application, and begin to identify the **`Services`** and **`Modules`** that will play a role
in its execution:

1. What tasks will be performed at startup to bring your system up, or at shutdown to stop cleanly -- without losing
work or triggering alarms or error-logs?
 These startup and shutdown concerns are good candidates for defining **Service APIs** to sponsor each underlying resource
   - A **`Service`** is a component you'll implement with logic defining what must happen to correctly `start` and `stop`.
    For example:
      - To **start** a `SocketListenerService`, you might open a listener-socket, and spawn threads to serve connections
      - To **start** a `DatabaseClientService` (or any DAO), you might configure a connection-pool, and run a test-query
        to check that everything's hunky-dory
      - To **start** a `HugeConfigurationService`, you might retrieve config data from various sources and build
        convenient structures to hold it 
      - To **stop** a `KafkaLoggingService`, you'd probably issue a flush/close, and wait for it to finish
1. Where will threads need to be started or scheduled to perform work?
   - event-sources, thread-pools, and timers are also naturally modeled as `Services`, so that they can be stopped at
     the right time to turn everything off
1. Implement each `Service` by subclassing one of the `UpstartService` base-classes:
   - `ExecutionThreadService` -- to run a single thread for the entire process lifetime
   - `ThreadPoolService` -- to use a JDK `ExecutorService` to execute tasks on "background" threads (returning
      composable JDK `CompletableFutures` or guava `ListenableFutures` representing their results)
   - `ScheduledService` -- to run a specific routine on a repeating schedule
   - `IdleService` -- to use dedicated threads to `start` and `stop`, but otherwise sit idle waiting to be invoked
   - `NotifyingService` -- to manage your own lifecycle asynchronously; call back into the framework when you've
     `started`/`stopped`/`failed`
   - see the guava [service documentation](https://github.com/google/guava/wiki/ServiceExplained) for details
1. What are high-level areas of separable concerns in the application that might be useful by themselves,
   or would naturally be tested together?
   - these areas are good candidates to be configured by distinct guice **`Modules`**
1. Freely build intermediary classes, facades, transaction-scripts, etc, to suit your needs
   - Don't construct or configure your objects yourself! Instead, embrace _Dependency Injection_! Design your components
     to `@Inject` references to their dependencies (with configuration-values annotated with `@ConfigPath`), and let
     guice build them for you
   - Objects with dependencies that also require dynamic input-parameters may use guice's [AssistedInject](https://github.com/google/guice/wiki/AssistedInject)
     facility to generate factory-interfaces
1. Tell Upstart about each service and `@ConfigPath` in a `UpstartModule` that targets its unit of responsibility, 
using the `serviceManager().manage(MyService.class)` and `bindConfig(MyConfigurationClass.class)` methods.
1. Build a test-suite for your application (or specific `Modules`/`Services`) using the `@UpstartServiceTest`
extension for [junit-jupiter](https://junit.org/junit5/docs/current/user-guide/)
1. Define a main-method to start your service, in a class that (optionally) extends `UpstartModule`
   - the main-method can use `UpstartApplication.buildServiceSupervisor(MainClass.class)` to get started
   - the main-class `configure` method can assemble the `Modules` and/or `Services` needed by the application
1. Construct the configuration for your application:
   - place application-specific configuration for subsystems in a HOCON resource called `upstart-application.conf`
   - for each target deployment-environment (defined by a distinct set of configuration-parameters), create an
     environment-specific configuration file in a HOCON resource called `upstart-environments/<environment-name>.conf`
1. Start your application by invoking your main-method, and ensure that the _mandatory_ **`UPSTART_ENVIRONMENT`**
environment-variable (or system-property) is defined externally to determine which environment's configuration to apply
   - `@UpstartTest`, `@UpstartServiceTest`, and `@UpstartClusterTest` automatically start your tests with `UPSTART_ENVIRONMENT=TEST`

## Upstart's Artifacts

#### [upstart](./upstart)

- application assembly using [guice](https://github.com/google/guice/wiki/Motivation) dependency-injection
- lifecycle management for graceful startup and shutdown of a graph of interdependent 
[`Service`](https://github.com/google/guava/wiki/ServiceExplained) implementations
- configuration capabilities using HOCON, with startup-time validation for configuration
  classes declared with `@ConfigPath`

```
<dependency>
  <groupId>io.upstartproject</groupId>
  <artifactId>upstart</artifactId>
</dependency>
```

#### [upstart-test](./upstart-test)
- test-utilities for exercising applications and validating configuration
```
<dependency>
  <groupId>io.upstartproject</groupId>
  <artifactId>upstart-test</artifactId>
  <scope>test</scope>
</dependency>
```

#### [upstart-example-app](./upstart-example-app)
- a small example project illustrating several essential upstart features

#### [upstart-web-javalin](./upstart-web-javalin) (add-on)
- upstart-driven HTTP web-server components based on [javalin](https://www.javalin.io/)
```
<dependency>
  <groupId>io.upstartproject</groupId>
  <artifactId>upstart-web-javalin</artifactId>
</dependency>
```
 
#### [upstart-cluster](./upstart-cluster) (add-on)
- clustered stateful service coordination based on [ZooKeeper](https://zookeeper.apache.org/), with optional support
for responsibility-partitioning, fencing, and coordinated cluster startup/shutdown
```
<dependency>
  <groupId>io.upstartproject</groupId>
  <artifactId>upstart-cluster</artifactId>
</dependency>
```

----------------------------------------------------------------------------------------------

# Deeper Dives

The sections below dig into the concepts and designs that form the upstart architecture and development-process

## Dependency-injection and Lifecycles with Guice and Services

Combining the dependency-information offered by guice, and the service-lifecycle features offered by
[guava services](https://github.com/google/guava/wiki/ServiceExplained), Upstart handles the (often complex and
tedious) task of starting and stopping services in the correct order automatically.

For example, if a `PurchaseService` requires the features of a `CreditCardService`, and both of these
services are registered with Upstart via `UpstartModule.serviceManager().manage()`, then Upstart's
`ManagedServiceGraph` (often used with a `ServiceSupervisor`) will wait for the `CreditCardService` to start up before
starting the `PurchaseService` (and vice versa for shutdown). This is achieved by inspecting the guice dependency-graph
at startup: presumably, if the `PurchaseService` calls into the `CreditCardService`, then it must (perhaps indirectly!)
`@Inject` a reference to it:

```java
class PurchaseService extends IdleService {
  @Inject
  PurchaseService(
          InventoryService inventoryService, // service dependency
          CreditCardService ccService,       // service dependency
          PurchaseServiceConfig config       // deserialized HOCON configuration
  ) {
    //...
  }
  
  protected void startUp() throws Exception {
    // ... initialize, possibly relying upon the InventoryService or CreditCardService
  }
  
  // can't call this until all services are correctly started
  public void purchase(User user, Item item, int amount) {
    Preconditions.checkState(isRunning(), "PurchaseService must be running, but was %s", state());
    
    if (inventoryService.reserveItem(item, amount) && creditCardService.chargeUser(user, item.cost(amount))) {
      shipItemsToUser(user, item, amount);
    }
  }
  
  protected void shutDown() throws Exception {
    // ... tear down
  }
  
  @ConfigPath("mycompany.purchase") // where to find this data in the UpstartConfig
  interface PurchaseServiceConfig {
    Duration transactionTimeout();
    String secretPasswordSsshhh();
    // ... whatever ...
  }
}
```

Given this example, it would be a mistake to allow the `PurchaseService` to start processing requests
before the `CreditCardService` was ready. Conversely, if we're shutting down, it would be best
to confirm that the `PurchaseService` was totally _stopped_ before stopping the `CreditCardService`.
Upstart's ServiceManager takes care of this:

```java
public class PurchaseProcessingApp extends UpstartApplication {
  private static final Logger LOG = LoggerFactory.getLogger(PurchaseProcessingApplication.class);

  protected void configure() {
    install(new PurchaseProcessingModule());
  }

  public ServiceSupervisor.BuildFinal configureSupervisor(ServiceSupervisor.ShutdownConfigStage builder) {
    return builder.shutdownGracePeriod(Duration.ofSeconds(30))
            .logger(LOG)
            .exitOnUncaughtException(true)
            .startedLogMessage("Purchase-processing system started!");
  }
  
  public static void main(String[] args) {
    new PurchaseProcessingApp().runSupervised();
  }
}
  
public class PurchaseProcessingModule extends UpstartModule {
  @Override
  public void configure() {
    // arrange for HOCON-based configuration to be injected via @ConfigPath annotation
    bindConfig(PurchaseService.PurchaseServiceConfig.class);
    
    // arrange for these services to be started/stopped in the correct order
    serviceManager().manage(PurchaseService.class)
                    .manage(InventoryService.class)
                    .manage(CreditCardService.class);
  }
}
```
#### Handling Unrecoverable Exceptions

If any managed `Service` enters the `FAILED` state, the Upstart `ServiceManager` immediately attempts to cleanly
shut down the application (according to the service dependency-graph, as described above). If launched with a
`ServiceSupervisor`, this will in turn cause the JVM process to exit.

Services become `FAILED` if they throw an exception out of any service-lifecyle method (such as `startUp`, `run`, etc;
the specifics vary depending on the `Service` base-class employed), or via an invocation of an API-method for this
purpose (eg, `NotifyingService.notifyFailed`).

In addition, if the `ServiceSupervisor` is configured to `exitOnUncaughtException`, then any "uncaught" exception
(thrown all the way out of any thread's `run` method, to the JDK's `Thread.uncaughtExceptionHandler`
facility) will also trigger a shutdown/exit procedure. Enabling this feature is encouraged to prevent uncaught
exceptions from going unnoticed, but you may wish to install an alternative `UncaughtExceptionHandler` instead.
 
## hojack, and @ConfigPath: configuration with HOCON, Jackson, and @Inject 

Applications built with upstart can use [typesafe-config](https://github.com/lightbend/config), with its feature-rich
[HOCON](https://github.com/lightbend/config#using-hocon-the-json-superset) syntax, to specify configuration parameters. 
They can then rely upon guice-injection to gain access to those values, by mapping them to injected java structures
annotated with `@ConfigPath` to specify where in the full configuration the values are to be found.
 
For example, given the following HOCON configuration snippet:

```
my.config {
    intValue: 7
    timeout: 5s
}
```
... upstart components could inject this configuration by defining an object to hold the configuration-structure,
like this:
```java
@ConfigPath("my.config") 
interface MyConfig {
  int intValue();
  Duration timeout();
}

class MyClass {
    @Inject
    public MyClass(MyConfig config) {
    }
}
```

In order to arrange this `@ConfigPath` wiring, dependencies must be registered at startup-time via
convenience-methods on the `UpstartModule` base-class:

```java
class MyModule extends UpstartModule {
  @Override
  public void configure() {
    bindConfig(MyConfig.class); // arranges an injectable singleton MyConfig instance from its @ConfigPath
  }
}
``` 

Advantages to this style of configuration include:
- convenient declarative definitions of all configuration options
- configuration settings can be comprehensively validated at system-startup time, preventing malformed configs from causing
more impactful runtime errors later
- automatic config-dump at system startup, documenting all active settings in the log

## Deployment Stages

Upstart employs a concept called the `UpstartDeploymentStage` to help manage optional features or policies to be
enabled in your application. Every `UpstartApplication` is constructed with a specific `UpstartDeploymentStage`,
which is normally defined in an `upstart-env-registry` configuration file (see the next section for more on
environment-configs). Supported values for `UpstartDeploymentStage` are:

- test
- dev
- stage
- prod

Components that wish to adjust behavior depending the active deployment-stage may 
`@Inject UpstartDeploymentStage` to learn about their deployment-environment. The 
`UpstartDeploymentStage` enum-constants also offer these convenience methods: 

- `isProductionLike()`  -- _Production/Staging_
- `isDevelopmentMode()` -- _Development_

## Environment Registry

The recommended mechanism for configuring an application involves the use of a `UpstartEnvironmentRegistry`, which
refers to a resource called `upstart-env-registry.conf` to determine the set of supported environment-configurations and their
respective `UpstartDeploymentStages`.

**Every named configuration which is referenced via `UPSTART_ENVIRONMENT` must be associated with a `DeploymentStage`
in an `upstart-env-registry.conf` resource; unregistered environment-names will result in a failed startup!**

Each application that uses the standard `UPSTART_ENVIRONMENT` mechanism for loading configuration should embed an
appropriate `upstart-env-registry.conf` declaring its deployment environments (usually in the same artifact as the application
main-method). However, if multiple applications target the same set of deployment-environments, the `upstart-env-registry` may
be packaged in a separate artifact that they share. `upstart-env-registry` files are also composable using normal HOCON semantics,
which may be useful for some scenarios.

The `upstart-app` artifact comes with a predefined [`upstart-env-registry.conf`](./upstart-app/src/main/resources/upstart-env-registry.conf),
ready to support the `local-dev` environment (development-mode), as follows:

```
upstart.application.environments {
  # each application must embed declarations of its target deployment-configurations and their stages in a resource
  # named upstart-env-registry.conf.
  #
  # entries in this table are comprised of (environment-name, UpstartDeploymentStage) pairs.
  # the values must match those defined in upstart.UpstartDeploymentStage:
  #     test, dev, stage, prod
  local-dev: dev
}
```

This `UpstartEnvironmentRegistry` system facilitates the ability to _validate_ all production-like configurations with
a junit-jupiter framework based on the provided `EnvironmentConfigValidatorTest` base-class. (See [upstart-test](./upstart-test/README.md)
for details).

## Configuration Resolution: Files and Precedence
Upstart configuration is immutable once an application is started,
and is usually accessed by `@Inject`ing values directly into the components that need them, annotated with a
`@ConfigPath` specifying where the values are to be found in the HOCON structure.

Although any pre-assembled `Config` object may optionally be used to start a Upstart application, the default
`UpstartEnvironment` loader assembles the configuration from a rich composition of layers intended for different phases
of application definition. We'll describe these layers here in **increasing** order of precedence (ie, values that
appear later in this list will override conflicting settings defined earlier in the list): 

1. `upstart-defaults/<ConfigPath>`
1. `reference`
1. `upstart-defaults`
1. `application`
1. `upstart-application`
1. `UPSTART_ENVIRONMENT`
1. `UPSTART_DEV_CONFIG` (only if `UpstartDeploymentStage.isDevelopmentMode` -- see [Deployment Stages](#deployment-stages) above)
1. `UPSTART_OVERRIDES` (HOCON contents of this environment-variable)

### Dynamic configs (lowest precedence): `upstart-defaults/<ConfigPath>`

When a POJO annotated with `@ConfigPath("path.to.HOCON.values")` is bound to its configuration via
`UpstartModule.bindConfig`, resources named `upstart-defaults/path.to.HOCON.values.{properties,json,conf}`
are automatically loaded, with the values contained therein prefixed with the specified ConfigPath.
 
For example, given a binding for config POJO: 
```java
@ConfigPath("secret.message")
interface SecretMessage {
  String secret();
  Duration selfDestructTimeout();
}

class SecretModule extends UpstartModule {
  @Override
  protected void configure() {
    // load configuration for `secret.message`
    bindConfig(SecretMessage.class);
  }
}
```
Given the setup above, any resources on the classpath named `upstart-defaults/secret.message.{conf,json,properties}` will be loaded,
with their contents defined under the path-prefix `secret.message`:

```HOCON
RESOURCE: upstart-defaults/secret.message.conf

# these values will be implicitly nested within `secret.message { ... }`

secret: tellnoone
selfDestructTimeout: 2m
```

This is useful for configuration fragments that are not always used, depending on the runtime context:
the file is only loaded if a guice-module calls `bindConfig` for the `ConfigPath`, which may be omitted based
on conditional logic.
 
In particular, this avoids a problem that would otherwise arise if variable-interpolations in these
conditionally-loaded resources were unresolvable: upstart requires all configs that are **loaded** to be fully
resolvable, so loading conditionally-required values dynamically ensures that the variables referenced
therein only require definitions if they will be used. (See the section on [variable interpolation](#compose-configuration-with-include-and-variableinterpolation) for more about this.)

### Library Defaults: `upstart-defaults`

Components which are integrated as reusable libraries may define _reasonable default_ settings by embedding
HOCON resources with the base-name `upstart-defaults` (ie, `upstart-defaults.{properties,json,conf}`).
All `upstart-defaults` resources on the classpath are merged together to form the lowest-precedence settings of the
full configuration. 

MINOR NOTE: Upstart's variable-interpolation behavior for `upstart-defaults` values differs slightly from the standard
HOCON design for `reference.conf`: HOCON normally requires all `reference` configs to be fully _resolvable_ -- no
undefined variables may be present in `reference` files. However, Upstart is more lenient in this regard: variables in
upstart config-files are left unresolved until all of the remaining configuration layers have been assembled. This
allows libraries to refer to place-holder variables which must then be defined in some other layer.

### Application Settings: `upstart-application`

The upstart deployment-unit is the `UpstartApplication`. Applications may need to provide configuration to
control the behavior of reusable libraries. For example, an application making use of a upstart-integrated
metrics-publication library may need to configure the library with contextual information.
 
Application-specific configuration values can be specified in a HOCON `upstart-application.conf` resource.

### Environment-specific Values: `upstart-environments/${UPSTART_ENVIRONMENT}.conf`

The default `UpstartEnvironment.ambientEnvironment()` method requires a **`UPSTART_ENVIRONMENT`** value to be
defined as either an environment-variable or JVM system-property. The specified value must be registered 
in `upstart-env-registry.conf`, and based on this value, the corresponding `upstart-environments/<environment-name>.conf`
resources are overlayed atop the `upstart-application` and `upstart-defaults`. 

### Developer-specific Customizations: `dev-configs/${user.name}.conf`

Any environment registered with the `Development` `DeploymentStage` enables *_Development-Mode_*, which supports
developer-specific configuration overrides.
 
If development-mode is enabled:

- At configuration-time, if the environment-variable `UPSTART_DEV_CONFIG` names a file which exists
(relative to the CWD, unless specified with an absolute path; by default: `./dev-configs/<username>.conf`), the
configuration is overridden with the file's contents.

This feature is recommended for experimental configuration overrides during developer-testing, to avoid
modifying baked-in configuration which could pose problems for other developers if pushed accidentally.

### Ambient Overrides: `${UPSTART_OVERRIDES}` (highest precedence)
If the **`UPSTART_OVERRIDES`** environment-variable is defined, the textual contents are parsed
as HOCON and overlayed as the highest-precedence source of configuration. This is only intended for
emergency-scenarios, to allow settings to be adjusted without need for a new artifact build/deploy pipeline.

In most cases, this value should be left undefined, and configuration-changes should be disseminated through a
standard commit/build/deploy pipeline.

## Compose configuration with `include` and `${variable.interpolation}`

HOCON has rich capabilities for composing configuration-values from various sources, including:
- loading other resources on the classpath (`include(classpath("my-other-config.conf"))`)
- values defined elsewhere in the configuration (using `${variable.interpolation}`)

Config-composition is highly encouraged, to avoid copying redundant settings around the configuration structure (be _DRY!_).
In fact, when whole subsets of configuration are needed by multiple components, it can be a good practice to embed a
_reference_ to the shared structure in a use-case-specific location in the config tree. This allows specific values to
be fine-tuned for specific consumers, without necessarily impacting any other consumers of the same inputs.

For example, consider the following HOCON:

```
myCompany.context {
  datacenter: us-west-1
  processRole: PurchaseService
}

myApplication {
  context: ${myCompany.context}    // myApplication embeds generic values into its own namespace
  otherStuff: whatever
} 

metrics.publisher {
  companyContext: ${myCompany.context}    // same generic values, different path
  otherStuff: whatever
} 
```

The consumers of the `myApplication` and `metrics.publisher` configuration-structures above can both access the
`myCompany.context` values under their own dedicated configuration-subspaces (eg, `myApplication.context.processRole`).
This way, it's straightforward to adjust the values for one of these components without altering the other.

## Logging configuration

Upstart uses SLF4J for all of its internal logging. It also provides a `UpstartStaticInitializer` which configures
logs passed to the `java.util.logging` subsystem to be captured by SLF4J. However, the integration of a specific
logger-provider to process SLF4J logs is left up to your application.
 
One recommended option is to integrate `upstart-log4j`, which provides:
   - a default `log4j.properties` file which routes all logs to stdout with a reasonable human-friendly format
   - support for configuring log4j loggers via HOCON (which can be specified in any supported HOCON resource-location,
   including a library's `upstart-defaults.conf`, an application's `upstart-application.conf`, or an environment-config,
   as follows:
```HOCON
upstart.log.rootLogger: WARN

upstart.log.levels: {
  "com.example.mypackage": INFO // remember to enclose the logger-name in quotes!
}
```

## Testing with upstart

See [upstart-test](./upstart-test)


-------------------------------------
# (Topics listed below need more documentation!)

## javalin/pippo web-server configuration with WebInitializers

## UpstartStaticInitializers

## AOP interception and InterceptorBinders

## Metrics
