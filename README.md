# ColaBike для Android

Нативный клиент [ColaBike](https://colabike.ru): Kotlin, Jetpack Compose, Material 3, Navigation 3, адаптивные макеты, Android 17 (compileSdk/targetSdk 37, minSdk 31).

```sh
./gradlew verify            # формат, контракт, lint, unit- и скриншот-тесты, debug APK
./gradlew :app:installDebug # на устройство или эмулятор
```

- [AGENTS.md](AGENTS.md) — команды, границы, запреты, definition of done.
- [docs/architecture.md](docs/architecture.md) — модули, поток данных, навигация, токены, контракт API.
- [DESIGN.md](DESIGN.md) — Figma, токены, адаптивность, состояния, доступность.
- [docs/adr/0001-platform-baseline.md](docs/adr/0001-platform-baseline.md) — принятые решения о платформе.

Публикация — позже в RuStore. Google Play не цель.
