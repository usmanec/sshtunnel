# CLAUDE.md

Все инструкции для агентов лежат в [AGENTS.md](AGENTS.md), документация по подсистемам — в [docs/](docs/README.md).

@AGENTS.md

## Дополнительно для Claude Code

- В облачной песочнице Android SDK и Google Maven (`dl.google.com`) обычно недоступны, `gradle :app:*`
  не соберётся. Проверяйте код через `tools/sandbox-check` (Maven Central доступен):
  ```bash
  cd tools/sandbox-check/net-tests && gradle --no-daemon test             # JVM-тесты сетевого ядра
  cd tools/sandbox-check/compile && gradle --no-daemon compileKotlin compileJava   # компиляция всего app
  ```
  Если в коде появился новый Android-only API (androidx.activity, core, Room и т. п.), добавьте
  минимальный стаб в `tools/sandbox-check/compile/stubskt` (так уже сделано, например, для
  `WindowInsets.isImeVisible`, который есть только в Android-версии Compose).
- Исходники JSch для сверки внутренностей: `https://repo1.maven.org/maven2/com/github/mwiede/jsch/0.2.21/jsch-0.2.21-sources.jar`.
- Перед пушем перечитайте diff: импорты, порядок инициализации, фокус на ТВ, отсутствие обходов тоннеля.
- PR не создавайте, пока пользователь не попросит.
