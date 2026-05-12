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
final class Generator[+A] private (private val run: () => A) {

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

  /** Filters generated values, retrying until predicate is satisfied. Use with caution — infinite loop if predicate is never
    * true.
    */
  def filter(predicate: A => Boolean): Generator[A] =
    Generator.delay {
      var value = sample()
      while (!predicate(value)) value = sample()
      value
    }

  def withFilter(predicate: A => Boolean): Generator[A] = filter(predicate)

  override def toString: String = s"Generator(<lazy>)"
}

object Generator {

  /** Creates a generator from a by-name expression. */
  def delay[A](value: => A): Generator[A] =
    new Generator(() => value)

  /** Creates a generator that always returns the same value. */
  def const[A](value: A): Generator[A] =
    new Generator(() => value)

  /** Samples each generator once and collects results into a Vector. */
  def sequence[A](generators: Seq[Generator[A]]): Generator[Vector[A]] =
    Generator.delay(generators.map(_.sample()).toVector)

  /** Generates a list of `n` values from a single generator. */
  def listOf[A](n: Int, generator: Generator[A]): Generator[Vector[A]] = {
    require(n >= 0, s"n must be >= 0: $n")
    Generator.delay(Vector.fill(n)(generator.sample()))
  }

  /** Generates a Map from key-value generator pairs. */
  def mapOf[K, V](entries: (Generator[K], Generator[V])*): Generator[Map[K, V]] =
    Generator.delay(entries.map { case (kg, vg) => kg.sample() -> vg.sample() }.toMap)

  /** Generates a tuple of two independent values. */
  def tupleOf[A, B](ga: Generator[A], gb: Generator[B]): Generator[(A, B)] =
    ga.zip(gb)

  /** Generates a tuple of three independent values. */
  def tupleOf[A, B, C](ga: Generator[A], gb: Generator[B], gc: Generator[C]): Generator[(A, B, C)] =
    Generator.delay((ga.sample(), gb.sample(), gc.sample()))
}
