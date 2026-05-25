## Цель спринта

Эпик #8 (file-transfer UI) получает фундамент на всех платформах: своя дизайн-система вместо Material 3, навигационный скелет, серверный wire contract для folder send. Параллельно агентный цикл `/implement` замыкает визуальную проверку — `@Preview` рендерятся headless и сверяются с UX-брифом без человека.

## Состав

| #   | Issue                                                     | Название                                                                                | Тип      | Размер | Итог |
| --- | --------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------- | ------ | ---- |
| 1   | [#187](https://github.com/khmelevartem/tether/issues/187) | TetherTheme + reusable UI primitives + миграция DeviceListScreen с Material 3           | feature  | L      | ✅ closed ([7b63201](https://github.com/khmelevartem/tether/commit/7b63201), [PR #241](https://github.com/khmelevartem/tether/pull/241)) |
| 2   | [#188](https://github.com/khmelevartem/tether/issues/188) | Decompose RootComponent + ChildStack navigation skeleton с restore-safe pattern         | refactor | M      | ✅ closed ([b4e3fd3](https://github.com/khmelevartem/tether/commit/b4e3fd3), [PR #234](https://github.com/khmelevartem/tether/pull/234)) |
| 3   | [#189](https://github.com/khmelevartem/tether/issues/189) | FileServer wire contract для folder send: sanitize пути и UploadStorage seam            | feature  | M      | ✅ closed ([d27b2a6](https://github.com/khmelevartem/tether/commit/d27b2a6), [PR #256](https://github.com/khmelevartem/tether/pull/256)) |
| 4   | [#127](https://github.com/khmelevartem/tether/issues/127) | Headless screenshot-рендер Compose previews (Roborazzi + ComposablePreviewScanner) + `review-visual` агент | infra | L | ✅ closed ([befb6b1](https://github.com/khmelevartem/tether/commit/befb6b1), [PR #228](https://github.com/khmelevartem/tether/pull/228)) |
| 5   | [#169](https://github.com/khmelevartem/tether/issues/169) | KtLint custom rule: запрет `runBlocking` в тестовом коде                                | infra    | S      | ✅ closed ([52130b5](https://github.com/khmelevartem/tether/commit/52130b5), [PR #229](https://github.com/khmelevartem/tether/pull/229)) |

**Итог:** 5/5 задач закрыты — все цели спринта достигнуты.

## Доп. результаты

- [#74](https://github.com/khmelevartem/tether/issues/74) ([2f42833](https://github.com/khmelevartem/tether/commit/2f42833), [PR #230](https://github.com/khmelevartem/tether/pull/230)) — KydraLog как единый KMP-фасад логирования; убирает разнобой логгеров перед file-transfer UI wiring.
- [#36](https://github.com/khmelevartem/tether/issues/36) ([8d537b3](https://github.com/khmelevartem/tether/commit/8d537b3), [PR #235](https://github.com/khmelevartem/tether/pull/235)) — glossary scaffolding + 7 mount points: фиксирует словарь домена перед взрывом UI-задач эпика #8.
- [#248](https://github.com/khmelevartem/tether/issues/248) ([9e0f896](https://github.com/khmelevartem/tether/commit/9e0f896), [PR #249](https://github.com/khmelevartem/tether/pull/249)) — `scripts/run-all.sh`: параллельный запуск всех таргетов одной командой, ускоряет smoke по 4 платформам.
- [#245](https://github.com/khmelevartem/tether/issues/245) ([903db70](https://github.com/khmelevartem/tether/commit/903db70), [PR #246](https://github.com/khmelevartem/tether/pull/246)) — дневная velocity в `/progress-boring`.
- [#242](https://github.com/khmelevartem/tether/issues/242) ([4a52cf5](https://github.com/khmelevartem/tether/commit/4a52cf5), [PR #243](https://github.com/khmelevartem/tether/pull/243)) — `prepare-commit-msg` restore на любой non-`#N:` subject.
- [#17](https://github.com/khmelevartem/tether/issues/17) ([380bc2c](https://github.com/khmelevartem/tether/commit/380bc2c), [PR #238](https://github.com/khmelevartem/tether/pull/238)) — удалён ручной fallback `/work-on-issue`: оставлен единственный вход через `/implement`.

## Следствия

- Эпик #8 готов к параллельному запуску платформенного wiring — #190 (state-machine), #191 (PeerCard transfer states), #192/#193/#194 (per-platform sender), #195 (receiver) перестают ждать preparatory.
- `RootComponent.Child` стабилен — #222 (settings surface) и #148 (device name UI) могут стартовать как первые потребители.
- `@Preview` в новых UI-задачах сверяется визуально без человека — снимает узкое место агентного цикла по всем последующим UI issue.

## Порядок мерджа

#169 → #127 → #188 → #187 → #189
