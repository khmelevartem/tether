## Цель спринта

Закрыть file-transfer surface на всех платформах поверх UI-каркаса из #191: реальные sender'ы на Android / Desktop / iOS и видимый receiver. Параллельно — закрыть Android FGS-гейт перед MVP.

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |
| 1 | [#192](https://github.com/khmelevartem/tether/issues/192) | Android sender wiring: SAF picker, share-sheet и MediaStore receiver | feature | M |
| 2 | [#193](https://github.com/khmelevartem/tether/issues/193) | Desktop sender wiring: AWT file picker на EDT и drag-and-drop по окну | feature | M |
| 3 | [#194](https://github.com/khmelevartem/tether/issues/194) | iOS sender wiring: UIDocumentPickerViewController и bookmark-based FileSource | feature | M |
| 4 | [#195](https://github.com/khmelevartem/tether/issues/195) | Receiver UI: FileServer events, PeerCard inbound states, платформенные уведомления | feature | M |
| 5 | [#59](https://github.com/khmelevartem/tether/issues/59) | Android FGS: оценить лимит dataSync 6h/сутки на Android 15+ и выбрать тип сервиса | infra | M |

## Следствия

- После #192 / #193 / #194 sender реально работает на всех трёх платформах — TransferScreen из #191 перестаёт быть пустым каркасом, появляются настоящие выборы файлов и share-sheet входы.
- После #195 receiver виден на DeviceListScreen: PeerCard свеллит при входящем, показывает прогресс, имена файлов, [Cancel]; платформенные уведомления (Android FGS, Desktop tray, iOS local) — на месте. Разблокируется wake-lock parity #304.
- После #59 Android FGS-тип зафиксирован под Android 15+ — либо подтверждена устойчивость `dataSync` при типичном использовании, либо выбран `connectedDevice` / `specialUse`. Снимается риск тихой смерти сервиса через 6h.

## Порядок мерджа

#195 → #59 → #192 → #193 → #194

#195 первым — расширяет `FileServer` контракт (events + cancelInbound), от которого sender wiring не зависит, но любая правка `FileServer` в одном из sender PR'ов потребует rebase. #59 — изолированный Android Manifest + FGS-тип, не пересекается с transfer surface. #192 / #193 / #194 — параллельные source set'ы (androidMain / desktopMain / iosMain), мерджатся в любом порядке.
