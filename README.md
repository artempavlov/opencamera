# Open Camera FPS для OnePlus 12

Неофициальная сборка [Open Camera](https://sourceforge.net/p/opencamera/code/) для OnePlus 12 (PJD110). Основана на теге `v1.56.2`; версия обеих сборок — `1.56.2`, код версии — `99`.

На основной камере откройте **`2: Back Camera` → Видео → ⋮ → `Video mode`**. Сначала выберите частоту, затем разрешение. В меню есть 720p60, 1080p60 и 4K60. Если драйвер сообщает соответствующие режимы, можно также выбрать **24 fps → 8K** или **480 fps → 2048×1152**.

Приложение завершает сохранение уже снятого RAW при выходе на главный экран. В обычном фоторежиме очередь на PJD110 вмещает до 30 пар DNG + JPEG/PNG. При нехватке RAM затвор может приостановиться раньше. Это размер очереди, а не скорость серийной съёмки.

### Что зависит от драйвера

На прошивке `PJD110_16.0.2.400(CN01)` для сторонних приложений 8K24, 2048×1152 при 480 кадр/с и 50-Мп RAW с ISO 6400 проверялись с отдельным модулем Magisk **OnePlus 12 Camera v5**. Сам APK эти режимы в драйвер не добавляет. Доработки сохранения файлов находятся в приложении.

### Сборка

Нужны JDK 21 и Android SDK с платформой 36. Укажите SDK через `ANDROID_HOME` или `local.properties`. Из корня репозитория:

```sh
./gradlew :app:assembleDebug
```

Результат — `app/build/outputs/apk/debug/app-debug.apk`, пакет `net.sourceforge.opencamera.pjd110`.

Для **Open Camera FPS 2** создайте отдельную рабочую копию, чтобы не менять исходники первой. Команда `git worktree add` ниже предполагает, что соседний каталог `opencamera-fps-2` свободен:

```sh
git worktree add --detach ../opencamera-fps-2 HEAD
python3 tools/prepare-second.py ../opencamera-fps-2
cd ../opencamera-fps-2
./gradlew :app:assembleDebug
```

У второй копии свой пакет `net.sourceforge.opencamera.pjd110.second`, свои настройки и каталог по умолчанию `DCIM/OpenCameraFPS2`. Для обновления уже установленного APK Android требует подпись тем же ключом.

Лицензия Open Camera: [GPLv3](gpl-3.0.txt). Уведомления и лицензии сторонних компонентов сохранены из upstream.
