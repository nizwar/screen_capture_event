import 'package:flutter/material.dart';

import 'package:screen_capture_event/screen_capture_event.dart';

void main() {
  runApp(const MaterialApp(
    home: ExampleScreenListener(),
  ));
}

class ExampleScreenListener extends StatefulWidget {
  const ExampleScreenListener({Key? key}) : super(key: key);

  @override
  _ExampleScreenListenerState createState() => _ExampleScreenListenerState();
}

class _ExampleScreenListenerState extends State<ExampleScreenListener> {
  final ScreenCaptureEvent screenListener = ScreenCaptureEvent();
  String text = "Do Screenshot or Screen Record";
  bool preventScreenshot = false;

  @override
  void initState() {
    screenListener.addScreenRecordListener((recorded) {
      setState(() {
        text = recorded ? "Start Recording" : "Stop Recording";
      });
    });
    screenListener.addScreenShotListener((filePath) {
      setState(() {
        text = "Screenshot stored on : $filePath";
      });
    });
    screenListener.watch();
    super.initState();
  }

  @override
  void dispose() {
    screenListener.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Example Screen Capture Event")),
      body: ListView(
        padding: const EdgeInsets.all(30),
        children: [
          Text(text, textAlign: TextAlign.center),
          const SizedBox(height: 10),

          ///Prevent Button
          TextButton(
            style: TextButton.styleFrom(
                backgroundColor: Theme.of(context).primaryColor),
            onPressed: () {
              setState(() {
                preventScreenshot = !preventScreenshot;
              });
              screenListener.preventAndroidScreenShot(preventScreenshot);
            },
            child: Text(
              preventScreenshot
                  ? "Allow Screenshot (ANDROID)"
                  : "Prevent Screenshot (ANDROID)",
              style: const TextStyle(color: Colors.white),
            ),
          ),

          ///Check Record Button
          TextButton(
            style: TextButton.styleFrom(
                backgroundColor: Theme.of(context).primaryColor),
            onPressed: () {
              screenListener.isRecording().then((value) {
                setState(() {
                  if (value) {
                    text = "You're Still Recording";
                  } else {
                    text = "You're not recording";
                  }
                });
              });
            },
            child: const Text(
              "Check Record Status",
              style: TextStyle(color: Colors.white),
            ),
          ),
        ],
      ),
    );
  }
}
