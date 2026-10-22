# Screen Capture Event
Catch screen capture (Screenshot & Screen Record) event for Android and iOS, Yes... Screen record is working for Android! 🙌

## Recipe
You can catch capture event by simply writing these codes

```dart
final ScreenCaptureEvent screenListener = ScreenCaptureEvent();


@override
void initState() {
    screenListener.addScreenRecordListener((recorded) {
        ///Recorded was your record status (bool)
        setState(() {
            text = recorded ? "Start Recording" : "Stop Recording";
        });
    });

    screenListener.addScreenShotListener((filePath) {
        ///filePath is empty when the path isn't known (see below)
        setState(() {
            text = "Screenshot stored on : $filePath";
        });
    });

    ///You can add multiple listener ^-^
    screenListener.addScreenRecordListener((recorded) {
        print("Hi i'm 2nd Screen Record listener");
    });
    screenListener.addScreenShotListener((filePath) {
        print("Wohooo i'm 2nd Screenshot listener");
    });

    ///Start watch
    screenListener.watch();
    super.initState();
}

@override
void dispose() {
    ///Don't forget to dispose it to detach all the observer :)
    screenListener.dispose();
    super.dispose();
}
```

Several screens can each have their own `ScreenCaptureEvent`: every instance between
`watch()` and `dispose()` gets the events, and disposing one doesn't stop the others.

You also can secure Android screen by prevent user to take screenshot or screen record, simply write this code

```dart
screenListener.preventAndroidScreenShot(true);
```

Your app's content is then blacked out of screenshots and recordings. Android 14 and later
may still report that a screenshot was taken.

## How it works

### Android

| | Screenshots | Screen recordings |
|---|---|---|
| **Android 15+** | System callback: screenshots of your app while it is on screen. No permission prompt. | System callback: `true` while your app is visible in a recording, on every device. No permission prompt. |
| **Android 14** | Same as Android 15+. | Best effort, see below. |
| **Android 13** | The screenshot folders are watched. Needs `READ_MEDIA_IMAGES`, which your app declares (see below). | Best effort, needs `READ_MEDIA_VIDEO`. |
| **Android 12 and older** | The screenshot folders are watched. Needs `READ_EXTERNAL_STORAGE`, declared by the plugin. | Best effort. |

`filePath` is the screenshot's file when the app can read the screenshot folders, and an
empty string otherwise (for example on Android 14+ without `READ_MEDIA_IMAGES`).

**Screen recordings before Android 15** can only be detected from the recording's file while
it is written: recorders that write into `DCIM/Screen recordings` (Samsung), `DCIM/ScreenRecorder`
(Xiaomi) or a screenshot folder (Huawei) are detected; the Android/Pixel recorder only saves its
file once it stops, so it can't be.

**Permissions.** The plugin declares `DETECT_SCREEN_CAPTURE` and `DETECT_SCREEN_RECORDING`
(Android 14/15, granted without a prompt) and `READ_EXTERNAL_STORAGE` up to Android 12. For
Android 13 support, or the screenshot's path on Android 13 and later, declare the media
permissions in your app's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<!-- Only for recordings on Android 13 and 14 -->
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
```

`ScreenCaptureEvent()` asks for them when they are declared (pass `false` to ask later with
`storagePermission()`). Google Play only allows these permissions for apps that need broad
access to photos and videos, so leave them out if detection alone is enough.

### iOS

Screenshots are reported after they are taken. iOS doesn't tell apps where screenshots are
saved, so `filePath` is `"ios_not_supported"`. Screen recording, mirroring and AirPlay all
count as recording (`UIScreen.isCaptured`). `preventAndroidScreenShot` has no effect on iOS.

The plugin supports Swift Package Manager (Flutter 3.44 or later) and CocoaPods.

Take a look at docs and example code to get further information, PRs are very welcome 🙌


## Donate
Love my work? It must be nice to enjoy a cup of coffee on this beautiful day ☕️ &nbsp;😉 

<a href="https://paypal.me/nizwar/"><img src="https://raw.githubusercontent.com/andreostrovsky/donate-with-paypal/master/blue.svg" height="40"></a>  
