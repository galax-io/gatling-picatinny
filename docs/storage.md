# Storage

[← Back to README](../README.md)

`SessionStorage` keeps the session records a run collects, so that later scenarios or later runs can be fed from them and
saved cookies can be restored. Storage is available only from the Scala API: there is no Java or Kotlin facade.

## Import

```scala
import org.galaxio.gatling.storage._
```

## Create

```scala
val storage   = SessionStorage()                               // in memory
val persisted = SessionStorage(JsonFileBackend("users.json"))  // with a backend
val moved     = storage.withBackend(RedisBackend("localhost")) // a copy of the records, with a new backend
val current   = moved.backend                                  // Some(RedisBackend(...)); None for SessionStorage()
```

`SessionStorage()` keeps the records in memory only; `SessionStorage(backend)` also has a backend to persist them to.
`withBackend(b)` returns a new storage with a copy of the current records and the backend `b`; records added to one later
do not reach the other. `backend` is the storage's backend, an `Option[StorageBackend]`. The records live in the memory of
one JVM, and many virtual users can write to one storage at once.

## Save session data

```scala
val login = scenario("login")
  .exec(http("login").post("/login").check(jsonPath("$.token").saveAs("token")))
  .exec(storage.save("userId", "token"))

val audit = scenario("audit")
  .exec(storage.saveAll)

val saved: Int = storage.size // the number of records
storage.clear()               // drops every record
```

`save(keys*)` adds one record with the listed keys and their values. A key absent from the session is skipped; a record
with none of the keys found is not added. `saveAll` adds one record with every attribute of the session. `size` is the
number of records, and `clear()` drops them all.

## Feed from saved records

```scala
val previous = SessionStorage(JsonFileBackend("users.json")).load() // the records a previous run saved

val api = scenario("api")
  .feed(previous.toFeeder.circular)
  .exec(http("data").get("/data").header("Authorization", "Bearer #{token}"))
```

`toFeeder` returns the records as an `IndexedSeq[Record[Any]]`, which Gatling feeds with `.queue`, `.circular` and its
other strategies. `toFeeder` is a snapshot taken when it is called: a feeder built while the simulation is assembled sees
only the records that are already there (for example, those `load()` read from a previous run), not those the users of
this run will save.

A storage with no records cannot feed a scenario: Gatling rejects an empty feeder
(`requirement failed: Feeder must not be empty`) and the simulation fails to start. That is the case for a fresh storage,
and for one whose `load()` found no file, no key or no rows, so check `size` before you build a feeder from it.

## Persist between runs

Run 1 saves the records, then writes them to the backend once, after the run:

```scala
val storage = SessionStorage(JsonFileBackend("users.json"))

after {
  storage.backend.foreach(_.save(storage.toFeeder))
}
```

Or with `persist()`, a scenario step that hands every record of the storage to its backend, in a scenario of one user that
starts after the others:

```scala
val persistOnce = scenario("persist").exec(storage.persist())

setUp(
  login.inject(atOnceUsers(100)).andThen(persistOnce.inject(atOnceUsers(1))),
)
```

Every virtual user that reaches `persist()` writes again, so N users write N times: a file or a Redis key is overwritten
each time, and a JDBC table gets the records appended again each time.

Run 2 loads the records while the simulation is assembled and feeds from them; when run 1 saved nothing, the feeder is
empty and the simulation fails to start, as said above:

```scala
val storage = SessionStorage(JsonFileBackend("users.json")).load()
val reuse   = scenario("reuse").feed(storage.toFeeder.circular)
```

`load()` appends the backend's records to those the storage already has and returns the same storage. The records pass
through JSON on the way: strings come back as strings, and other values are not promised to come back as the same Scala
type.

## Backends

| Backend | Parameters (defaults) | `save` | Errors |
|---|---|---|---|
| `JsonFileBackend` | `filePath` | overwrites the JSON file; `load()` without the file adds no records; `clear()` deletes the file | an `IOException` from `save` when the file cannot be written (such as `NoSuchFileException` when its directory is missing); a json4s `ParseException` from `load()` when the file is not JSON, and a `MappingException` when it is not a list of records |
| `RedisBackend` | `host`, `port = 6379`, `storageKey = "gatling:auth:storage"` | overwrites the one key | `StorageWriteException` when Redis refuses the `SET` or answers the `DEL` with no count; the Redis client's own exception, not `StorageWriteException`, when Redis cannot be reached or answers with an error (such as `NOAUTH`, `READONLY` or `OOM`) |
| `JdbcStorageBackend` | `jdbcUrl`, `tableName = "gatling_session_storage"`, `username = ""`, `password = ""` | appends rows: a repeated `persist()` duplicates the records | `IllegalArgumentException` on creation when `tableName` is not an SQL identifier (letters, digits and underscores, not starting with a digit) |

A backend of your own implements `StorageBackend`: `save(records)`, `load()` and `clear()`.

## JDBC backend

```scala
val backend = JdbcStorageBackend("jdbc:postgresql://localhost:5432/perf", username = "perf", password = sys.env("PERF_DB_PASSWORD"))
val storage = SessionStorage(backend)

backend.clear() // drop the records of earlier runs before this run saves its own
```

The table is created on first access, once per backend instance, with three columns: `id`, an SQL-standard identity column
(`GENERATED BY DEFAULT AS IDENTITY`) and the primary key; `record_data`, the record as JSON; `created_at`, the time the row
was inserted. `load()` reads the rows in insertion order. `clear()` deletes every row: call it on the backend before saving
again to drop the records of earlier runs. The database driver is not part of the library, so add it to your build. The
backend is tested against PostgreSQL.

## Restore cookies

```scala
val cookieStorage = SessionStorage()

val roles = scenario("roles")
  .exec(http("login user").get("/login/user").check(jsonPath("$.cookie").saveAs("setCookie")))
  .exec(cookieStorage.restoreCookies("setCookie", "example.com"))
  .exec(http("user data").get("/user/data"))   // sends the user's cookie
  .exec(http("login admin").get("/login/admin").check(jsonPath("$.cookie").saveAs("setCookie")))
  .exec(cookieStorage.restoreCookies("setCookie", "example.com"))
  .exec(http("admin data").get("/admin/data")) // sends the admin's cookie of the same name
```

`restoreCookies(setCookieField, domain)` parses the `Set-Cookie` value held in the session attribute `setCookieField` and
puts each cookie in the Gatling cookie jar for `domain` with the path `/`, so later requests to `domain` send it. It also
keeps each cookie's name → value as a session attribute. The path, max-age, secure and httpOnly attributes of a cookie are
not carried over. A repeated call overwrites the cookie of the same name, which is how the example above switches roles.
Without the attribute in the session the step does nothing. The step removes its internal key at the end, so that key
never reaches `saveAll`; the loop attribute `cookie` stays, though. After a call that restores at least one cookie, the
session keeps `cookie`, the name and value of the last restored cookie as a map: `saveAll` stores it, and it replaces an
attribute of yours named `cookie`.
