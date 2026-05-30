
## Цель спринта

Закрыть последний carry-over спринта 1 (#9 — фундамент паринга), дать Android первый user-visible экран и поднять iOS из «scaffold» в полноценный таргет, симметричный Android и Desktop. К концу спринта:

- security MVP получает фундамент (`TrustedDeviceStore` + `POST /pair`);
- Android-пользователь впервые открывает приложение и видит список пиров;
- iOS-устройство физически может принимать файлы — принцип «cross-platform is the product» из vision.md впервые выполняется фактически, а не на уровне scaffold'а.

## Состав

| #   | Issue                                                   | Название                                              | Тип     | Размер |
| --- | ------------------------------------------------------- | ----------------------------------------------------- | ------- | ------ |
| 1   | [#9](https://github.com/khmelevartem/tether/issues/9)   | Паринг — протокол обмена ключами и TrustedDeviceStore | feature | M      |
| 2   | [#7](https://github.com/khmelevartem/tether/issues/7)   | Android UI — экран списка устройств и ViewModel       | feature | M      |
| 3   | [#81](https://github.com/khmelevartem/tether/issues/81) | FileServer на iOS — приём файлов                      | infra   | M      |

## Параллелизм по слоям

| Слой | Задачи |
|------|--------|
| commonMain + security + jvmMain server | #9 |
| Android UI + commonMain UI | #7 |
| appleMain network | #81 |

Три полностью независимых потока. Никаких блокировок внутри спринта. Конфликтов по сборке нет — разные source sets и модули.

## Цепочки блокировок наружу

- #9 → #10 (паринг PIN/CLI), #11 (паринг PIN UI Android) — следующий спринт.
- #7 → #8 (Android send UI), #58 (Start/Stop UI) — следующий спринт.
- #81 → будущие iOS-issues (UI device list, pairing actuals на Keychain) — заведутся отдельно.

## Связанные продуктовые спеки

| Issue | Спека |
|-------|-------|
| #9 | [pairing.md](../product/features/pairing/spec.md) (scoped) |
| #7 | [device-list.md](../product/features/device-list/spec.md) (scoped) |
| #81 | без спеки (инфра, не пользовательская фича; контракт зафиксирован в `commonMain/network/FileServer.kt`) |

## Не вошло намеренно

- **#10, #11 (паринг PIN/CLI и Android UI)** — оба заблокированы #9, в один спринт с #9 не помещаются. Естественно идут в спринт 3.
- **#8 (Android send UI)** — зависит от #7 + полный pairing-стек. Спринт 3.
- **iOS UI screens (device list, transfer, pairing)** — depend на готовый `FileServer.apple`. Заводим issue после закрытия #81.
- **#74 KydraLog** — кросс-таргетный refactor, конфликтует со всеми тремя треками этого спринта. Отдельным окном.
- **#55 (Desktop CLI/UI split), #58 (Android Start/Stop), #59 (FGS dataSync), #41 (macOS native), #25 (curl Expect:100), #36 (skill terminology)** — без MVP-блокировки, отложены.

## Полезный инкремент

После спринта:

1. **Pairing-фундамент** (#9) — `TrustedDeviceStore` + `POST /pair` готовы. Разблокирована вся security-цепочка (#10, #11).
2. **Первый видимый экран Android** (#7) — пользователь открывает приложение и видит пиров. `TetherViewModel` появляется как точка интеграции UI и DI-графа — следующие задачи (#8, #58, #11) встают на готовый каркас.
3. **iOS становится полноценным peer'ом** (#81) — `FileServer.apple` перестаёт быть стабом. Это первый раз, когда «ship to all targets together» из vision.md выполняется фактически: iOS принимает файлы по тому же протоколу, что Desktop и Android.
4. **Долг спринта 1 закрыт** — последний carry-over (#9) уходит с доски.
