## 1.3.0
* Android 14 and later report screenshots through the system's screen capture callback, without a permission prompt ([#20](https://github.com/nizwar/screen_capture_event/issues/20))
* Android 15 and later report screen recordings through the system's screen recording callback, on every device ([#3](https://github.com/nizwar/screen_capture_event/issues/3), [#12](https://github.com/nizwar/screen_capture_event/issues/12))
* Android: one event per screenshot, with the final file's path instead of a temporary `.pending-...` one; no more repeated events ([#34](https://github.com/nizwar/screen_capture_event/issues/34)); screenshot folders that don't exist yet are watched as soon as they are created, so the first screenshot on a device is caught too
* Android: no more ANRs: the plugin no longer scans folders, and everything else runs off the main thread ([#28](https://github.com/nizwar/screen_capture_event/issues/28), building on [#37](https://github.com/nizwar/screen_capture_event/pull/37), thanks to @liemvu)
* Android: screen recording detection before Android 15 no longer flips between `true` and `false` ([#32](https://github.com/nizwar/screen_capture_event/issues/32)); all folders are watched again on Android 9 and older ([#11](https://github.com/nizwar/screen_capture_event/issues/11))
* Android: `storagePermission()` asks for `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` on Android 13+ when the app declares them ([#37](https://github.com/nizwar/screen_capture_event/pull/37)); the plugin no longer declares `WRITE_EXTERNAL_STORAGE`
* iOS: every method call completes (`preventAndroidScreenShot` and `storagePermission` never did), notifications are observed once, and each Flutter engine gets its own channel ([#16](https://github.com/nizwar/screen_capture_event/issues/16)); on Android every call completes too ([#37](https://github.com/nizwar/screen_capture_event/pull/37))
* Several `ScreenCaptureEvent` instances can watch at the same time; disposing one no longer stops the others
* iOS: Swift Package Manager support ([#36](https://github.com/nizwar/screen_capture_event/issues/36), [#38](https://github.com/nizwar/screen_capture_event/pull/38), thanks to @charafau), minimum iOS 12
* Example rebuilt for current Flutter versions, with an integration test

## 1.2.0
* Upgrade AGP 8.0, Thanks to @ibrahimmohamed2411
* Samsung Screen Record Fixes, thanks to @acike
* Fix exceptions [#25](https://github.com/nizwar/screen_capture_event/issues/25), thanks to @kimiaki704 

## 1.1.1
* Fix Exception when addScreenShotListener (Android) Thanks to @juarezfranco
## 1.1.0
* Use pemission handler without 3rd party plugin
* Fix more bugs

## 1.0.3

* Merge [#7](https://github.com/nizwar/screen_capture_event/pull/7) fixes
## 1.0.2

* Update deps

## 1.0.1

* Update deps

## 1.0.0+1

* Update license

## 1.0.0

First release

* Catch Android & iOS Screen Record Listener
* Catch Android & iOS Screenshot Listener
* Prevent Screenshot for Android
* Check if recording on Android & iOS
