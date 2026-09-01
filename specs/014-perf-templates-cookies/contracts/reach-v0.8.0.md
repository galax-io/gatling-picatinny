# Contract: OpenNFR reach at upstream `v0.8.0`

**Source**: upstream `README.md` § *What any tool can actually run*, at release `v0.8.0`
(`c1a1761`, 2026-09-01). That page is the tables' only home; this file transcribes it and derives
nothing of its own. Where the two disagree the upstream table is right and the disagreement is a
finding to report upstream.

This supersedes the `v0.6.0` transcription in `specs/013-opennfr-assertions/contracts/reach.md` for the
metric and selection axes. **Only the rows that changed are restated here.**

---

## 1. What changed at `v0.8.0`

| | `v0.6.0` | `v0.8.0` |
|---|---|---|
| the duration metric | one name, borrowed from a convention that another producer owns | **two** names, both under the load-test namespace |
| a selection naming only a group hierarchy | **cannot** — no metric name in the format was true of what a group measures | **can**, and only paired with the group metric |
| the borrowed name | admitted | **retired outright** — not aliased, and with no acceptance window |

## 2. The metric axis

| Document metric | Renders to | Verdict |
|---|---|---|
| operation duration (`loadtest.request.duration`) | the response-time statistic | **can** — under every selection the Selection axis admits **except** a hierarchy with no request name |
| group duration (`loadtest.group.duration`) | the group's cumulated response-time statistic | **can** — and **only** under a hierarchy with no request name |
| the retired name (`http.client.request.duration`) | — | **cannot** — retired at `v0.8.0` and not aliased |
| body sizes, anything else | — | **cannot** |

## 3. The selection axis — only the changed row

| Selector | Renders to | Verdict |
|---|---|---|
| a hierarchy with no request name, every element a plain string | a path resolving to a **group** | **can**, and **only** paired with the group metric. A predicate carrying the operation metric, any other metric, or no metric at all is still refused under this selection |
| a hierarchy containing a wildcard element, **with or without** a request name | — | **cannot** — a group at that position with any name, and no scope carries a wildcard path part |

Every other selection row is unchanged from the `v0.6.0` transcription.

## 4. The pairing is bidirectional

This is the part most easily got half-right. Both directions are refusals:

| | operation metric | group metric |
|---|---|---|
| global, quantified, request-named, hierarchy+request | **can** | refuse |
| hierarchy only | refuse | **can** |

A design that only *adds* the newly permitted row, without also refusing the operation metric under the
group-only selection, does not implement this table.

## 5. What Gatling actually computes for a group — and the caveat

The group metric renders to the **sum of the durations of the operations the group encloses, not the
elapsed time of the traversal**. A run that pauses inside a group is not charged for the pause.

The wall-clock quantity *is* computed by the target, but it is absent from the interface assertions read,
so **no assertion can reach it and no configuration makes it assertable**. One setting suggests
otherwise: a charting flag switches the *report* between the two group quantities and never touches the
assertion path. With that flag set, a run's charts and its assertions disagree by design. The shipped
documentation must say this, because a reader who does not know it will try to reconcile the two numbers
by guessing.

## 6. Two facts about the target that shape the implementation

**Rendering a group duration needs no new call.** The target exposes exactly one time-metric entry
point, and its assertion value has no field in which a group-versus-request distinction could be
recorded. The statistic is chosen entirely by path resolution at validation time: a path that resolves
to a group is served the group's cumulated statistic, a path that resolves to a request is served the
request's. So this change lives in the renderer's **validation rules**, not in what it emits.

Two consequences follow, and both are documentation obligations rather than defects:

1. A group assertion **prints as a response-time assertion** in the target's own report. There is no way
   to make the message say "group duration". The metric distinction exists only in the requirement
   document.
2. A group requirement and the old "call the group a request" workaround render to the **identical**
   value. The prohibition on that workaround therefore survives only as a documentation rule, and the
   migration table must keep stating it.

**A path that is both is ambiguous in the target.** Where a rendered path is simultaneously the full
hierarchy of a recorded group and the full path of a recorded request, which one matches depends on hash
order. This is a property of the target, recorded upstream the same way, and it applies identically to
the deprecated builder. It is not this library's to resolve.

## 7. Upstream wording that must not be transcribed literally

Upstream's aggregation section names the group statistic in a form that reads like a code call. **No
such builder exists** — it is the name of the statistic, not of an API. A literal transcription into
this contract would describe code that does not compile. Transcribe the statistic.

## 8. Refusal messages

The renderer's stated contract is that every refusal carries the words of the row that refused it. The
new refusals are no exception:

| Refused | Message must name |
|---|---|
| group metric under a non-group selection | that the group metric is admitted only under a hierarchy with no request name |
| operation metric under the group-only selection | that such a path resolves to a group, which has no request statistics of its own |
| no metric, or another metric, under the group-only selection | the same |
| the retired name, anywhere | **both** the retirement and the replacement to write instead — an author who hits this needs to be told what to do, not only that something is wrong |

## 9. Local disagreements with the published tables

The existing register of places where this library follows a published row it has reason to think wrong
survives unchanged in substance — the aggregation rows that refuse a count and a rate over a metric are
still refused upstream at `v0.8.0`. Only the metric name quoted in that register's prose moves.

One mechanical consequence: the register's guard compares the native statistic by equality with the
response-time statistic. Once a second native statistic exists, that comparison must widen to a
membership test, or the guard that checks every locally-decided refusal points at the register becomes
one-sided.
