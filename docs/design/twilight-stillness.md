> **Заменено 06.10.2026.** Действующее направление — [Графит и лайм](graphite-lime.md) ([ADR 0023](../adr/0023-graphite-lime-redesign.md)). Этот документ сохранён как история решения и больше не эталон.

# Twilight Stillness в ColaBike

Визуальное направление приложения (issue [#3](https://github.com/grayhex/colabike-android/issues/3)). Этот документ — источник, соответствие референса токенам `core:designsystem` и список решений, которые владелец смотрит на ревью. Правила для разработки (компоненты, состояния, доступность) — в [DESIGN.md](../../DESIGN.md).

## Источник

| Что | Где |
| --- | --- |
| Стиль | [Sleek — Twilight Stillness](https://sleek.design/design-md/twilight-stillness-Q3S0v0bt7yM) («Twilight Stillness Mobile», version 1) |
| DESIGN.md стиля | [`reference/twilight-stillness.DESIGN.md`](reference/twilight-stillness.DESIGN.md), текст без изменений (добавлен только перевод строки в конце). SHA-256 `812bce27c37106245972ae916572a43bd5fd71c1ce24f8b78edd580655f10ed5` |
| Экраны референса | два экрана страницы Sleek (Explore и Home) просмотрены 03.10.2026; в репозиторий не копируются (сторонние сгенерированные материалы), смотрите по ссылке |
| Получен | 03.10.2026 из страницы Sleek: DESIGN.md встроен в её HTML, из него и извлечён |

Вложение `DESIGN.md` из комментария владельца в issue #3 агенту открыть не удалось (`github.com/user-attachments` недоступен из окружения агента), поэтому сверка шла с экспортом страницы. Если вложение отличается от `reference/` (например, правки владельца), расхождения нужно внести в `reference/` и в таблицы ниже.

Старый незавершённый Figma и прежние визуальные правила больше не источник истины. Палитра и шрифты взяты из прочитанного DESIGN.md, не из названия стиля.

## Что в референсе и что мы с ним сделали

Референс односторонний: только тёмная тема, вёрстка на CSS/Tailwind, иконки Phosphor, шрифты Lora и Outfit. Нативному Android-приложению это переносится не один в один.

| Тема | Референс | В ColaBike | Почему |
| --- | --- | --- | --- |
| Тёмная палитра | warm charcoal `#151413`, парчмент `#EAE6DB`, песок `#C9BBA5`, шалфей `#8C9A8D` | **без изменений** | это и есть стиль |
| Светлая палитра | нет | **наша адаптация**: тёплая бумага `#F6F2EA`, чернила `#1F1E1D`, умбра `#5F5039` вместо песка | второй темы в оригинале нет; не выдаём её за часть оригинала, смотрите на ревью |
| Шрифт заголовков | Lora 400 | Lora 400 (OFL), вариативный TTF в `res/font` | есть кириллица (проверено) |
| Шрифт текста | Outfit 300 | **Source Sans 3**, Light 300 для текста от 14 sp, Regular для мелкого | у Outfit нет кириллицы: 0 из 64 русских букв (проверено по файлу). Source Sans 3 уже в проекте, с кириллицей |
| Жирный шрифт | запрещён | запрещён: самый тяжёлый вес — Medium (только `labelSmall`) | как в референсе |
| Иконки | Phosphor thin / fill | **Material Symbols Rounded, вес 300**: контур в покое, заливка при выборе | issue требует Material Symbols; вес 300 ближе к тонкой линии, чем стандартные 400 |
| Фон карточек | `surface/40` + `backdrop-blur` | непрозрачная `surfaceContainer` + hairline 1 dp | в Compose нет размытия того, что лежит под карточкой; hairline сохраняет вид |
| Ауры | два размытых пятна 5 % | `ColaCanvas`: два радиальных градиента 6 % (песок сверху справа, шалфей снизу слева) | дёшево и одинаково на всех устройствах |
| Углы | 16 / 22 / 24 / 32 px + CSS `squircle` | 16 / 20 / 24 / 32 dp, обычное скругление | суперэллипса в stable Compose нет; нигде меньше 16, как в референсе |
| Таб-бар | плавающая пилюля со свечением активной иконки | `ColaNavigationBar` на compact, `ColaNavigationRail` от 600 dp | на широких экранах пилюля внизу не работает |
| Подписи вкладок | только иконки | подпись у всех при ≤ 3 разделах, при 4–5 у выбранного; у остальных подпись для TalkBack | пять русских подписей на 360 dp не помещаются читаемо |
| Eyebrow | 10 px, `tracking 0.18em`, капсом | 11 sp, 1,8 sp, капсом в коде | 11 sp — нижняя граница читаемого |
| Поля ввода | граница-hairline | граница `outline`, контраст ≥ 3:1 к холсту | WCAG 1.4.11 для границ полей |
| Отступы | gutter 24, секции 40, карточка 24 | 20 / 32 / 20 dp | на 360 dp gutter 24 съедает седьмую часть ширины |
| Тени | только у таб-бара | только у плавающей панели | как в референсе |
| Цвет бренда | нет | жёлтый `#F3B51B` только в `BrandMark` (вход, рейка), лаунчере и сплэше | бренд ColaBike сохраняется, не смешиваясь с «одной тёплой точкой» песка |

Из-за пункта про бренд у `tertiary` значение жёлтого, а песок — это `primary` (тёмная тема) и умбра (светлая).

## Соответствие токенам

### Цвета

Тёмная — референс, светлая — адаптация. Роль Material 3 → токен `core:designsystem`.

| Роль референса | Тёмная | Светлая | Роль Material 3 |
| --- | --- | --- | --- |
| background | `#151413` | `#F6F2EA` | `background`, `surface`, `surfaceContainerLowest` (тёмная) |
| surface (карточки) | `#1F1E1D` | `#FBF9F4` | `surfaceContainer` |
| surface-alt | `#2A2927` | `#ECE6DA` | `surfaceContainerHigh`, `surfaceVariant` |
| border | `#2A2927` | `#DDD6C8` | `outlineVariant`: hairline карточек и чипов |
| on-surface | `#EAE6DB` | `#1F1E1D` | `onSurface`, `onBackground` |
| on-surface-variant | `#9A9893` | `#605C55` | `onSurfaceVariant` |
| primary | `#C9BBA5` | `#5F5039` | `primary` (`onPrimary`: `#151413` / `#F6F2EA`) |
| accent | `#8C9A8D` | `#566557` | `secondary` |
| destructive | `#7F1D1D` | `#8E2B24` | `ColaTheme.colors.destructive`; `errorContainer` (тёмная) |
| (примерка «ореола» 20 %) | `#393530` / `#2D2F2B` | `#E5DBC6` / `#DCE3DA` | `primaryContainer` / `secondaryContainer` |
| (текст и иконки ошибки) | `#E5958B` | `#A33B32` | `error`: oxblood на тёмном нечитаем, поэтому светлее |
| (граница поля) | `#6B6964` | `#8C877C` | `outline` |

Правило референса: песок и шалфей — точечные цвета, не заливки. Исключение — выбранный чип и главная кнопка.

Контраст проверен численно (WCAG 2.x), не на глаз:

| Пара | Тёмная | Светлая |
| --- | --- | --- |
| `onSurface` на фоне | 14,75 | 14,91 |
| `onSurfaceVariant` на фоне / на карточке | 6,38 / 5,78 | 5,95 / 6,32 |
| `onPrimary` на `primary` (кнопка, выбранный чип) | 9,76 | 6,98 |
| `primary` на фоне (иконки, фокус) | 9,76 | 6,98 |
| `primary` на `primaryContainer` | 6,45 | 5,67 |
| `error` на фоне | 7,89 | 5,82 |
| граница поля к холсту (нужно ≥ 3) | 3,36 | 3,20 |

### Типографика

Размеры в `sp`, масштабируются системным шрифтом.

| Токен референса | Material 3 | Шрифт, размер / интерлиньяж |
| --- | --- | --- |
| display-md | `displayMedium` | Lora 30 / 36 |
| headline-lg (заголовок экрана) | `headlineLarge` | Lora 24 / 30 |
| headline-md | `headlineMedium`, `titleLarge` | Lora 20 / 26 |
| headline-sm | `headlineSmall` | Lora 18 / 24 |
| numeral-stat | `ColaTheme.textStyles.numeral` | Lora 30 / 34 |
| body-md | `bodyMedium` (`bodyLarge` для текста чтения: 16 / 24) | Source Sans 3 Light 14 / 22 |
| body-sm | `bodySmall` | Source Sans 3 Regular 13 / 20 |
| label-md | `labelMedium`, `labelLarge` (кнопки: 14 / 20) | Source Sans 3 Regular 13 / 18 |
| label-sm | `labelSmall` | Source Sans 3 Medium 12 / 16 |
| label-uppercase | `ColaTheme.textStyles.eyebrow` | Source Sans 3 Regular 11 / 16, трекинг 1,8 sp, капсом в коде |

### Формы и отступы

| Референс | Токен | Где |
| --- | --- | --- |
| `sm` 16 | `shapes.small`, `extraSmall` | миниатюры, скелетоны |
| `md` 22 | `shapes.medium` (20) | строки списка, баннеры |
| `lg` 24 | `shapes.large` | карточки, поля |
| `xl` 32 | `shapes.extraLarge` | рамка фото на карточке велосипеда |
| `full` | `PillShape` | кнопки, чипы, бейджи, панель навигации, аватар |
| gutter 24 | `Spacing.screen` 20 | поля экрана |
| section-gap 40 | `Spacing.section` 32 | между секциями экрана |
| card-padding 24 | `Spacing.card` 20 | внутри карточки и плитки |
| (нижняя граница касания) | `Spacing.touch` 48 | высота кнопок и строк |

### Компоненты

| Референс | Компонент |
| --- | --- |
| card, list-row | `ColaCard`, `UserRow`, `RideCard` |
| hero-карточка с фото | `BikeCard` (`BikePhoto`) |
| button-primary / secondary | `Button` / `OutlinedButton` Material 3 с темой |
| chip / chip-active | `ColaFilterChip` |
| badge-pill | `PillBadge` |
| soft icon tile | `SoftIconTile` (пустые состояния) |
| tinted icon halo | `IconHalo` |
| stat tile | `StatTile` |
| top-app-bar | `ColaTopBar` |
| tab-bar | `ColaNavigationBar` (compact), `ColaNavigationRail` (от 600 dp) |
| input-text | `OutlinedTextField` с `colaTextFieldColors()` и `colaFieldShape` |
| avatar | `Avatar` |
| ауры | `ColaCanvas` |

## На ревью у владельца

1. **Светлая тема целиком** (`LightColors`, таблицы выше): её нет в оригинале. Сравнение PNG: `app/src/test/screenshots/*_light.png` против `*_dark.png`.
2. **Жёлтый бренд** оставлен только в знаке, а песок — главный «точечный» цвет. Если жёлтый должен быть заметнее, меняется `tertiary` и `BrandMark`.
3. **Пара шрифтов Lora + Source Sans 3** вместо Lora + Outfit (кириллица).
4. **Подписи вкладок** при 4–5 разделах показываются только у выбранного.

## Чего здесь нет

- Вкладки «Лента», «Покатушки», «Сообщения» заведены в `TopLevel`, но не показываются, пока нет их срезов (#6, #7, #8). Поиск и уведомления — действия верхней панели и тоже появятся со своими срезами.
- Живой эмулятор и TalkBack на устройстве в этой задаче не проверялись. Доступность проверена семантикой в тестах и 200 % шрифтом на скриншотах.
