# Transactions

[← Back to README](../README.md)

`startTransaction`/`endTransaction` syntax for Gatling scenarios. A transaction is a union of actions that measures summary response time including pauses — unlike groups, which exclude pauses. You can also pass an explicit `stopTime` (epoch millis) via `endTransaction(name, stopTime)`.

Requires Gatling 3.13.x. Your simulation must extend `SimulationWithTransactions` instead of `Simulation`.

## Import

```scala
import org.galaxio.gatling.transactions.Predef._
```

## Usage

Scala:

```scala
exec(Actions.createEntity())
  .startTransaction("transaction1")
  .doWhile(_ ("i").as[Int] < 10)(
    feed(feeder)
      .exec(Actions.insertTest())
      .pause(2)
      .exec(Actions.selectTest)
  )
  .endTransaction("transaction1")
  .exec(Actions.batchTest)
  .exec(Actions.selectAfterBatch)
```

Java:

```java
exec(Actions.createEntity())
  .exec(startTransaction("transaction1"))
  .exec(Actions.insertTest())
  .pause(2)
  .exec(Actions.selectTest)
  .exec(endTransaction("transaction1"))
  .exec(Actions.batchTest)
  .exec(Actions.selectAfterBatch)
```

Kotlin:

```kotlin
exec(Actions.createEntity())
  .exec(startTransaction("transaction1"))
  .exec(Actions.insertTest())
  .pause(2)
  .exec(Actions.selectTest)
  .exec(endTransaction("transaction1"))
  .exec(Actions.batchTest)
  .exec(Actions.selectAfterBatch)
```

## Full simulation example

For a complete, runnable simulation that extends `SimulationWithTransactions` and wires `startTransaction`/`endTransaction` into real HTTP traffic, see the example overlays (the `Debug` simulation in each; the Scala overlay also has a dedicated `TransactionScenario`):

- Scala — [Debug.scala](../examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny/Debug.scala) · [TransactionScenario.scala](../examples/scala-sbt-example/src/test/scala/org/galaxio/performance/picatinny/scenarios/TransactionScenario.scala)
- Java — [Debug.java](../examples/java-maven-example/src/test/java/org/galaxio/performance/picatinny/Debug.java)
- Kotlin — [Debug.kt](../examples/kotlin-gradle-example/src/gatling/kotlin/org/galaxio/performance/picatinny/Debug.kt)

See [Examples & Testing](examples.md) for how to generate and run them.
