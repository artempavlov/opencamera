# Open Camera for OnePlus 12

An unofficial build of [Open Camera](https://sourceforge.net/p/opencamera/code/) for the OnePlus 12 (PJD110), based on version `1.56.2`.

Select **`2: Back Camera` → Video → ⋮ → Video mode** to choose a frame rate and resolution. The menu supports 720p, 1080p, and 4K at 60 frames per second. When the camera driver exposes the modes, it also offers 8K at 24 frames per second and 2048 × 1152 at 480 frames per second.

The app finishes saving a captured RAW image when you go to the home screen. On the PJD110, standard photo mode can queue up to 30 DNG + JPEG/PNG pairs. The shutter can pause sooner if memory runs low. This is the queue capacity, not a burst rate.

## Camera driver requirements

On firmware `PJD110_16.0.2.400(CN01)`, 8K at 24 frames per second, 2048 × 1152 at 480 frames per second, and 50-megapixel RAW at ISO 6400 were verified with the separate **OnePlus 12 Camera v5** Magisk module. The APK does not add these modes to the camera driver. The file-saving fixes do not require the module.

## Build

Use JDK 21 and Android SDK Platform 36. Set `ANDROID_HOME` or configure the SDK in `local.properties`, then run:

```sh
./gradlew :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. The default application ID is `net.sourceforge.opencamera.pjd110`.

To install a second copy alongside the default one, build the same source with a different application ID:

```sh
./gradlew :app:assembleDebug -PcameraApplicationId=com.example.mycamera
```

Use `cameraAppName` to change the launcher label and `cameraSaveFolder` to change the default save folder. For example:

```sh
./gradlew :app:assembleDebug \
  -PcameraApplicationId=net.sourceforge.opencamera.pjd110.second \
  '-PcameraAppName=Open Camera 2' \
  -PcameraSaveFolder=OpenCamera2
```

Different application IDs keep app settings separate. Unless you set `cameraSaveFolder`, both copies default to `DCIM/OpenCamera`. Copy the APK before building another variant because the output path is the same. To update an existing installation, keep its application ID and sign the APK with the same key.

## License

[GPLv3](gpl-3.0.txt). Upstream notices and third-party licenses are included.
