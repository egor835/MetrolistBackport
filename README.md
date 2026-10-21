# Metrolist Backport

Личный форк [Metrolist](https://github.com/MetrolistGroup/Metrolist), клиента YouTube Music, который работает на **Android 5.0+ (API 21)**. Оригинал требует Android 8.

Проверено на двух телефонах с Android 5.1.1 (armeabi-v7a): Sharp NP505SH (кнопочный, с системным WebView на Chromium 39) и Sony Xperia Z C6603. Работают главная, поиск, воспроизведение, тексты песен, медиа-уведомление и вход в аккаунт Google.

## Что изменено относительно оригинала

### Понижение библиотек вместо `overrideLibrary`

Новые AndroidX-библиотеки требуют minSdk 23 и выше. Если просто заглушить проверку через `tools:overrideLibrary`, приложение собирается, но падает: в Compose, Media3 и других библиотеках сотни вызовов API 22–23 без проверки версии. Поэтому все зависимости откачены до последних версий с поддержкой minSdk 21:

| Библиотека | Версия |
|---|---|
| Compose | 1.9.5 |
| Material3 | 1.5.0-alpha01 (последняя с expressive-компонентами и minSdk 21) |
| Media3 | 1.8.1 |
| Lifecycle | 2.9.4 |
| Activity | 1.11.0 |
| Navigation | 2.9.8 |
| Room | 2.7.2 |
| DataStore | 1.1.7 |
| Coil | 3.4.0 |
| AppCompat | 1.7.1 |
| Browser | 1.9.0 |
| Cast framework | 22.1.0 |
| Hilt Navigation | 1.2.0 |
| reorderable | 3.0.0 |
| material-kolor | 4.0.5 |

Код приложения переписан под старые API этих версий. Новые API Compose/Android заменены бэкпортами: `ui/utils/WindowInfoUtils.kt`, `ui/utils/LocaleUtils.kt`, `ContextCompat`/`ServiceCompat`/`AlarmManagerCompat`, проверки `SDK_INT`. Lint `NewApi` при minSdk 21 чистый.

`overrideLibrary` остался только для `com.metrolist.innertubex`. Сканирование её байткода показало, что вызовов API выше 21 там нет.

### Пересобранный QuickJS

Опубликованный `quickjs-kt-android` 1.0.15 собран под API 23 и импортирует `__register_atfork`, которого нет в libc Android 5, поэтому на 5.x библиотека не загружается. В `app/libs/quickjs-kt-android-1.0.15-api21.aar` лежит та же версия, пересобранная под API 21: Java/Kotlin-классы оригинальные, заменены только `.so`. Оригинальный артефакт исключён в `app/build.gradle.kts`.

### Свой движок WebView на Android 5.x

На Android 5 системный WebView зашит в прошивку и часто не обновляется, а страница входа Google на старом Chromium показывает «Something went wrong». `utils/CustomWebView.kt` позволяет подсунуть приложению WebView из APK-файла. Он работает только внутри Metrolist, на систему не влияет.

Как это работает: при старте приложения, до создания первого WebView, загрузчик создаёт ClassLoader для выбранного APK и сам ставит `WebViewFactory.sProviderInstance`, повторяя то, что делает фреймворк на API 21/22. Файлы хранятся в `files/webview/` приложения. Если APK не загрузится, приложение молча работает на системном WebView.

Кроме входа, новый движок чинит генерацию PoToken (YouTube), которая на старом WebView отключается.

### Удалено

- **Интеграция с Discord (Rich Presence)**. Строки в `metrolist_strings.xml` и переводах остались, они ни на что не влияют.

## Как пользоваться

1. Поставь APK (см. «Сборка»).
2. **Только Android 5.x, если вход в Google не работает:**
   1. Скачай WebView на Chromium **95 или старше**: 95 — последняя версия с поддержкой Android 5. Проверенный вариант — [Bromite SystemWebView 95.0.4638.79](https://github.com/bromite/bromite/releases/tag/95.0.4638.79). Выбери файл под свою архитектуру:
      - `arm_SystemWebView.apk` для 32-битного ARM;
      - `arm64_SystemWebView.apk` для 64-битного;
      - x86/x64 для эмуляторов.
   2. Положи APK на телефон, например в `Download`.
   3. В Metrolist нажми на иконку аккаунта справа вверху, затем «WebView engine» → «Choose APK» и выбери файл.
   4. Подготовка занимает до минуты на старом железе: копирование, распаковка библиотек, компиляция dex. Потом приложение перезапустится, а в пункте будет написано «Custom, version …».
   5. Войди через «Login» как обычно.
   6. Вернуть системный WebView можно там же, кнопкой «Use system WebView».

   > Bromite заброшен с 2023 года, а Chromium 95 вышел в 2021-м и содержит известные уязвимости. Внутри Metrolist он открывает только страницы Google и YouTube. Этот же APK не стоит использовать как браузер.

   Если вход и через новый движок не работает, есть ручной вариант. Войди в Metrolist на другом устройстве, скопируй токен (окно аккаунта → «Tap to show token») и вставь его через «Log in with token».

### Ограничения на Android 5

- Не работает автопродолжение воспроизведения при подключении Bluetooth-устройства: нужного API нет до Android 6.
- В уведомлении о загрузке нет кнопки отмены до Android 7.
- На медленных телефонах предзагрузка расшифровщика подписи YouTube может не уложиться в 30 секунд. Лимит зашит в innertubex, в логе это выглядит как `EJS solve failed ... QuickJsInterruptedException`. Сейчас это ни на что не влияет: потоки отдаёт клиент, которому подпись не нужна.
- Встроенный апдейтер (варианты `foss` и `gms`) смотрит релизы **оригинального** Metrolist. Они требуют Android 8 и на этот форк не встанут, так что его предложения обновиться надо игнорировать. В варианте `izzy` апдейтера нет.
- Интерфейс местами подтормаживает на железе 2012–2013 годов.

## Сборка

Нужны JDK 21 (подойдёт JBR из Android Studio) и Android SDK. NDK не нужен: пересобранный QuickJS уже лежит в репозитории.

```bash
./gradlew :app:assembleFossDebug   # без Google Cast, с апдейтером
./gradlew :app:assembleGmsDebug    # с Google Cast
./gradlew :app:assembleIzzyDebug   # без Cast и без апдейтера
```

APK появится в `app/build/outputs/apk/<вариант>/debug/app-<вариант>-debug.apk`. Он универсальный: все ABI в одном файле, около 50 МБ.

Нюансы:

- **Debug-сборка ставится рядом** с обычной: пакет `com.metrolist.music.debug`, название «Metrolist Debug». Подписывается `app/persistent-debug.keystore`, если такой файл есть, иначе `~/.android/debug.keystore`. Ключ должен быть один и тот же от сборки к сборке, иначе обновление поверх не встанет.
- **Release** (`assembleFossRelease`) собирается с R8, но подписи в конфиге нет: ключ берётся из `keystore/release.keystore` и переменных `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Для личного использования проще ставить debug.
- **Пересборка QuickJS** нужна, только если поднимаешь версию `quickjs-kt` (её тянет innertubex). Понадобятся NDK r28c и CMake:
  ```bash
  ANDROID_NDK_HOME=/path/to/ndk CMAKE=/path/to/cmake scripts/build-quickjs-api21.sh
  ```
  Скрипт скачивает исходники нужного тега и оригинальный AAR, пересобирает `libquickjs.so` под API 21 для всех ABI и кладёт результат в `app/libs/`. Затем поправь имя файла в `app/build.gradle.kts`. В `.gitignore` для этого AAR есть исключение, потому что `*.aar` игнорируется глобально.
- **Юнит-тесты:** `./gradlew :app:testFossDebugUnitTest`. Тест `PodcastEnrichmentTest` в `innertube` иногда падает из-за сети; при повторном запуске проходит.

### Если подтягиваешь изменения из оригинала

- Не поднимай версии из таблицы выше. Renovate/апстрим будут предлагать новые, а они требуют minSdk 23+. Если апстрим начал использовать новое API, его надо портировать вниз.
- После слияния проверь lint: `./gradlew :app:lintFossDebug` и найди `NewApi` в `app/build/reports/lint-results-fossDebug.html`. Должно быть 0.
- Код Discord будет возвращаться конфликтами, в основном в `MusicService`. Его надо удалять.
- Проверь в манифесте, что новые библиотеки не требуют minSdk выше 21. Сборка на это ругнётся сама, и правильный ответ — откатить библиотеку, а не добавлять её в `overrideLibrary`.

## Лицензия

GPL-3.0, как и оригинал. Оригинальный проект: [MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist).
