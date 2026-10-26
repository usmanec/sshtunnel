# Версии, релизы и обновления

## Версия сборки

Версия берётся из git (`app/build.gradle.kts`):

- `versionCode` = `git rev-list --count HEAD`;
- `versionName` = `git describe --tags --dirty` без префикса `v` (например, `1.2.0`, `1.2.0-3-gabc1234`,
  `1.2.0-dirty`); если тегов нет — `0.0.1-dev-<hash>`.

Нужна полная история: в shallow-клоне `versionCode` будет маленьким, а тег не найдётся.

## Выпуск релиза

```bash
git tag v1.2.0 && git push origin v1.2.0
```

Тег вида `vX.Y.Z` запускает `.github/workflows/release.yml`: `gradle :app:assembleRelease`,
подпись, `apksigner verify` и GitHub Release с `app-release.apk`.

Секреты репозитория (Settings → Secrets and variables → Actions):

| Секрет | Что это |
|---|---|
| `KEYSTORE_GIT_REPOSITORY` | приватный репозиторий с keystore (`owner/repo`) |
| `KEYSTORE_ACCESS_TOKEN` | токен с правом чтения этого репозитория |
| `KEYSTORE_FILE` | путь к keystore относительно модуля `app` (репозиторий клонируется в `app/keystore`, например `keystore/release.jks`) |
| `KEYSTORE_PASSWORD` | пароль keystore |
| `RELEASE_SIGN_KEY_ALIAS` | алиас ключа |
| `RELEASE_SIGN_KEY_PASSWORD` | пароль ключа |

Без этих переменных окружения `assembleRelease` локально собирается как раньше (блоки signingConfigs
из AI Studio не тронуты). **Все релизы подписываются одним ключом**: Android не ставит обновление
с другой подписью.

## Обновление внутри приложения (Settings → Updates)

- `update/ReleaseInfo` (чистый Kotlin, тесты `update/ReleaseInfoTest`) разбирает ответ
  `GET /repos/usmanec/sshtunnel/releases/latest`: пропускает draft и prerelease, берёт первый `.apk`,
  сравнивает только числовую часть версии (`1.2.0-3-gabc` не старше `v1.2.0`, откат не предлагается).
- `update/UpdateManager` скачивает APK в `getExternalFilesDir(DOWNLOADS)/update.apk`, проверяет размер,
  имя пакета и SHA-256 сертификатов подписи против установленного приложения. Неподходящий файл
  удаляется, пользователь видит причину.
- Установка — через `FileProvider` (`${applicationId}.provider`, `res/xml/provider_paths.xml`) и
  `ACTION_VIEW`. На Android 8+ сначала нужно разрешение «Установка неизвестных приложений»
  (`REQUEST_INSTALL_PACKAGES`); если на ТВ нет такого экрана, показывается ошибка.
- Состояние — `MainViewModel.updateState`; проверка автоматически выполняется один раз за запуск
  при открытии настроек, дальше — кнопкой.
- Запросы к GitHub идут напрямую, не через тоннель (приложение исключено из своего тоннеля).
