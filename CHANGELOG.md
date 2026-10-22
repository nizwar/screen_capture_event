## 1.2.1
* Fix Android 16 main-thread deadlock/ANR: all filesystem scanning and FileObserver
  setup now run on a background ExecutorService instead of the main thread.
* Request `READ_MEDIA_IMAGES` on Android 13+ (TIRAMISU), falling back to
  `READ_EXTERNAL_STORAGE` on older versions.
* Every MethodChannel call now returns a result so Dart futures never hang.
* `getLastModified()` guards directory access, filters to readable files, caps the
  scan at 200 files with a partial mtime sort, and handles `SecurityException`.
* Bump SDK constraint to Dart 3 (`>=3.0.0 <4.0.0`).

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
