# Приёмка alpha

Что значит «alpha готова», чем это подтверждено и чего не подтверждено. Это интеграционная приёмка продукта (задача [#11](https://github.com/grayhex/colabike-android/issues/11)), а не повтор CI foundation: тут нет новых правил, только перечень сценариев, проверок и оставшихся ограничений. Состояние на 04.10.2026.

## Граница alpha

Вход → лента и гараж → велосипед и профиль → журнал и обсуждение → покатушка и карта → личный диалог → выход. Работают лайки, подписки, сохранение записей журнала и запись комментариев, которые есть в API v1. Смена аккаунта не оставляет чужого содержимого. Каталог компонентов, барахолка (#9), авторские сценарии (#10) и remote config (#14) в alpha не входят.

## Как читать таблицы

Три уровня проверки, и они не заменяют друг друга:

- **CI, моки.** Фейковые репозитории, `MockWebServer`, Robolectric и Roborazzi в `./gradlew verify`. Доказывает логику и вид экранов, но не работу на устройстве и не живой сервер.
- **Эмулятор.** `smoke.yml`: Android 17 (`google_apis`), Keystore, запуск, а с secrets `COLABIKE_SMOKE_EMAIL` / `COLABIKE_SMOKE_PASSWORD` живой read-only сценарий под отдельным аккаунтом. Без secrets живой сценарий исключён из прогона, и job об этом говорит; зелёным за фальшивые данные он не становится.
- **Руками.** Проверка человеком на устройстве с тестовыми аккаунтами. Разрушительных автоматических тестов на production нет и не будет.

## Сценарии и операции API

| Сценарий | Операции API v1 | CI, моки | Эмулятор, живой | Руками |
| --- | --- | --- | --- | --- |
| Вход паролем, гость, выход | `POST /auth/sessions`, `POST /auth/sessions/refresh`, `GET /me`, `DELETE /auth/sessions/current` | `AccountFlowTest`, `AuthControllerTest`, `DeviceSessionTest` (обновление токена, потерянный ответ, 5xx, 429, параллельные запросы) | `StartupTest` (чистая установка открывает вход), `LiveSmokeTest` (вход, `/bikes`, велосипед, `/me`, выход) | вход с настоящим аккаунтом |
| Вход через Яндекс ID | `POST /auth/sessions` (код + PKCE) | `YandexSignInTest`, `AuthControllerTest` | нет | **ждёт владельца**: App Link, `ANDROID_CERT_SHA256`, кнопка включается `-Pcolabike.yandexSignIn=true` |
| Велосипеды, гараж, карточка | `GET /bikes`, `GET /bikes/{id}`, `PUT`/`DELETE /bikes/{id}/like`, `GET /experience/bikes` | `BikeFlowTest`, `BikeDetailTest`, `BikesSearchTest`, `RepositoriesTest` | `LiveSmokeTest` (список и карточка) | лайк на своём тестовом велосипеде |
| Люди, подписки | `GET /users/{ref}`, `GET /users/{ref}/followers`, `PUT`/`DELETE /users/{id}/follow` | `PeopleFlowTest`, `PeopleTest` | нет | подписка на тестовый аккаунт |
| Лента, журнал, сохранённое | `GET /me/feed`, `GET /bikes/{id}/journal`, `GET /journal/{id}`, `PUT`/`DELETE /journal/{id}/save` | `FeedFlowTest`, `FeedTest`, `FeedRepositoryTest` | нет | сохранение записи |
| Комментарии | `GET`/`POST`/`PATCH`/`DELETE …/comments`, ответы, `focus` | `CommentsFlowTest`, `CommentsTest`, `CommentsRepositoryTest` (`Idempotency-Key`: повтор не даёт второго комментария) | нет | запись и ответ тестовым аккаунтом |
| Покатушки, карта, разбор | `GET /rides`, `GET /rides/{id}`, `GET /rides/{id}/analysis`, личные списки | `RidesFlowTest`, `RidesTest`, `AnalysisChartsTest` | `RouteMapRenderTest` (маршрут на настоящей карте в обеих темах) | **ждёт владельца**: источник тайлов `colabike.mapStyleUrl` |
| Уведомления | `GET /me/notifications`, `GET /me/notifications/count` | `NotificationsFlowTest`, `NotificationsTest`, `NotificationsRepositoryTest` | нет | колокольчик с настоящими событиями |
| Личные сообщения | `POST /chat/token`, `POST /chat/channels`, `GET /chat/people`; дальше SDK Stream Chat напрямую | `MessagesFlowTest`, `ChatSessionTest`, `NewConversationTest`, `WriteViewModelTest`, `ChatRepositoryTest` | нет: в CI SDK к провайдеру не подключается | **ждёт владельца**: два подтверждённых аккаунта, обмен, переподключение ([ADR 0011](adr/0011-messenger.md)) |
| Выход и смена аккаунта | `DELETE /auth/sessions/current` | `SignOutsTest`, `SessionScopeTest`, `SessionStoresTest`, `DevicesTest` | `LiveSmokeTest` (выход) | вход вторым аккаунтом после первого |

## Устройства, окна, доступность

| Что | Чем подтверждено | Чего нет |
| --- | --- | --- |
| Android 17 (API 37) | эмулятор `google_apis` в `smoke.yml` | реального устройства |
| minSdk 31 | Robolectric и lint с `minSdk = 31`; в [PR поставки](https://github.com/grayhex/colabike-android/issues/11) добавляется эмулятор API 31 | запуска на реальном устройстве с Android 12 |
| Телефон и широкое окно, светлая и тёмная темы | Roborazzi: `compact` и `expanded`, `light` и `dark` для каждого экрана | складного устройства, режима разделённого экрана |
| 200 % шрифт | скриншоты `*_font200` для каждого экрана | — |
| TalkBack | роли, подписи, `heading()`, `liveRegion` проверяются тестами по семантике | прослушивания живым TalkBack (руками) |
| IME и edge-to-edge | форма входа использует `imePadding()`, поле комментария — `WindowInsets.safeDrawing` (в нём клавиатура); верх и низ экранов не уходят под системные панели | показа клавиатуры: Robolectric её не рисует, проверять на устройстве; SDK чата ведёт своё поле ввода сам |
| Predictive back | `android:enableOnBackInvokedCallback`, `NavDisplay`; «назад» нажимается в `ShellNavigationTest` и `ProcessDeathTest` | жеста «назад» с анимацией на устройстве |
| Без Google Play Services | `checkNoPlayServices` в `verify` (ни Play Services, ни Firebase, ни Billing, ни Integrity, ни push-вендоров в classpath release и debug); эмулятор без GMS — [PR поставки](https://github.com/grayhex/colabike-android/issues/11) | устройства вендора (Huawei, Xiaomi) |

## Надёжность

| Вопрос из задачи | Как устроено | Чем подтверждено |
| --- | --- | --- |
| Поворот и смерть процесса | ключи `Destination` сохраняются как JSON, ViewModel грузят данные заново | `ProcessDeathTest` (раздел, открытый велосипед и диалог переживают восстановление, «назад» работает), `DestinationSerializationTest` (каждый ключ возвращается из сохранённого вида, новый ключ без теста ломает сборку) |
| Фон и возврат | токен обновляется при возвращении; сокет чата SDK закрывает сам, фоновых соединений и служб нет | `DeviceSessionTest`, манифест без foreground-службы и автозапуска (`ManifestPermissionsTest`) |
| Нет сети, таймаут | `Offline` → экран ошибки с повтором, список остаётся; таймауты 15/30/60 с | `ApiCallTest`, ошибки с повтором в `*FlowTest` |
| 429 | «попробуйте через N минут» по `Retry-After`, автоматического повтора нет | `ApiCallTest`, `ViewModelTest` |
| 5xx | «сервер не ответил» с кодом для поддержки (`X-Request-ID`), автоматического повтора нет | `ApiCallTest` |
| Потерянный ответ на запись | комментарии, чат (`POST /chat/channels`) уходят с `Idempotency-Key`, повтор того же текста несёт тот же ключ; лайки, подписки, сохранение идемпотентны сами | `CommentsRepositoryTest`, `NewConversationTest` |
| Одновременное истечение токена | один запрос обновления на всех | `DeviceSessionTest` |
| Короткая потеря связи не выходит из аккаунта | обмен на refresh-токен после сбоя повторяется, учётные данные остаются | `DeviceSessionTest` |
| Нет бесконечных повторов | в коде нет циклов повторов: повтор — только по нажатию человека, чат подключается заново только по кнопке | аудит `grep` по `app` и `core` |
| Чужой кэш после смены аккаунта | `SessionScope` очищает ViewModel, `AppGraph` по выходу стирает черновики, сохранённое и базу SDK чата, кэш картинок очищается | `SessionScopeTest`, `SessionStoresTest`, `ChatSessionTest` |

## Сборка и поставка

- **R8.** `:app:assembleRelease` собирается с SDK чата, картой и сгенерированным клиентом. Сериализаторы `Destination` и API-моделей остались в `mapping.txt` (правило `-keepclassmembers` в `proguard-rules.pro`). Запуск release на устройстве пока не проверялся: он войдёт в [PR поставки](https://github.com/grayhex/colabike-android/issues/11).
- **Манифест release.** Только `INTERNET`, `ACCESS_NETWORK_STATE` и `WAKE_LOCK` (последний нужен WorkManager). Микрофона, уведомлений, автозапуска, foreground-службы, push-провайдера и экспортируемого `PreviewActivity` нет.
- **Размер.** Release 53 МБ без подписи, в основном четыре ABI библиотеки карты. Фильтры ABI — решение владельца ([архитектура](architecture.md)).
- **16 КБ страницы.** В `arm64-v8a` и `x86_64` все сегменты `LOAD` выровнены на 16 КБ и `zipalign -P 16` проходит. В 32-битных `armeabi-v7a` и `x86` `libmaplibre.so` выровнена на 4 КБ: требование 16 КБ к 32-битным ABI не предъявляется.
- **Версии.** `versionCode = 1`, `versionName = 0.1.0` в `app/build.gradle.kts`. Для обновления поверх установленной сборки `versionCode` растёт, а подпись остаётся той же.

## Диагностика без утечек

На экране «О приложении» показаны версия, номер сборки и версия контракта API с началом контрольной суммы снимка. Сбой сервера (`5xx`) показывает код для поддержки (`X-Request-ID`), отказ без слов от сервера — статус, код и `X-Request-ID`. Пароль, токены, текст переписки, URL с секретами и точные приватные координаты в диагностику, `toString()` и логи не попадают. Внешних crash- и analytics-SDK нет и автоматически они не подключаются.

## Измерения

Запуск, прокрутка ленты с фото, открытие карточки и карты, память **на устройстве не измерялись**: в среде, где собиралась alpha, нет устройства, а цифры эмулятора на `swiftshader` не показывают реальной плавности. Измерять так, чтобы результат повторялся:

1. Release-сборка (`:app:assembleRelease`), подписанная любым ключом, на устройстве, не на эмуляторе, режим самолёта выключен, аккаунт тестовый.
2. Холодный запуск: `adb shell am start -W -S ru.colabike.app/.MainActivity`, десять раз, в отчёт идёт медиана `TotalTime`.
3. Лента: открыть «Велосипеды», `adb shell dumpsys gfxinfo ru.colabike.app reset`, десять раз проскроллировать список (`adb shell input swipe`), снять `dumpsys gfxinfo ru.colabike.app` и записать долю «janky frames».
4. Карточка и карта: то же для открытия карточки велосипеда и карты маршрута (время до первого кадра с содержимым).
5. Память: `adb shell dumpsys meminfo ru.colabike.app` после сценария выше, строка `TOTAL PSS`.

Macrobenchmark и Baseline Profile добавляются для путей, где эти измерения покажут проблему, и с записью результата до и после.

## Оставшиеся ограничения

- Живой обмен сообщениями, authenticated live-smoke и вход через Яндекс ID проверяет владелец (данные и секреты у него).
- Карта рисует маршрут без подложки, пока не выбран источник тайлов.
- Подписанный release-кандидат и публикация в RuStore — отдельное решение владельца (keystore, отпечаток для App Link). Условия Stream License (аккаунт клиента Stream, ограничения на открытое ПО и конкурентов) прочитать и подтвердить тоже ему: см. [ADR 0011](adr/0011-messenger.md).
- Не делается: полноценный offline-first режим, push, GPS-рекордер, офлайн-карты.
