# Настройки

## Глобальные (SharedPreferences `vpn_settings`, ключи в `VpnPrefs`)

| Ключ | По умолчанию | Где используется | Когда применяется |
|---|---|---|---|
| `routing_policy` | `ROUTE_ONLY_SELECTED` | `establishTun` | при создании TUN* |
| `bypass_lan` | `true` | маршруты TUN | при создании TUN* |
| `dns_server` | `1.1.1.1` | DNS TUN, только валидный IPv4 (`VpnPrefs.isValidDns`) | при создании TUN* |
| `auto_reconnect` | `true` | `runSshLoop` | сразу |
| `auto_start_on_boot` | `false` | `BootReceiver` | при загрузке |
| `smart_battery_saver` | `true` | режим простоя (`IDLE_SUSPENDED`) | сразу |
| `idle_timeout_seconds` | `60` | порог простоя (30/60/120/300) | сразу |
| `screen_off_throttling` | `true` | статистика раз в 15 с при выключенном экране (скрыта на ТВ) | сразу |
| `aggressive_battery_saver` | `false` | keepalive ≥ 60 с, реже статистика | keepalive — при подключении |

\* При активном тоннеле изменение вызывает `MainViewModel.markRoutingChanged()` →
`ReconnectBanner` предлагает переподключиться (повторный `ACTION_CONNECT` пересоздаёт TUN без разрыва).

Выбор приложений хранится в Room (`app_routing_rules.isSelected`) и тоже применяется при создании TUN.

## Профиль сервера (`SshServerProfile`, Room)

| Поле | По умолчанию | Примечание |
|---|---|---|
| `host`, `port` | —, `22` | порт 1–65535 |
| `username` | `root` | |
| `authType` | `PASSWORD` | `PASSWORD`, `PRIVATE_KEY` или `PRIVATE_KEY_AND_PASSWORD` (имя хранится в Room — не переименовывать) |
| `privateKey`, `passphrase` | | ключ OpenSSH/PEM (RSA/ECDSA/Ed25519); `passphrase` — только для зашифрованного ключа |
| `password` | | для `PASSWORD` и `PRIVATE_KEY_AND_PASSWORD`; неиспользуемые выбранным типом секреты при сохранении очищаются |
| `socksPort` | `10808` | локальный SOCKS5, 1024–65535 |
| `compression` | `false` | zlib; бесполезен для видео/сжатых данных |
| `keepAliveIntervalSeconds` | `15` | 0–3600, 0 — без keepalive |
| `isActive` | | ровно один активный; при удалении активного активным становится новейший |

Перед сохранением сервер проверяется (`testSshAuthentication`). Если проверка не прошла,
доступна кнопка «Save anyway». Сохранение профиля, на котором сейчас работает тоннель,
перезапускает тоннель с новыми параметрами.

## Как добавить настройку

1. Ключ и дефолт в `VpnPrefs` (новый смысл — новый ключ, старый не переиспользовать).
2. Flow + сеттер в `VpnRepository`.
3. `StateFlow` + метод в `MainViewModel`; если влияет на TUN — вызвать `markRoutingChanged()`.
4. UI в `SettingsScreen` через `SettingSwitchRow` / `FilterChip` с `tvFocusVisual`.
5. Чтение в `SshVpnService` через `VpnPrefs.get(this)`.
