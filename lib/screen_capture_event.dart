import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class ScreenCaptureEvent {
  final List<Function(bool recorded)> _screenRecordListener = [];
  final List<Function(String filePath)> _screenshotListener = [];

  static const MethodChannel _channel = MethodChannel('screencapture_method');

  /// The instances between [watch] and [dispose]. The native side watches while
  /// there is at least one, and its events go to all of them.
  static final Set<ScreenCaptureEvent> _watching = {};
  static bool _handlerSet = false;

  ScreenCaptureEvent([bool requestPermission = true]) {
    if (requestPermission &&
        !kIsWeb &&
        defaultTargetPlatform == TargetPlatform.android) {
      storagePermission();
    }
    if (!_handlerSet) {
      _handlerSet = true;
      _channel.setMethodCallHandler(_onNativeEvent);
    }
  }

  static Future<void> _onNativeEvent(MethodCall call) async {
    for (final event in _watching.toList()) {
      switch (call.method) {
        case "screenshot":
          for (final callback in event._screenshotListener.toList()) {
            callback.call(call.arguments as String);
          }
          break;
        case "screenrecord":
          for (final callback in event._screenRecordListener.toList()) {
            callback.call(call.arguments as bool);
          }
          break;
        default:
      }
    }
  }

  ///Request permission to read the screenshot and screen recording folders (Android)
  ///
  ///Only asks for the permissions your app declares: READ_EXTERNAL_STORAGE up to
  ///Android 12, READ_MEDIA_IMAGES (and READ_MEDIA_VIDEO) on Android 13 and later.
  ///See the README for when you need them.
  Future<void> storagePermission() {
    return _channel.invokeMethod("request_permission");
  }

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
  ///
  ///On Android, [filePath] is the screenshot's file when the app can read the
  ///screenshot folders, and an empty string otherwise. On iOS, which doesn't
  ///tell apps where screenshots go, it is "ios_not_supported".
  void addScreenShotListener(Function(String filePath) callback) {
    _screenshotListener.add(callback);
  }

  ///Start watching capture behavior
  void watch() {
    if (_watching.add(this) && _watching.length == 1) {
      _channel.invokeMethod("watch");
    }
  }

  ///Stop watching. The native side keeps watching while other instances do.
  void dispose() {
    if (_watching.remove(this) && _watching.isEmpty) {
      _channel.invokeMethod("dispose");
    }
  }

  ///You can get record status to check if screenrecord still active
  Future<bool> isRecording() {
    return _channel.invokeMethod("isRecording").then((value) => value ?? false);
  }
}
