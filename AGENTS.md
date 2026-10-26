# AGENTS.md

Памятка для AI-агентов и новых разработчиков. Прочитайте её целиком перед изменениями.
Подробности по подсистемам — в [docs/](docs/README.md).

## Что это за проект

SSH Tunnel (`applicationId` и `namespace` — `top.rootu.ssh.tunnel`) — приложение для
Android и Android TV (minSdk 24, targetSdk/compileSdk 36). Оно заворачивает трафик выбранных
приложений в SSH-проброс `ssh -D`: системный тоннель (Android `VpnService`) → собственный
userspace TCP/IP → локальный SOCKS5 → SSH-каналы `direct-tcpip` (JSch).

Назначение: работа и тестирование мобильных приложений внутри сети предприятия.
**В README и пользовательских текстах продукт описывается как «тоннель» / «проброс», а не «VPN».**
Имена классов Android API (`VpnService` и т. п.) это не касается.

Модуль один — `:app`. Kotlin, Jetpack Compose (Material3), MVVM на StateFlow и корутинах,
Room для серверов и правил приложений, SharedPreferences для настроек, JSch (mwiede) + BouncyCastle
для SSH. Проект изначально сгенерирован в Google AI Studio — комментарии в `app/build.gradle.kts`
про signingConfigs оставлены намеренно.

## Сборка и проверка

```bash
gradle :app:testDebugUnitTest   # юнит-тесты (JUnit + Robolectric)
gradle :app:assembleDebug
gradle :app:assembleRelease     # R8 + shrinkResources включены
```

- `gradlew` в репозитории нет, используйте установленный Gradle или Android Studio.
- CI нет. Если Android SDK недоступен (облачная песочница, `dl.google.com` закрыт), используйте
  `tools/sandbox-check` — см. [docs/TESTING.md](docs/TESTING.md):
  - `tools/sandbox-check/net-tests` — JVM-тесты сетевого ядра (быстро, без Android);
  - `tools/sandbox-check/compile` — компиляция **всех** исходников против android-all + Compose Desktop
    со стабами Android-only API.
- Release собирается с R8. Новая библиотека с рефлексией → правила в `app/proguard-rules.pro`.

## Карта кода

```
app/src/main/java/
├── com/jcraft/jsch/ChannelWindowControl.java   Доступ к package-private окну канала JSch (см. NETWORKING)
└── top/rootu/ssh/tunnel/
    ├── MainActivity.kt          Навигация (NavigationRail на ТВ/широких экранах, BottomBar на телефоне)
    ├── data/
    │   ├── model/               SshServerProfile, AppRoutingRule, VpnStats/VpnStatus, лог
    │   ├── local/               Room: AppDatabase, ServerDao, AppRuleDao
    │   └── repository/          VpnRepository (UI-доступ к данным), VpnPrefs (ключи и дефолты настроек)
    ├── vpn/
    │   ├── SshVpnService.kt     Жизненный цикл: TUN, SOCKS, SSH, переподключение, статистика, уведомления
    │   ├── VpnTunPacketHandler  Чтение TUN (poll), маршрутизация TCP/DNS/UDP
    │   ├── Socks5ProxyServer    Локальный SOCKS5 → TunnelTransport
    │   ├── SshTunnelClient      JSch-сессия, implements TunnelTransport
    │   ├── SshChannelBuffer     Неблокирующий приёмный буфер канала с управлением окном SSH
    │   ├── TunnelTransport      Интерфейс транспорта (SSH в проде, фейк в тестах)
    │   └── net/                 Чистый Kotlin без Android: Packets, TcpProxySession, Socks5Client,
    │                            DnsMessages/DnsCache, Cidr, ByteQueue, GeoIpLookup (внешний IP)
    ├── receiver/BootReceiver    Автозапуск после загрузки
    └── ui/
        ├── MainViewModel.kt     Единая VM для всех экранов
        ├── screens/             Home, Apps, Servers, Logs, Settings
        ├── components/          SettingSwitchRow, ReconnectBanner, TvTextField
        ├── util/                tvFocusVisual / tvCardFocusable, isTvDevice, OEM battery helper
        └── theme/
```

## Ключевые потоки

1. **Подключение:** UI (`HomeScreen`) → `VpnService.prepare` → `MainViewModel.startVpn` →
   `SshVpnService` `ACTION_CONNECT` → `runTunnel`: SOCKS5 → `establishTun` → `VpnTunPacketHandler` →
   `runSshLoop` (подключение и переподключения).
2. **Пакет приложения:** TUN → `VpnTunPacketHandler.handlePacket` → `TcpProxySession` →
   `Socks5Client` → `Socks5ProxyServer` → `SshTunnelClient.openStream` (direct-tcpip).
3. **Состояние для UI:** статические `StateFlow` в `SshVpnService.Companion`
   (`vpnStatus`, `vpnStats`, `activeServer`, `logs`), их читает `MainViewModel`.
4. **Настройки маршрутизации** (приложения, политика, DNS, обход LAN) применяются при создании TUN.
   Если их меняют при активном тоннеле, `MainViewModel.needsReconnect` показывает `ReconnectBanner`.

## Правила проекта (обязательно)

- **Никаких утечек мимо тоннеля.** TUN живёт всё время работы сервиса, в том числе во время
  переподключения SSH; при недоступном SSH приложения получают RST. Не закрывайте TUN «на время»
  и не добавляйте маршруты/исключения, которые пустят DNS или TCP выбранных приложений напрямую.
  IPv6 на TUN намеренно не настраивается (Android тогда блокирует IPv6 для выбранных приложений).
- **Не блокируйте сессионный поток JSch.** Данные каналов принимает только `SshChannelBuffer`
  (никогда не блокирует). Не используйте `channel.getInputStream()` у `direct-tcpip`.
- **Блокирующий I/O — на своих пулах потоков**, не на `Dispatchers.IO` (лимит 64 потока
  ведёт к зависанию при множестве соединений).
- **Состояние жизненного цикла `SshVpnService` меняется только в главном потоке**
  (`serviceScope` на `Dispatchers.Main.immediate`), блокирующее — в `withContext(IO)`.
- **Сетевое ядро (`vpn/net`) — без Android-зависимостей**, чтобы тестироваться на JVM.
  Новая логика пакетов/TCP/DNS покрывается тестами в `app/src/test/java/top/rootu/ssh/tunnel/vpn`.
- **Ключи настроек** — только через `VpnPrefs`. Меняется смысл ключа → новый ключ + миграция,
  старый не переиспользовать. Новая настройка проходит путь: `VpnPrefs` → `VpnRepository` →
  `MainViewModel` → `SettingsScreen`, а если влияет на TUN — `markRoutingChanged()`.
- **Управление с пульта обязательно.** Любой новый интерактивный элемент должен получать фокус
  с D-pad, показывать фокус (`tvFocusVisual`) и срабатывать на OK. Правила — в
  [docs/TV_UI.md](docs/TV_UI.md).
- **Тексты и локализация:** все видимые пользователю строки (UI, тосты, уведомления, сообщения
  журнала и ошибки SSH) — только в ресурсах: `res/values/strings.xml` (английский, по умолчанию) и
  `res/values-ru/strings.xml` (русский). Новая строка добавляется **в оба файла** с одинаковыми
  плейсхолдерами (`%1$s`, `%1$d`). В Compose — `stringResource(...)`, вне Compose — `context.getString(...)`.
  Не разбирайте локализованный текст в логике (пример: результат проверки сервера — флаг
  `isTestFailed`, а не `startsWith("Error")`). В текстах на обоих языках — «тоннель»/«tunnel»,
  а не «VPN» (кроме названий системных пунктов меню Android и `ACTIVATE_VPN`).
  `vpn/net` и `Socks5ProxyServer` без Android-зависимостей: локализованные сообщения пишет `SshVpnService`.
- Комментарии в коде — на английском, коротко, в стиле окружающего кода.

## Частые ловушки

- **Фокус в Compose:** `onFocusChanged` видит только focus target *после* себя в цепочке
  модификаторов. `tvFocusVisual` ставится перед `clickable`, лишний `focusable()` перед `clickable`
  создаёт второй target, и OK перестаёт работать.
- **Вложенные focusable** внутри focusable-карточки недоступны стрелками. Делайте их соседями
  (см. `ServerItemCard`).
- **TUN-дескриптор неблокирующий**: `read()` возвращает 0 — ждать через `Os.poll`, не крутиться в цикле.
- **Порядок в JSch:** `setServerAliveInterval` после `timeout` (интервал и есть таймаут сокета).
  Потоки канала подключаются **до** `connect()`.
- **Текстовые поля только через `TvTextField`.** Обычный `OutlinedTextField` на ТВ открывает
  клавиатуру при простом перемещении фокуса и снова открывает её после «Назад». Формы с полями —
  полноэкранные, не в `AlertDialog` (окно диалога «прыгает» при изменении содержимого).
- **На ТВ нет многих системных Activity** (выбор файла, окно подтверждения тоннеля):
  оборачивайте `launch(...)` в `try/catch (ActivityNotFoundException)`.
- **Room-синхронизация списка приложений** — одной транзакцией (`withTransaction`), иначе Flow
  эмитит сотни раз.

## Git

- Сообщения коммитов — Conventional Commits на английском: `type(scope): summary`
  (`feat`/`fix`/`refactor`/`docs`/`chore`; scope: `vpn`, `ui`, `build`, `docs`, ...).
- Одна логическая правка — один коммит. Force-push в общие ветки запрещён.
