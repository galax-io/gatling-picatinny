# T025 — базовая линия JMH ДО оптимизаций (#126, #137)
# Снято 2026-09-01, `-prof gc -bm avgt -f 1 -wi 2 -i 3`, sbt 1.13.0

| бенчмарк | время (us/op) | аллокации (B/op) |
|---|---:|---:|
| `parseAttributeDense` | 1.862 | 5048 |
| `parseAwkward` | 1.391 | 3648 |
| `parseBareNameValue` | 1.262 | 6000 |
| `parseFullAttributes` | 13.359 | 33056 |
| `makeJsonEscapeFree` | 0.482 | 1288 |
| `makeJsonEscapeHeavy` | 1.541 | 4264 |
| `makeJsonLateEscape` | 0.731 | 2032 |
| `makeXmlEscapeHeavy` | 0.500 | 2280 |

Сырые данные: `before-perf.json`. Повторить: см. quickstart.md §5.
