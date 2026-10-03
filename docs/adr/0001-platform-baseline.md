# ADR 0001. Базовая платформа Android-клиента

Статус: принято в рамках [cola#325](https://github.com/grayhex/cola/issues/325), 03.10.2026. Изменения этих решений оформляются новым ADR.

## minSdk 31 (Android 12)

compileSdk и targetSdk — 37 (Android 17). minSdk выбирается один раз, с приоритетом современного API.

Почему 31, а не ниже:
- одна модель проверки App Links (`DomainVerificationManager`, поведение Android 12+). Вход через Яндекс ID возвращается verified App Link, и непроверенная ссылка уходит в браузер, а не в диалог выбора;
- одни правила резервного копирования (`dataExtractionRules`), без legacy `fullBackupContent`;
- системный splash, `PendingIntent` с обязательной изменяемостью и dynamic color есть всегда, без compat-веток.

Почему не 33: нативный photo picker и `OnBackInvokedCallback` в базе не дали бы ничего, чего нет в AndroidX на 31–32. При этом 33 отрезало бы заметную долю аудитории в России. Для фото на 31–32 `PickVisualMedia` откатывается на системный выбор документов, и Google Play Services для этого не нужны.

## Только stable-версии

Версии закреплены в `gradle/libs.versions.toml`, alpha, beta и rc не используются (проверено 03.10.2026 по Google Maven и Maven Central):

| Компонент | Версия |
| --- | --- |
| AGP | 9.4.1 |
| Gradle | 9.8.0 (wrapper с SHA-256) |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 (Compose 1.12.1, material3 1.4.0) |
| Navigation 3 | 1.2.0 |
| material3-adaptive | 1.3.0 |
| Lifecycle | 2.11.0 |
| OkHttp | 5.5.0 |
| kotlinx.serialization | 1.11.0 |
| Coil | 3.6.3 |
| OpenAPI Generator | 7.25.0 |
| Robolectric | 4.17 |
| Roborazzi | 1.76.0 |
| Espresso | 3.7.0 |

Espresso 3.7.0 обязательна: в Android 17 нет `InputManager.getInstance()`, и старые версии под Robolectric SDK 37 падают.

## Material 3 Expressive — по мере стабилизации

Issue требует Material 3 Expressive как системную базу. Но в stable material3 1.4.0 `MaterialExpressiveTheme`, `MotionScheme` и Expressive-компоненты (`ButtonGroup`, `LoadingIndicator`, `FloatingToolbar`) объявлены internal. Публично они есть только в 1.5.0-alpha.

Выбрали stable 1.4 и Expressive там, где он доступен:
- формы с крупными радиусами;
- токены пружин Expressive в `ColaMotion`;
- навигационный suite.

Когда выйдет stable material3 1.5, тема переходит на `MaterialExpressiveTheme`, `ColaMotion` удаляется, Expressive-компоненты подключаются по одному со скриншотами.

Альтернатива — 1.5.0-alpha ради API, которые ещё меняются. Отклонена: фундамент не должен переписываться из-за переименований в alpha.

## Модули и DI

- Пять модулей из issue: `app`, `core:model`, `core:network`, `core:auth`, `core:designsystem`. `core:model` и `core:network` — чистый Kotlin, они тестируются на JVM без Android.
- Ручной DI: `AppGraph` в `app`. Объектов около десяти, и фреймворк (Hilt/KSP) добавил бы время сборки и магию, не закрыв ни одной задачи. Hilt появится, если граф перестанет помещаться в один читаемый файл: это отдельное решение.
- Convention plugins (`build-logic`) пока не заводим: при пяти модулях общие значения лежат в version catalog (`compileSdk`, `minSdk`, `jvmTarget`).

## Клиент API v1

OpenAPI Generator `kotlin` / `jvm-okhttp4` с kotlinx.serialization вместо Moshi. Причины:
- шаблон Moshi с `enumUnknownDefaultCase` падает на `null` в nullable enum;
- Moshi в шаблоне работает через reflection (`kotlin-reflect`) и требует отдельных правил R8.

Настройки и runtime-правку `explicitNulls = false` описывает [архитектура](../architecture.md#api-v1-и-контракт). Сгенерированный код не коммитится и не правится: он собирается из `api/openapi.json`, снимок закреплён SHA-256.

## Тесты UI

Скриншоты пишем на Robolectric + Roborazzi с нативной графикой, на JVM, без эмулятора. Так они быстрые и детерминированные, и запускаются в той же команде `verify`. Compose Preview Screenshot Testing от Google не выбрали: на 03.10.2026 он не опубликован как stable.

Эмулятор нужен там, где без него нельзя проверить. Это Android Keystore и live smoke против production, они собраны в отдельном `smoke.yml`.

## Дистрибуция

Публикация — RuStore, позже и отдельным этапом. В приложении нет Google Play Services, Play Billing, Play Integrity, in-app updates и провайдера шрифтов. Release keystore хранится вне Git, а SHA-256 его сертификата нужен cola#324 для `assetlinks.json`.
