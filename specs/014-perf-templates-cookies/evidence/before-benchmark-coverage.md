# T004 — starting condition: SyntaxBenchmark cannot detect an escaping regression
# Recorded: 2026-09-01T16:32:19Z

String literals in `SyntaxBenchmark.scala`: **40**

- literals containing a character `escapeJson` branches on (`"` `\` or below 0x20): **0**
- literals containing a character `escapeXml` branches on (`&` `<` `>` `"` `'`): **0**

Literals present:

- `id`
- `name`
- `John`
- `email`
- `john@example.com`
- `active`
- `score`
- `user`
- `id`
- `profile`
- `name`
- `John`
- `address`
- `city`
- `Moscow`
- `zip`
- `101000`
- `items`
- `id`
- `name`
- `userName`
- `tags`
- `alpha`
- `beta`
- `#{gamma}`
- `nested`
- `key`
- `value`
- `count`
- `active`
- `userId`
- `uid`
- `sessionId`
- `sid`
- `token`
- `authToken`
- `requestId`
- `reqId`
- `timestamp`
- `ts`

**Conclusion.** Not one fixture exercises either escape path. A #126 rewrite that adds a
fast path for escape-free text would post a large win on this suite while a regression in
the escaping branches stayed invisible. Escaping-heavy fixtures (T020, T021) are required
before the #126 before/after numbers mean anything. See contracts/escaping.md §7.
