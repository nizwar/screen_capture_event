import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class ScreenCaptureEvent {
  final List<Function(bool recorded)> _screenRecordListener = [];
  final List<Function(String filePath)> _screenshotListener = [];

  static const MethodChannel _channel = MethodChannel('screencapture_method');

  ScreenCaptureEvent([bool requestPermission = true]) {
    if (requestPermission && Platform.isAndroid) storagePermission();
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case "screenshot":
          for (var callback in _screenshotListener) {
            callback.call(call.arguments);
          }
          break;
        case "screenrecord":
          for (var callback in _screenRecordListener) {
            callback.call(call.arguments);
          }
          break;
        default:
      }
    });
  }

  ///Request storage permission for Android usage
  Future<void> storagePermission() => Permission.storage.request();

  ///It will prevent user to screenshot/screenrecord on Android by set window Flag to WindowManager.LayoutParams.FLAG_SECURE
  Future<void> preventAndroidScreenShot(bool value) {
    return _channel.invokeMethod("prevent_screenshot", value);
  }

  ///Listen when user screenrecord the screen
  ///You can add listener multiple time, and every listener will be executed
  void addScreenRecordListener(Function(bool recorded) callback) {
    _screenRecordListener.add(callback);
  }

  ///Listen when user screenshot the screen
  ///You can add listener multiple time, and every listener will be executed
  ///Note : filePath only work for android
  void addScreenShotListener(Function(String filePath) callback) {
    _screenshotListener.add(callback);
  }

  ///Start watching capture behavior
  void watch() {
    _channel.invokeMethod("watch");
  }

  ///Dispose all listener on native side
  void dispose() {
    _channel.invokeMethod("dispose");
  }

  ///You can get record status to check if screenrecord still active
  Future<bool> isRecording() {
    return _channel.invokeMethod("isRecording").then((value) => value ?? false);
  }
}
