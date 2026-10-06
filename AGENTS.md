# Работа в colabike-android

Нативный Android-клиент ColaBike. Сервер, API v1 и веб — в [grayhex/cola](https://github.com/grayhex/cola). Перед задачей прочитайте нужное: [архитектура](docs/architecture.md), [дизайн](DESIGN.md) и его источник [Twilight Stillness](docs/design/twilight-stillness.md), [решения платформы](docs/adr/0001-platform-baseline.md) и [визуальное направление и навигация](docs/adr/0002-visual-direction-and-shell.md), [гость, устройства и сценарии сайта](docs/adr/0003-account-guest-and-web-flows.md), [поиск, лайк и карточка велосипеда](docs/adr/0004-bikes-search-like-and-page.md), [люди, подписки и поиск](docs/adr/0005-people-follow-and-search.md), [лента, журнал и сохранённое](docs/adr/0006-feed-journal-and-saved.md), [комментарии](docs/adr/0007-comments.md), [покатушки](docs/adr/0008-rides-lists-plans-and-page.md), [карта маршрута и разбор](docs/adr/0009-route-map-and-analysis.md), [уведомления](docs/adr/0010-notifications-inbox.md), [мессенджер](docs/adr/0011-messenger.md), [каталог компонентов](docs/adr/0012-component-catalog.md), [рынок](docs/adr/0013-market.md), [удалённая конфигурация](docs/adr/0014-remote-app-config.md), [push-уведомления](docs/adr/0015-push-notifications.md), [настройки уведомлений](docs/adr/0016-notification-settings.md), [регистрация push и RuStore](docs/adr/0017-push-registration-and-rustore.md), [покатушки рядом](docs/adr/0018-nearby-rides.md), [намерения «Хочу кататься»](docs/adr/0019-ride-intentions.md), [участие в выезде](docs/adr/0020-ride-participation.md), [удаление аккаунта, жалобы и блокировка](docs/adr/0021-account-deletion-and-safety.md), [приёмка alpha](docs/acceptance.md) и [экраны и операции API](docs/screen-api-mapping.md).

## Команды

- `./gradlew verify` — единственная проверка перед PR. Она запускает формат (Spotless/ktfmt), контрольную сумму контракта, Android Lint без предупреждений, отсутствие Play-зависимостей в classpath, unit- и скриншот-тесты, debug-сборку. CI запускает ту же команду.
- `./gradlew spotlessApply` — отформатировать код.
- `./gradlew :app:recordRoborazziDebug :core:designsystem:recordRoborazziDebug` — перезаписать эталонные скриншоты после осознанного изменения UI. Затем посмотрите diff PNG.
- `./gradlew :core:network:updateApiContract` — обновить снимок `api/openapi.json` и его SHA-256 из production. Затем проверьте diff контракта и сгенерированного клиента.
- `./gradlew :app:assembleRelease` — release с R8 (без подписи). `scripts/check-native-alignment.sh <apk>` проверяет, что нативные библиотеки готовы к страницам 16 КБ; установка, обновление и подпись — [docs/install.md](docs/install.md).
- `scripts/cert-fingerprint.sh <apk|keystore>` — SHA-256 сертификата подписи для `ANDROID_CERT_SHA256` на сайте (App Link Яндекса). Печатает только публичный отпечаток.
- Нужны JDK 21 и Android SDK с platform 37: `sdk.dir` в `local.properties` или `ANDROID_HOME`.

## Skills

Официальные Android skills ([android/skills](https://github.com/android/skills), Apache 2.0) лежат в `.claude/skills`, закреплены на коммите из `SOURCE.txt`:

- `adaptive` — адаптивная навигация и multi-pane через Navigation 3 Scenes;
- `navigation-3` — ключи, back stack, сцены, deep links, ViewModel;
- `edge-to-edge` — insets, IME, читаемость системных панелей;
- `testing-setup` — стратегия тестов и скриншоты;
- `android-intent-security` — входящие intent и App Link.

Claude Code подхватывает их сам; другим агентам их нужно читать как обычный Markdown перед работой в своей области. Обновлять копии только через `scripts/update-android-skills.sh` и ревью diff, руками не править. Если skill противоречит этому файлу, приоритет у этого файла: здесь записаны решения проекта (например, Robolectric + Roborazzi для скриншотов, ручной DI).

## Границы

- Модули: `app` (shell, навигация, экраны), `core:model` (модели приложения и интерфейсы репозиториев, чистый Kotlin), `core:network` (сгенерированный клиент API v1, транспорт, маппинг DTO → модели, чистый Kotlin), `core:auth` (сессия устройства и токены), `core:designsystem` (тема, токены, компоненты). Feature-модуль заводится только вместе с реальным вертикальным срезом.
- UI: UDF. ViewModel отдаёт неизменяемый `StateFlow<…UiState>`, Composable не ходит в HTTP и не видит DTO. Экран получает репозитории из `AppDependencies`, а не HTTP-клиенты.
- Генерированный код (`core/network/build/generated`) не правится руками. Поведение меняется настройкой генератора в `core/network/build.gradle.kts` или контрактом на сервере.
- Ошибки данных — только `DataError`. Текст для людей — через `UiText`/`toUiText()`, один маппинг на все экраны.
- Новый UI — только Compose и компоненты `core:designsystem`. Цвета, размеры и шрифты берутся из темы, без сырых значений.

## Запреты

- Токены, пароль, код `cola_ac_…` и PKCE-verifier не попадают в Logcat, аналитику, crash reports, URL, `toString()`, скриншоты и резервные копии. Не добавляйте HTTP-логирование тел, не логируйте заголовок `Authorization`.
- Не использовать `EncryptedSharedPreferences`, WebView для входа, Google Play Services, Play Billing, Play Integrity и in-app updates.
- Release keystore, `keystore.properties` и секреты не коммитятся. Тестовые учётные данные живут только в secrets CI.
- Тесты и превью не обращаются к production: только фейки и MockWebServer. Исключение — live smoke в `smoke.yml` под отдельным аккаунтом.
- Нельзя ослаблять `verify`: отключать lint-проверки, удалять тесты, пропускать скриншоты, ставить alpha-версии без записи в ADR.

## Definition of done

- `./gradlew verify` зелёный. Новая логика покрыта unit-тестами, новый компонент или экран — скриншотами в светлой и тёмной теме, экран — ещё и в compact и expanded окне.
- TalkBack: у интерактивных элементов есть роль и подпись, карточка читается одной фразой, заголовки помечены `heading()`, ошибки объявляются (`liveRegion`).
- Шрифт 200 % не ломает раскладку (скриншот `*_font_200`), цели касания не меньше 48 dp, при отключённых анимациях ничего не мигает.
- Edge-to-edge: контент не уходит под системные панели, predictive back работает через Navigation 3.
- Изменили поведение — обновите главу в `docs/` или `DESIGN.md` в том же PR. В описании PR: проблема, результат, проверки, что не проверено.
