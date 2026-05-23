## Цель спринта

Эпик #8 (file-transfer UI) получает фундамент на всех платформах: своя дизайн-система вместо Material 3, навигационный скелет, серверный wire contract для folder send. Параллельно агентный цикл `/implement` замыкает визуальную проверку — `@Preview` рендерятся headless и сверяются с UX-брифом без человека.

## Состав

| #   | Issue                                                     | Название                                                                                | Тип      | Размер |
| --- | --------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------- | ------ |
| 1   | [#187](https://github.com/khmelevartem/tether/issues/187) | TetherTheme + reusable UI primitives + миграция DeviceListScreen с Material 3           | feature  | L      |
| 2   | [#188](https://github.com/khmelevartem/tether/issues/188) | Decompose RootComponent + ChildStack navigation skeleton с restore-safe pattern         | refactor | M      |
| 3   | [#189](https://github.com/khmelevartem/tether/issues/189) | FileServer wire contract для folder send: sanitize пути и UploadStorage seam            | feature  | M      |
| 4   | [#127](https://github.com/khmelevartem/tether/issues/127) | Headless screenshot-рендер Compose previews (Roborazzi + ComposablePreviewScanner) + `review-visual` агент | infra | L |
| 5   | [#169](https://github.com/khmelevartem/tether/issues/169) | KtLint custom rule: запрет `runBlocking` в тестовом коде                                | infra    | S      |

## Следствия

- Эпик #8 готов к параллельному запуску платформенного wiring — #190 (state-machine), #191 (PeerCard transfer states), #192/#193/#194 (per-platform sender), #195 (receiver) перестают ждать preparatory.
- `RootComponent.Child` стабилен — #222 (settings surface) и #148 (device name UI) могут стартовать как первые потребители.
- `@Preview` в новых UI-задачах сверяется визуально без человека — снимает узкое место агентного цикла по всем последующим UI issue.

## Порядок мерджа

#169 → #127 → #188 → #187 → #189
