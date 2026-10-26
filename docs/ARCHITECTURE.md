# Архитектура

## Слои

```
UI (Compose)            MainActivity, ui/screens/*, ui/components/*
   │  StateFlow
MainViewModel           единая VM: серверы, приложения, настройки, команды сервису
   │                         │
VpnRepository/VpnPrefs  SshVpnService (статические StateFlow в companion)
   │                         │
Room + SharedPreferences     TUN ─ VpnTunPacketHandler ─ Socks5ProxyServer ─ SshTunnelClient (JSch)
```

- **UI** ничего не знает о сети: читает `StateFlow` из `MainViewModel` и вызывает её методы.
- **`MainViewModel`** — одна на всё приложение (`viewModel()` в `MainApp`). Команды сервису
  отправляются Intent-ами (`ACTION_CONNECT` с `EXTRA_SERVER_ID`, `ACTION_DISCONNECT`).
- **`SshVpnService`** публикует состояние через `companion object`:
  `vpnStatus`, `vpnStats`, `activeServer`, `logs` и флаг `isUiVisible` (ставит `MainActivity`
  в `onStart/onStop`, чтобы замеры задержки шли только при открытом UI).

## Хранение данных

| Что | Где |
|---|---|
| Профили серверов (`SshServerProfile`) | Room, таблица `ssh_servers` (`ServerDao`) |
| Выбор приложений (`AppRoutingRule`) | Room, таблица `app_routing_rules` (`AppRuleDao`) |
| Настройки | SharedPreferences `vpn_settings`, ключи — в `VpnPrefs` |
| Журнал | В памяти, `SshVpnService.logs`, последние 300 записей |

Room-база: `fallbackToDestructiveMigration`, версия 1. При изменении схемы добавьте миграцию,
иначе пользователи потеряют серверы. Пароли и ключи хранятся открытым текстом
(`allowBackup=false`), шифрование — известная доработка.

Список приложений синхронизируется `VpnRepository.syncInstalledApps()` при старте и при
возврате на экран Apps: новые добавляются, удалённые убираются, выбор сохраняется. «Системным»
считается только предустановленный пакет без иконки в лаунчере — поэтому YouTube/Netflix на ТВ
видны в фильтре «User Apps».

## Жизненный цикл сервиса

```
onStartCommand
 ├─ ACTION_CONNECT           startForeground → startTunnel(serverId)
 ├─ ACTION_DISCONNECT        shutdown()
 └─ null / android.net.VpnService (Always-on, перезапуск системой) → startTunnel(-1 = активный сервер)

startTunnel: отменяет предыдущий tunnelJob и ждёт его, загружает профиль, runTunnel(profile)

runTunnel
 1. Socks5ProxyServer.start()        — порт занят → ERROR, стоп
 2. establishTun()                   — новый TUN заменяет старый без разрыва (смена сервера)
 3. VpnTunPacketHandler.start(), stats/ping циклы
 4. runSshLoop():
      connect → CONNECTED → waitWhileConnected (обрыв сессии или смена сети)
      ошибка аутентификации → ERROR, стоп (без повторов)
      autoReconnect=false → стоп
      иначе RECONNECTING, пауза 2,4,8,…,60 с (раньше — при смене сети) и снова
 finally: stopPipeline(closeTun = false)  — TUN остаётся (kill switch)

shutdown(): отмена, stopPipeline(closeTun = true), DISCONNECTED, stopForeground, stopSelf
```

Состояния `VpnStatus`: `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `IDLE_SUSPENDED` (подключено,
но нет трафика дольше `idle_timeout_seconds` — реже обновляется статистика, нет замеров задержки),
`RECONNECTING`, `ERROR`.

`failAndStop(reason)` останавливает сервис, ставит `ERROR` и показывает отдельное уведомление
с причиной. Причина также видна на главном экране (последняя ошибка/предупреждение из журнала).

## Автозапуск

- `BootReceiver` (BOOT_COMPLETED, QUICKBOOT_POWERON, MY_PACKAGE_REPLACED) запускает тоннель,
  если включён `auto_start_on_boot` и разрешение уже выдано (`VpnService.prepare == null`).
- Always-on из системных настроек стартует сервис без action — он подключается к активному серверу.
