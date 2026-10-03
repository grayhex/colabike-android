# ColaBike для Android

Нативный клиент [ColaBike](https://colabike.ru): Kotlin, Jetpack Compose, Material 3, Navigation 3, адаптивные макеты, Android 17 (compileSdk/targetSdk 37, minSdk 31).

```sh
./gradlew verify            # формат, контракт, lint, unit- и скриншот-тесты, debug APK
./gradlew :app:installDebug # на устройство или эмулятор
```

- [AGENTS.md](AGENTS.md) — команды, границы, запреты, definition of done.
- [docs/architecture.md](docs/architecture.md) — модули, поток данных, навигация, токены, контракт API.
- [DESIGN.md](DESIGN.md) — тема, компоненты, разделы и навигация, адаптивность, состояния, доступность.
- [docs/design/twilight-stillness.md](docs/design/twilight-stillness.md) — визуальный источник и соответствие токенам.
- [docs/adr/0001-platform-baseline.md](docs/adr/0001-platform-baseline.md) — принятые решения о платформе.
- [docs/adr/0002-visual-direction-and-shell.md](docs/adr/0002-visual-direction-and-shell.md) — визуальное направление и навигационная оболочка.
- [docs/adr/0003-account-guest-and-web-flows.md](docs/adr/0003-account-guest-and-web-flows.md) — гость, устройства аккаунта и сценарии, которые остаются на сайте.

Публикация — позже в RuStore. Google Play не цель.
