package org.galaxio.gatling.feeders.faker

/** A lazy, composable description of how to produce a value.
  *
  * `Generator` is intentionally small: it gives load-test code the useful functional operations (`map`, `flatMap`, `zip`)
  * without exposing Cats or implementation details in the public API. Values are generated only when `sample()` is called
  * directly or when a Gatling feeder asks for the next record.
  *
  * @tparam A
  *   generated value type
  */
final case class Generator[+A](private val run: () => A) {

  /** Generates one value. */
  def sample(): A = run()

  /** Transforms generated values while keeping generation lazy. */
  def map[B](f: A => B): Generator[B] =
    Generator.delay(f(sample()))

  /** Builds dependent generators where the next value depends on the previous one. */
  def flatMap[B](f: A => Generator[B]): Generator[B] =
    Generator.delay(f(sample()).sample())

  /** Combines two independent generated values into a tuple. */
  def zip[B](other: Generator[B]): Generator[(A, B)] =
    Generator.delay((sample(), other.sample()))
}

object Generator {

  /** Creates a generator from a by-name expression. */
  def delay[A](value: => A): Generator[A] =
    Generator(() => value)

  /** Creates a generator that always returns the same value. */
  def const[A](value: A): Generator[A] =
    Generator(() => value)
}
