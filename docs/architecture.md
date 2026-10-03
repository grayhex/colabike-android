# Архитектура

## Модули

| Модуль | Тип | Что внутри | Зависит от |
| --- | --- | --- | --- |
| `app` | Android application | `MainActivity`, `AppGraph` (ручной DI), shell и навигация, экраны со своими ViewModel | все `core` |
| `core:model` | Kotlin/JVM | модели приложения (`BikeSummary`, `BikeDetail`, `Account`, `Person`, `RideSummary`, `Page`), интерфейсы репозиториев, `DataError` | — |
| `core:network` | Kotlin/JVM | снимок контракта, сгенерированный клиент `ru.colabike.api`, `HttpClients`, `ColaBikeApi`, `apiCall`, `MediaUrls`, маппинг DTO → модели, сетевые репозитории | `core:model` |
| `core:auth` | Android library | `DeviceSession`, `AuthInterceptor`, хранение секретов (`EncryptedFileStore` + `KeystoreTokenCipher`), PKCE и вход через Яндекс ID | `core:network` |
| `core:designsystem` | Android library + Compose | `ColaBikeTheme`, палитра, шрифт, формы, `ColaMotion`, компоненты | `core:model` |

`core:model` и `core:network` не знают об Android: их тесты быстрые и идут на JVM. Feature-модули появятся вместе с вертикальными срезами. Тогда экран, его ViewModel и репозиторий переедут из `app` в `feature:<имя>`.

## Поток данных

```
Composable ──события──▶ ViewModel ──suspend──▶ Repository (интерфейс в core:model)
    ▲                       │                        │
    └──── StateFlow<UiState>┘                        ▼
                                  NetworkXxxRepository ─▶ apiCall { generated API } ─▶ OkHttp
```

- Экран состоит из двух функций. `XxxRoute` создаёт ViewModel (`viewModel { … }`) и собирает состояние через `collectAsStateWithLifecycle`. `XxxScreen` не хранит состояние: принимает `UiState` и лямбды. Превью и скриншоты рисуют `XxxScreen` с фейковым состоянием.
- `UiState` — неизменяемый `data class` или `sealed interface`. ViewModel обновляет его только через `MutableStateFlow.update`.
- DTO из `ru.colabike.api.models` до экрана не доходят. Маппинг живёт в `core/network/.../Mappers.kt`: относительные пути фото становятся абсолютными, неизвестные значения enum получают безопасную замену, почта из `/me` в модель не попадает.
- Каждый сбой превращается в `DataError` ровно в одном месте, в `apiCall`:
  - `Offline` — сеть;
  - `SignedOut` — 401 `invalid_token` / `unauthorized`;
  - `NotFound`;
  - `RateLimited` — значение `Retry-After`;
  - `Rejected` — код и сообщение API;
  - `Server` — с `X-Request-ID` для поддержки;
  - `Unexpected`.

  Слова для людей подбирает `UiText`/`toUiText()` в `app`.

## Навигация

- Navigation 3. Ключи — `@Serializable` объекты `Destination` (`Bikes`, `Bike(id)`, `Profile`), поэтому back stack переживает смерть процесса. В ключ кладётся только id: экран сам загружает то, что показывает.
- Корень (`ColaBikeApp`) смотрит на `AuthState`:
  - `Restoring` — пустой фон;
  - `SignedOut` — вход;
  - `SignedIn` — `AppShell`.

  Вход не лежит в back stack, поэтому системный «назад» не возвращает на него.
- `AppShell` строится на `NavigationSuiteScaffold`: нижняя панель на compact-ширине и rail на medium и expanded. Вкладка владеет всем стеком, переключение вкладки сбрасывает стек (`selectTab`).
- Список и карточка — `ListDetailSceneStrategy` из material3-adaptive-navigation3. От 840 dp список и велосипед стоят рядом, а без выбранного велосипеда справа показывается заглушка. Уже 840 dp видна одна панель. `openBike` заменяет открытый велосипед, а не накапливает их.
- ViewModel экранов после входа живут в хранилище сессии (`SessionScope`), а не activity. Выход очищает его, поэтому следующий аккаунт не увидит ничего от предыдущего. Кэш картинок Coil при выходе тоже очищается.
- Predictive back: `android:enableOnBackInvokedCallback` и `NavDisplay`. ViewModel живёт столько же, сколько запись стека (`rememberViewModelStoreNavEntryDecorator`), а сохраняемое состояние — `rememberSaveableStateHolderNavEntryDecorator`.

## Сессия устройства и токены

Серверная сторона описана в cola: [`docs/modules/api-v1.md`, «Сессии устройств»](https://github.com/grayhex/cola/blob/main/docs/modules/api-v1.md).

- Вход:
  - по почте и паролю — `POST /auth/sessions`;
  - по коду нативного потока Яндекс ID — тот же адрес с полями `code` и `codeVerifier`.

  Ответ `SessionGrant` несёт пару токенов и `Me`.
- Токен доступа (`cola_at_…`, 15 минут) живёт только в памяти `DeviceSession`. Refresh-токен (`cola_rt_…`) хранится в `noBackupFilesDir/session/refresh.bin`, зашифрованный AES-256-GCM. Ключ лежит в Android Keystore, не экспортируется и не покидает устройство. Формат файла: байт версии, IV 12 байт, шифртекст с тегом. `EncryptedSharedPreferences` не используется.
- `AuthInterceptor` добавляет `Authorization: Bearer` только к запросам на хост сайта. Картинки и ссылки других хостов токена не видят.
  - Токен истекает меньше чем через 30 секунд, или его нет после холодного старта — сначала refresh, потом запрос.
  - 401 `token_expired` — один refresh на всех. Его сериализует `ReentrantLock`: опоздавший поток получает уже новый токен. Затем исходный запрос повторяется ровно один раз.
  - 401 `invalid_token` или отказ refresh — сессия очищается полностью (память и файл). `AuthState` становится `SignedOut`, корень показывает вход.
  - Потерянный ответ refresh (`IOException`) — один повтор с тем же refresh-токеном. Сервер принимает его ещё 30 секунд, повторное использование не засчитывается.
- Выход: сначала `DELETE /auth/sessions/current` (best effort), затем локальная очистка, даже без сети.
- Яндекс ID (cola #304):
  1. `YandexSignIn.startUrl()` создаёт PKCE и сохраняет verifier зашифрованным (`pkce.bin`), потому что процесс может умереть, пока впереди браузер.
  2. Custom Tabs (не WebView) открывают `/api/auth/native/start?provider=yandex&code_challenge=…&code_challenge_method=S256`.
  3. Сайт возвращает verified App Link `https://colabike.ru/app/auth?code=cola_ac_…` или `?error=…`. `MainActivity` (`singleTask`) передаёт его в `AuthController.handleLink`.
  4. Verifier выдаётся один раз и стирается.

  Кнопка включается флагом `-Pcolabike.yandexSignIn=true`, пока на сайте нет `NATIVE_AUTH_RETURN_URL` и `assetlinks.json` (cola #324).
- Утечек быть не должно:
  - HTTP-логирования нет;
  - `toString()` у `Token`, `Pkce`, `YandexSignIn.Return.Code` и `LoginUiState` значения не печатает;
  - `android:allowBackup="false"`, а `data_extraction_rules.xml` исключает всё и из облачной копии, и из переноса между устройствами.

## API v1 и контракт

- Источник истины — `https://colabike.ru/api/v1/openapi.json`. В репозитории лежит снимок `api/openapi.json` (pretty JSON) и его SHA-256 в `api/openapi.json.sha256`. Задача `verifyApiContract` входит в `check` и `verify`, поэтому изменённый снимок без новой суммы ломает сборку.
- Обновление: `./gradlew :core:network:updateApiContract`, затем ревью diff. Еженедельный workflow `contract.yml` сравнивает снимок с production (`checkApiContractDrift`).
- Генерация: OpenAPI Generator 7.25.0 со следующими настройками:
  - `kotlin`, `jvm-okhttp4`;
  - `serializationLibrary=kotlinx_serialization`;
  - `enumUnknownDefaultCase=true` — новое значение enum на сервере не роняет разбор у выпущенного приложения;
  - `number → kotlin.Double`.

  Почему kotlinx, а не Moshi: шаблон Moshi регистрирует адаптеры enum без `nullSafe()` и падает на `null` (проверено в cola#325). Во время выполнения `ColaBikeApi` один раз включает `explicitNulls = false`, иначе строгая схема входа отвечает 400 на `"code": null`.
- Перед генерацией `prepareGeneratorInput` превращает `const`/`enum` у `boolean` в обычный `boolean`: такая константа не компилируется с fallback для enum. На сервере это уже запрещено тестом (cola#325), а нормализация оставляет собираемыми старые снимки.
- `ApiConfig.siteUrl` берётся из `BuildConfig.SITE_URL`. Staging нет, поэтому debug и release ходят на `https://colabike.ru`. Тесты и превью работают на фейках (`app/src/test/.../Fakes.kt`) и MockWebServer, production не меняют.

## Тесты и CI

| Где | Что | Чем |
| --- | --- | --- |
| `core:network` | маппинг, коды ошибок, `Retry-After`, `X-Request-ID`, медиа-URL, тело запроса без `null` | JUnit + MockWebServer |
| `core:auth` | вход, single-flight refresh под параллельной нагрузкой, повтор, `invalid_token`, потерянный ответ, восстановление после рестарта, выход офлайн, PKCE, разбор App Link, отсутствие токенов в `toString` | JUnit + MockWebServer |
| `core:auth` androidTest | настоящий Keystore: шифрование, удалённый ключ | эмулятор |
| `core:designsystem` | компоненты в обеих темах и с шрифтом 200 % | Robolectric + Roborazzi |
| `app` | ViewModel с фейками, экраны в compact и expanded, обе темы, list-detail в две панели | JUnit, Robolectric + Roborazzi |
| `app` androidTest | live smoke на Android 17: вход → `/bikes` → велосипед → `/me` → выход | эмулятор API 37, `smoke.yml` |

- `ci.yml` запускает `./gradlew verify` на каждый PR и push в `main`.
- `smoke.yml` поднимает эмулятор API 37 (`google_apis`, x86_64, KVM) и выполняет `connectedDebugAndroidTest`. Учётные данные приходят из secrets `COLABIKE_SMOKE_EMAIL` и `COLABIKE_SMOKE_PASSWORD`, без них live-тест пропускается с пометкой.

## Release и RuStore

- `release` собирается с R8 и сжатием ресурсов. Подпись в Git не входит: keystore и пароли хранит владелец. SHA-256 сертификата подписи нужен cola#324 для `assetlinks.json`, иначе App Link не пройдёт проверку.
- Публикация — позже в RuStore. Google Play, Play Billing, Play Integrity и Play-зависимые API не подключаются.
