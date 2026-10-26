# Тестирование

## Юнит-тесты (`app/src/test`)

| Тест | Что проверяет |
|---|---|
| `vpn/net/PacketsTest` | разбор и сборка IPv4/TCP/UDP/ICMP, контрольные суммы, опции SYN, фрагменты |
| `vpn/net/CidrTest` | маршруты обхода LAN: покрытие всего, кроме частных сетей |
| `vpn/net/DnsMessagesTest` | ключ кэша, TTL, TC-усечение, истечение кэша |
| `vpn/net/ByteQueueTest` | буфер неподтверждённых данных |
| `vpn/net/TcpProxySessionTest` | рукопожатие, полузакрытие, окно клиента, повтор, дубли, RST, нулевое окно |
| `vpn/net/GeoIpLookupTest` | внешний IP: перебор сервисов, локализация страны, разбор JSON и HTTP (chunked, ошибки) |
| `vpn/SocksTunnelEndToEndTest` | TCP приложения → TcpProxySession → SOCKS5 → транспорт; отказ при упавшем транспорте |
| `ExampleRobolectricTest` | дефолты настроек, форматирование статистики, английский по умолчанию и русский перевод (Robolectric, `@Config(qualifiers = "ru")`) |
| `data/model/AuthTypeTest` | порядок методов входа (ключ раньше пароля), стабильность имён в Room |
| `update/ReleaseInfoTest` | разбор релиза GitHub: draft/prerelease, выбор `.apk`, сравнение версий с суффиксами `git describe` |

`FakeApp` в `vpn/net` изображает TCP-стек приложения по ту сторону TUN — используйте его для новых
тестов сетевого ядра.

## Обычная сборка

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## Без Android SDK (облачная песочница)

Google Maven недоступен, AGP не работает. В `tools/sandbox-check` два независимых Gradle-проекта,
зависят только от Maven Central:

```bash
cd tools/sandbox-check/net-tests && gradle --no-daemon test
cd tools/sandbox-check/compile && gradle --no-daemon compileKotlin compileJava
```

- `net-tests` — запускает тесты из `app/src/test/java/top/rootu/ssh/tunnel/vpn` и `.../update` на чистой JVM
  (компилирует только `vpn/net/*`, `Socks5ProxyServer.kt`, `TunnelTransport.kt`, `update/ReleaseInfo.kt`).
- `compile` — компилирует **все** исходники `app/src/main` против Robolectric `android-all`
  (классы Android) и JetBrains Compose Desktop 1.7 (тот же API, что androidx Compose 1.7 /
  Material3 1.3 из BOM 2024.09.00). Android-only API (ComponentActivity, LocalContext, Room,
  NotificationCompat, lifecycle, `stringResource` и т. п.) закрыты стабами в `compile/stubskt`.
  Класс `R` генерируется задачей `generateR` из `app/src/main/res` (строки, массивы, drawable, mipmap,
  шрифты), поэтому ссылка на несуществующий ресурс тоже ловится. Ловит только ошибки компиляции; поведение стабов не проверяется. Новые Android-only
  вызовы → добавить стаб с той же сигнатурой.

Ни один из вариантов не проверяет KSP/Room, ресурсы, манифест и R8 — это только реальная сборка.

## Ручная проверка на устройстве

1. Подключение по паролю и по ключу (RSA и Ed25519) — **обязательно на release-сборке** (R8).
2. В выбранном приложении открывается внутренний ресурс; на шлюзе `ss -tnp | grep sshd` показывает
   соединения к цели.
3. DNS: внутреннее имя резолвится (через шлюз); на устройстве `adb shell dumpsys connectivity`
   показывает DNS тоннеля.
4. Утечки: выключить SSH-сервер или сеть — выбранные приложения не открывают сайты, остальные работают.
5. Видео в выбранном приложении + параллельно другое приложение — второе не «замерзает», когда плеер
   набрал буфер (управление потоком каналов).
6. Смена Wi-Fi ↔ Ethernet/мобильная сеть — переподключение за несколько секунд.
7. Обход LAN: при включённой опции доступен локальный NAS/роутер из выбранного приложения.
8. ТВ — по чек-листу из [TV_UI.md](TV_UI.md).
9. Обновление: поставить сборку со старым тегом, Settings → Updates → «Загрузить и установить»;
   на Android 8+ появляется запрос разрешения, после него — системный установщик.
