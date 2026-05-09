# macOS-host JVM discovery должен идти через Bonjour, не JmDNS

## Симптом

Desktop CLI на macOS-сборке через JmDNS не видит mDNS-пиров с других устройств в той же
сети (например, реальный Android-телефон) — `[peers]` пустой даже за минуты ожидания.
Параллельно `dns-sd -B _tether._tcp local.` тех же пиров видит мгновенно. Mac↔Mac через
JmDNS работает.

## Причина

На macOS ядро направляет входящие multicast mDNS-пакеты с внешних интерфейсов
исключительно `mDNSResponder` через привилегированный путь (BPF / kernel control
socket). User-space сокеты, joined в multicast-группу `224.0.0.251` через стандартный
`IP_ADD_MEMBERSHIP`, **их не получают** — даже с `SO_REUSEPORT`. Loopback-multicast
(пакеты, отправленные с того же Mac) ядро доставляет всем подписчикам нормально, поэтому
Mac↔Mac discovery через JmDNS работает за счёт loopback-пути.

`mDNSResponder` хранит внешние записи в кеше, но не ре-публикует их в локальный
multicast самостоятельно. Запрос через `dns-sd -B` его «прокачает», и cached PTR
действительно появятся в локальном multicast — но SRV/TXT для внешних устройств в
multicast не выходят даже через `dns-sd -L`. То есть subprocess-обходом до `serviceResolved`
с реальным IP/портом дойти нельзя.

Воспроизводится в чистом Python (`socket.SOCK_DGRAM` на UDP 5353 с `IP_ADD_MEMBERSHIP`)
без JmDNS — это не баг JmDNS, а архитектурное поведение macOS. Полный сброс
`mDNSResponder` (`sudo killall mDNSResponder`) ничего не меняет — кеш строится заново
тем же путём.

## Решение

На macOS-host JVM-сборке `MdnsDiscovery` использует Apple DNS-SD API через JNA-биндинг
к libSystem (`DNSServiceBrowse` → `DNSServiceResolve` → `DNSServiceGetAddrInfo`), и
публикует свой сервис через `DNSServiceRegister`. Это делает процесс клиентом
mDNSResponder, а не его конкурирующим multicast-слушателем.

На Linux/Windows никакого системного mDNS-демона нет, и JmDNS работает напрямую через
raw multicast — там оставляем JmDNS. Дисптач — по `os.name` в `MdnsDiscovery.jvm.kt`.

## Где смотреть

- [`MdnsDiscovery.jvm.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/MdnsDiscovery.jvm.kt) — фабрика по `os.name`
- [`bonjour/DnsSd.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/DnsSd.kt) — JNA-биндинги к libSystem
- [`bonjour/MdnsDiscoveryBonjour.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/MdnsDiscoveryBonjour.kt) — реализация browse/resolve/addrinfo через DNS-SD
- [`MdnsDiscoveryJmdns.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/MdnsDiscoveryJmdns.kt) — JmDNS-вариант для Linux/Windows
- Issue [#47](https://github.com/khmelevartem/tether/issues/47) — диагностические комментарии с экспериментальными данными
