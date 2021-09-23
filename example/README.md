# Recipe
You can catch capture event by simply writing these codes

final ScreenCaptureEvent screenListener = ScreenCaptureEvent();

```dart
@override
void initState() {
    screenListener.addScreenRecordListener((recorded) {
        ///Recorded was your record status (bool)
        setState(() {
            text = recorded ? "Start Recording" : "Stop Recording";
        });
    });

    screenListener.addScreenShotListener((filePath) {
        ///filePath only available for Android
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

You also can secure Android screen by prevent user to take screenshot or screen record, simply write this code


```dart
screenListener.preventAndroidScreenShot(true);
```

Take a look at docs and example code to get further information, open issue or contributing are very welcome 🙌

