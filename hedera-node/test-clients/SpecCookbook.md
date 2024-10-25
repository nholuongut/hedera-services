# Spec Cookbook

Because the `HapiSpec` DSL now includes a huge variety of `SpecOperation`'s, it can be hard to see the best way to write
a given test.

This work-in-progress cookbook tries to give some patterns and anti-patterns to keep in mind. Most of the patterns and
anti-patterns follow from a few organizing principles. We try to,
1. Make test setup as declarative as possible.
2. Hide explicit use of `HapiSpec` infrastructure like the registry and key factory.
3. Abstract any pattern used in more than a few places into its own `SpecOperation`.
4. Limit `HapiSpecOperation` modifiers to a few general extension points instead of many narrow ones.
5. Use `@Nested` test classes when the conditions for a set of tests form an increasingly specific hierarchy.

The cookbook contains mostly concrete examples; but it is good to keep the principles in mind throughout.

## Table of contents
- [Patterns](#patterns)
  * [DO create template objects to enumerate families of related tests](#do-create-template-objects-to-enumerate-families-of-related-tests)
  * [DO fully validate a transaction's record before submitting the next one](#do-fully-validate-a-transactions-record-before-submitting-the-next-one)
  * [DO prefer the object-oriented DSL when working with contract-managed entities](#do-prefer-the-object-oriented-dsl-when-working-with-contract-managed-entities)
  * [DO opt for `@BeforeAll` property overrides whenever possible](#do-opt-for-beforeall-property-overrides-whenever-possible)
  * [DO strictly adhere to the `@HapiTest` checklist](#do-strictly-adhere-to-the-hapitest-checklist)
  * [DO identify natural groupings of test classes and collect them in packages](#do-identify-natural-groupings-of-test-classes-and-collect-them-in-packages)
- [Anti-patterns](#anti-patterns)
  * [DON'T extract string literals to constants, even if they are repeated](#dont-extract-string-literals-to-constants-even-if-they-are-repeated)
  * [DON'T add any `HapiSpecOperation` modifier not essential to the test](#dont-add-any-hapispecoperation-modifier-not-essential-to-the-test)
  * [DON'T start by copying a test that uses `withOpContext()`](#dont-start-by-copying-a-test-that-uses-withopcontext)

## Patterns

### DO create template objects to enumerate families of related tests

When working with tokens, we often need to repeat the same basic test for each type of token key; or for each
flavor of custom fee. It is tempting to copy and paste the same test several times and customize it for each variation.
But this has all the usual problems that come with repeating code.

Instead, for each feature that varies, define an `enum` with the choices for that feature; and create a template object
that can represent every combination of features. For example, the `Hip540TestScenario` record explicitly names each
feature of a HIP-540 test scenario, enumerating the possible choices for the,
1. Non-admin key type.
2. Admin key state.
3. Target key state.
4. Management action.
5. Authorizing signatures.

The result is a comprehensive set of dozens of tests that are all run by,
```java
        @HapiTest
        public Stream<DynamicTest> allScenariosAsExpected() {
              return ALL_HIP_540_SCENARIOS.stream()
                  .map(scenario -> namedHapiTest(scenario.testName(), scenario.asOperation()));
        }
```
Of course there are tradeoffs, but when done carefully, templates are more likely to cover the entire surface area
of a feature.

### DO fully validate a transaction's record before submitting the next one

A good rule of thumb is that each `@HapiTest` should focus on asserting the behavior of a single transaction.

But a test might do enough work setting up its entities that it makes sense to assert the behavior of several
transactions and records. If so, _completely validate_ the record of each transaction before submitting the next.
This keeps the burden on the reader's memory as light as possible.

### DO prefer the object-oriented DSL when working with contract-managed entities

The security model for Hedera contracts gives a contract `0.0.C` control over a native entity `0.0.E` only when the
entity's key is updated to a structure activated by a `contractID` or `delegatable_contract_id` key for `0.0.C`.

Suppose `0.0.E` is a token. To accomplish this we must,
1. Create a token with an admin key.
2. Create the managing contract.
3. Look up the contract's id or EVM address.
4. Update the token's admin key to a threshold key structure with the contract id.

Using "low-level" operations, this would often look like,
```java
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        return hapiTest(
                        // Create our key, treasury account, token, and a manager contract
                        newKeyNamed("tokenKey"),
                        cryptoCreate("treasury"),
                        tokenCreate("managedToken")
                                .supplyKey("tokenKey")
                                .adminKey("tokenKey")
                                .treasury("treasury"),
                        uploadInitCode("adminContract"),
                        contractCreate("adminContract"),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                // Define a 1/2 threshold key with the manager contract id
                                newKeyNamed("contractKey")
                                        .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, "adminContract"))),
                                // Update the token's admin key to the threshold key
                                tokenUpdate("managedToken").adminKey("contractKey"),
        ...
```

While with the object-oriented DSL this becomes something like,
```java
        ...
        @Contract(contract = "UpdateTokenInfoContract", creationGas = 4_000_000L)
        static SpecContract updateTokenContract;
        ...
        @HapiTest
        public Stream<DynamicTest> canUpdateMutableTokenTreasuryOnceAuthorized(
            @NonFungibleToken(keys = {SUPPLY_KEY, ADMIN_KEY}) SpecNonFungibleToken mutableToken
        ) {
                return hapiTest(
                        // Authorize the contract to manage the token
                        mutableToken.authorizeContracts(updateTokenContract),
        ...
```

### DO opt for `@BeforeAll` property overrides whenever possible

Any time we use a `@LeakyHapiTest` we lose all concurrency we would otherwise have in that test class. This lost time
adds up when running against a `SubProcessNetwork`. So prefer instead to group all tests that need a particular set of
property overrides, and put them in a test class annotated with `@HapiTestLifecycle`.

Then add a `@BeforeAll` with an injected `TestLifecycle` instance and set the overrides there.
```java
        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
                testLifecycle.overrideInClass(Map.of("contracts.evm.version", "v0.46"));
        }
```
The overridden properties will automatically be restored to their previous values on the target network after the test
class completes.

### DO strictly adhere to the `@HapiTest` checklist

It has been said that quantity has a quality all its own. This is a good description of the main challenge in quickly
and accurately evolving the Hedera network software at the level of its gRPC API, block stream, and contract with NMT.
No one piece of business logic at any phase of the network lifecycle is very complex. But there are many pieces of
business logic, and some have material interactions with each other.

Without explicit verification of behavior, there is no real way to be confident that any given change to the consensus
node software has only the desired effects---and no other.

So the core maintainers of the software do not approve _any_ pull request that does not come complete with tests that
fully specify the behavior of the change via tests that pass the checklist in the README
[here](README.md#the-hapitest-checklist).

### DO identify natural groupings of test classes and collect them in packages

Unfortunately, the test classes in this module have barely any package-level organization, even though there are
many good options to consider. For example, a package could contain,
1. All the tests for a HIP.
2. All the tests that use a certain system contract.
3. All the tests targeting one phase of the network lifecycle.
4. All the tests from a parent package that must run in embedded mode.

All new tests should have some reasonable package-level organization; and we encourage pull requests that do nothing
but improve the package-level organization of the existing tests.

## Anti-patterns

### DON'T extract string literals to constants, even if they are repeated

When a test does a reasonable job of keeping its setup declarative and its scope small, it will only reference any
single entity, test resource, or network property a few times. But in some cases it is useful to repeat a `String`
literal more than once or twice.

Do not extract these literals to constants. This bloats the test class or method with variable definitions for no
real benefit; and adds a level of indirection when the reader wants to, for example, review the default value of a
property or open the Solidity file referenced by a contract.

### DON'T add any `HapiSpecOperation` modifier not essential to the test

Because we use spotless code style, which wraps lines aggressively, almost every modifier on a `HapiSpecOperation`
adds a line break.

Suppose we need a mutable token to test a contract management function. The minimal `HapiTokenCreate` might be,
```java
        tokenCreate("mutableToken").adminKey("aKey"),
```
But if we aimlessly copy or add modifiers, this could easily expand to,
```java
        cryptoCreate("treasury"),
        tokenCreate("mutableToken")
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury("treasury")
                                .initialSupply(1234)
                                .adminKey("aKey")
                                .freezeKey("aKey")
```
And already we are spending our reader's attention span rather liberally.

### DON'T start by copying a test that uses `withOpContext()`

Saying the `SpecOperation` family makes up a DSL is a bit of an overstatement. They are more a collection of types with
suggestively named factories and fluent modifiers. But when arranged thoughtfully, they can assert expected behaviors
of the Hedera network with clear intent.

Given a `HapiSpec` initialized for a target network, you can also use these operations in arbitrary Java code as an SDK
for that network by calling `SpecOperation#execFor(HapiSpec)` wherever you want---inside a `for` loop, asynchronously
in a scheduled `Runnable`, and so on.

Though this approach offers flexibility, it makes the resulting tests significantly harder to review and search.
Therefore, avoid starting by copying a test that uses `withOpContext()`. Only use this device when truly necessary, and
even then, adhere to the limits prescribed by the [`@HapiTest checklist`](README.md#the-hapitest-checklist).
