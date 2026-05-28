## Цель спринта

Закрыть file-transfer surface на всех платформах поверх UI-каркаса из #191: реальные sender'ы на Android / Desktop / iOS и видимый receiver. Параллельно — последняя миграция персистентности на DataStore.

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#192](https://github.com/khmelevartem/tether/issues/192) | Android sender wiring: SAF picker, share-sheet и MediaStore receiver | feature | M |
| 2 | [#193](https://github.com/khmelevartem/tether/issues/193) | Desktop sender wiring: AWT file picker на EDT и drag-and-drop по окну | feature | M |
| 3 | [#194](https://github.com/khmelevartem/tether/issues/194) | iOS sender wiring: UIDocumentPickerViewController и bookmark-based FileSource | feature | M |
| 4 | [#195](https://github.com/khmelevartem/tether/issues/195) | Receiver UI: FileServer events, PeerCard inbound states, платформенные уведомления | feature | M |
| 5 | [#251](https://github.com/khmelevartem/tether/issues/251) | TrustedDeviceStore: migrate to DataStore backend in commonMain | enhancement | M |

## Следствия

- После #192 / #193 / #194 sender реально работает на всех трёх платформах — TransferScreen из #191 перестаёт быть пустым каркасом, появляются настоящие выборы файлов и share-sheet входы.
- После #195 receiver виден на DeviceListScreen: PeerCard свеллит при входящем, показывает прогресс, имена файлов, [Cancel]; платформенные уведомления (Android FGS, Desktop tray, iOS local) — на месте. Разблокируется wake-lock parity #304.
- После #251 `TrustedDeviceStore` уезжает в commonMain, `expect/actual` персистентности больше нет; ADR adr-persistence-key-value полностью реализован.

## Порядок мерджа

#251 → #195 → #192 → #193 → #194

#251 первым — изолированная storage-миграция, не пересекается с UI. #195 вторым — расширяет `FileServer` контракт (events + cancelInbound), от которого sender wiring не зависит, но любая правка `FileServer` в одном из sender PR'ов потребует rebase. #192 / #193 / #194 — параллельные source set'ы (androidMain / desktopMain / iosMain), мерджатся в любом порядке.
