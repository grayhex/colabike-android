# Приёмка UI/UX #61

Этот документ описывает артефакты и протокол приёмки. Точный commit, результаты CI и ссылка
на APK фиксируются в описании финального PR, связанного с [#61](https://github.com/grayhex/colabike-android/issues/61).
Видео содержит `source-commit.txt` и `ci-run.txt`; имя артефакта APK также содержит проверенный SHA.
Это не заявление о пользовательской конверсии или плавности физического устройства.

## Одинаковые данные до и после

Baseline — `ddeb05fc0fd98878c7cfe6968a93658ade72d109` (PR #63): уже исправленные общие
компоненты, но прежняя композиция содержательных экранов. Он выбран потому, что содержит
тот же лицензированный набор фотографий, синтетические GPX и вымышленных людей.
Это **не** исходный вид приложения до всех изменений #61. Полный исходный аудит относится
к `999c6ff018094469c4f397c9ccb83cfa8567552e` и другим fixtures; напрямую сравнивать эти фото нельзя.

| Экран | До (светлая тема) | После (светлая) | После (тёмная) |
| --- | --- | --- | --- |
| Лента | [PNG](https://github.com/grayhex/colabike-android/blob/ddeb05fc0fd98878c7cfe6968a93658ade72d109/app/src/test/screenshots/realistic_feed_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_feed_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_feed_phone_dark.png) |
| Каталог | [PNG](https://github.com/grayhex/colabike-android/blob/ddeb05fc0fd98878c7cfe6968a93658ade72d109/app/src/test/screenshots/realistic_catalog_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_catalog_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_catalog_phone_dark.png) |
| Велосипед | [PNG](https://github.com/grayhex/colabike-android/blob/ddeb05fc0fd98878c7cfe6968a93658ade72d109/app/src/test/screenshots/realistic_bike_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_bike_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_bike_phone_dark.png) |
| Поездка | [PNG](https://github.com/grayhex/colabike-android/blob/ddeb05fc0fd98878c7cfe6968a93658ade72d109/app/src/test/screenshots/realistic_ride_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_ride_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_ride_phone_dark.png) |
| Компания | [PNG](https://github.com/grayhex/colabike-android/blob/ddeb05fc0fd98878c7cfe6968a93658ade72d109/app/src/test/screenshots/realistic_together_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_together_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_together_phone_dark.png) |
| Профиль | [PNG](https://github.com/grayhex/colabike-android/blob/ddeb05fc0fd98878c7cfe6968a93658ade72d109/app/src/test/screenshots/realistic_profile_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_profile_phone_light.png) | [PNG](../../../app/src/test/screenshots/realistic_profile_phone_dark.png) |

Набор включает версии 200% и 1200 dp. Галерея проверяет настоящее Android Dialog-окно
при fontScale 2.0. `adaptive_*` фиксирует 600/840/1200 dp в обеих темах до и после выбора
велосипеда. Фотографии в приложении отображаются целиком; данные не изменяются для композиции.
Лицензии и авторство сохраняются в [реестре фотографий](../../../app/src/test/resources/visual/photos/sources.json).
Карты на Roborazzi-снимках — схемы. Native-подложку смотрят в device-видео, а не оценивают по этим PNG.

## Воспроизводимые device-сценарии

`VisualAcceptanceTest` запускается на Android, использует локальные репозитории и лицензированные
фотографии; интерактивная карта и превью — настоящие MapLibre. Ни вход, ни производство не
подменяются зелёным fake-smoke: отдельный `LiveSmokeTest` сохраняет собственную проверку.

`scripts/record-visual-flows.sh` записывает три сценария, каждый при normal/reduced motion:

1. Лента → поездка → карта → фон/возврат → весь маршрут → назад → компания → написать.
2. Велосипед → фото → увеличить/сбросить → второе фото → закрыть на том же фото → профиль.
3. Добавить велосипед → поиск модели/года → проверить комплектацию → категория → сохранить.

Обе записи используют светлую тему, одни данные и одно устройство. Скрипт проверяет результат
каждого инструментального теста, сохраняет fingerprint/размер/плотность, лицензии фотографий,
восстанавливает системные шкалы анимации и публикует MP4 в отдельном CI-артефакте. Тест потери
сети отдельно выключает соединение, проверяет отсутствие активной сети, возвращает карту из фона
и повторно открывает маршрут; в `finally` восстанавливает соединение.

**Это debug-демонстрация на эмуляторе, не измерение jank.** Локальная среда без KVM используется
для компиляции, unit/Roborazzi/lint; нативные проверки выполняются в CI на API 31/36/37.

## Приёмка на физическом устройстве

Эти результаты не получены в облачной среде. Их нельзя заменить зелёным CI или записью эмулятора.

- На одном устройстве среднего класса записать модель, Android, refresh rate, размер/fontScale,
  состояние энергосбережения, версии baseline/final. Прогреть данные одинаковым проходом.
- Использовать profileable/release, без debugger. Снять Perfetto FrameTimeline для прокрутки ленты,
  списка → detail → назад, галереи, раскрытия комплектации/обсуждения. Повторить по 5 раз.
  Сохранить traces и фактическое число total/janky frames; доля = janky / total × 100%.
  Цель <5% согласовывалась как предварительная; отсутствие ухудшения проверять против baseline
  на том же устройстве, а не против эмулятора. Не фильтровать медленные проходы из отчёта.
- С TalkBack пройти те же сценарии, проверить порядок фокуса, подписи карты/реакций, точку графика
  через кнопки, возврат из галереи, ошибки/повтор, IME и системные панели. Проверить внешний
  keyboard/D-pad на rail и при смене ширины. При 200% не требовать жеста pinch для функций.
- Пять велосипедистов самостоятельно выполняют поиск поездки, связь с автором и добавление
  велосипеда. Записать время, ошибки, подсказки и комментарии каждого. Предварительная цель:
  минимум 4 из 5 заканчивают каждый сценарий без помощи. Никакие синтетические результаты
  не заполняют эту таблицу.

Задача #61 остаётся открытой до живой приёмки владельцем. Внедрение и автоматические результаты
фиксируются отдельными PR, их merge не означает прохождение ещё не проведённого исследования.
