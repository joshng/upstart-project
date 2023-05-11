# Testing with Upstart

Upstart provides an extensive framework for testing code that is configured with `UpstartModules`, based on
[JUnit 5](https://junit.org/junit5/).

Features of the testing framework include:

- constructing a `UpstartApplication` with `Modules` specified by the test
- overriding HOCON configuration-values before they are applied
- overriding guice-bindings configured by the Modules to replace injected objects with mocks or alternative implementations
- starting/stopping upstart-managed `Services` before/after each test-case
- `@Inject`-ing guice-managed components into test classes
- asserting managed `Service` dependency relationships

## Preamble: Prefer acceptance-testing

Upstart's testing facilities are designed with a bias towards _acceptance-testing_, which we define as:

> Assembling the entire system as a whole, and exercising and observing its **behavior** from "outside"

This style of testing tends to be accurate at detecting real issues, without being overly sensitive to
inconsequential implementation details: if we prefer testing the behavior of the system from "outside" (ie, only
the way a "client" or "user" would interact with it), then the "inside" can be changed, and the results verified,
without updating many tests. As long as the expected behavior of existing features remains the same, these tests
should continue to provide value without ongoing maintenance costs (unlike fine-grained unit-tests).

Some amount of more intimate "unit-testing" may also be wise, to exercise behaviors that are difficult to arrange
otherwise, but minimizing this style of testing in favor of a more integrated approach is recommended when feasible.

## Testing Whole Modules
To support this style of testing, the upstart testing frameworks involve [guice](https://github.com/google/guice/wiki/Motivation):
test-cases assemble the system to be tested by `installing` a guice `Module`. Ideally, a upstart test will install the
same guice `Module` that is installed in production, then proceed to interact with the components defined by that
`Module` in the same manner that a real client would: either via network APIs (for network-hosted services),
or via their public java APIs (for components which are embedded as libraries in other applications).

## Testing an unstarted Injector: @UpstartTest
The most basic style of upstart test uses the `@UpstartTest` annotation: this arranges a upstart-flavored guice
`Injector` to be constructed just before each `@Test` method (ie, via a junit `BeforeTestExecutionCallback`). The
assembly of this `Injector` can be influenced via a few mechanisms:
1. a test-class may directly implement `com.google.inject.Module` (or extend `UpstartModule`), and be installed into
   the injector to `configure` the bindings being tested (commonly used to `install` the real main-module of an application)
1. a `@BeforeEach` method may accept a `UpstartTestBuilder`, which can fine-tune the HOCON configuration
   and bindings exposed via the injector, including:
   - altering configuration-values with `UpstartTestBuilder.overrideConfig`
   - altering bindings defined in the injector with `UpstartTestBuilder.overrideBindings` (useful for forcing
     alternative implementations for specific dependencies, including mock-objects)

#### An additional lifecycle callback: `@AfterInjection`

## Testing Managed Services: @UpstartServiceTest

When testing Upstart applications, it's common to need application `Services` started and stopped before
and after each test-case, so that test-code can interact with a fresh system after its startup completes (ie,
when a real client would normally do so). `@UpstartServiceTest` handles this pattern: it will assemble a 
`UpstartApplication` in the same manner as `@UpstartTest` (above), then `start` the application (with
all of its managed Services) before the `@Test`-method is invoked. Finally, the application will be `stopped`
after the `@Test`-method completes. Any service-failures that occur during startup or shutdown will fail the test.

#### ... and more lifecycle callbacks: `@AfterServiceStarted`/`@BeforeServiceStopped`

## The 'test' Environment

## Overriding HOCON Configs

See the `@EnvironmentConfig.Fixture` annotation, `EnvironmentConfigFixture` interface, and `UpstartTestBuilder.overrideConfig`.

## Overriding Guice Bindings

See `UpstartTestBuilder.overrideBindings`

## Validating Deployment Configurations

Implement a subclass of `EnvironmentConfigValidatorTest`, perhaps utilizing `EnvironmentConfigFixture` to set up any necessary
environment-specific config values.

## Asserting Service Dependencies

See `ServiceDependencyChecker`

## Testing Distributed Clusters

`@UpstartClusterTest`
