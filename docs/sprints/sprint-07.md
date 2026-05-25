## Цель спринта

Эпик #8 двигается параллельно по двум независимым осям: общая transfer-state-machine в `commonMain` (фундамент под все sender/receiver задачи) и device-list row contract (UI-surface paired × reachable, который потребляет PeerCard). Параллельно закрывается macOS-вход как минимальная нативная точка запуска и точечный bug на Android-эмуляторе.

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#190](https://github.com/khmelevartem/tether/issues/190) | Common transfer state-machine: BatchSender и TransferComponent с тестами | feature | M |
| 2 | [#201](https://github.com/khmelevartem/tether/issues/201) | Device list: реализовать четырёхкейсовый row contract (paired × reachable) | feature | M |
| 3 | [#41](https://github.com/khmelevartem/tether/issues/41) | macOS native entry point — запускаемое приложение для macosArm64 | feature | S |
| 4 | [#91](https://github.com/khmelevartem/tether/issues/91) | Передача файла на Android-эмулятор зависает по таймауту | bug | S |

## Следствия

- После #190 разблокируется вся вторая волна эпика #8: #191 (Transfer UI), #192/#193/#194 (per-platform sender wiring), #195 (Receiver UI) — все они тонкие потребители контракта state-machine.
- #201 закрывает populated-state часть device-list brief'а; остальные состояния (searching, first-launch) становятся следующим явным docs-апдейтом.
- macOS native-таргет получает первый запускаемый артефакт — открывается ручная проверка `appleMain` кода без iOS-устройства.
