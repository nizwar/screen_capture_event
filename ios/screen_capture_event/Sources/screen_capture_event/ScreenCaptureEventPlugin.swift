import Flutter
import UIKit

/// Reports screenshots (UIApplication.userDidTakeScreenshotNotification) and screen
/// recording, mirroring and AirPlay (UIScreen.isCaptured) to Dart.
public class ScreenCaptureEventPlugin: NSObject, FlutterPlugin {
    private let channel: FlutterMethodChannel
    private var watching = false

    init(channel: FlutterMethodChannel) {
        self.channel = channel
    }

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "screencapture_method", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(ScreenCaptureEventPlugin(channel: channel), channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "isRecording":
            result(UIScreen.main.isCaptured)
        case "watch":
            startWatching()
            result(nil)
        case "dispose":
            stopWatching()
            result(nil)
        case "prevent_screenshot", "request_permission":
            // Android only.
            result(nil)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    public func detachFromEngine(for registrar: FlutterPluginRegistrar) {
        stopWatching()
    }

    private func startWatching() {
        guard !watching else { return }
        watching = true
        NotificationCenter.default.addObserver(
            self, selector: #selector(screenshotTaken),
            name: UIApplication.userDidTakeScreenshotNotification, object: nil)
        NotificationCenter.default.addObserver(
            self, selector: #selector(captureChanged),
            name: UIScreen.capturedDidChangeNotification, object: nil)
        if UIScreen.main.isCaptured {
            channel.invokeMethod("screenrecord", arguments: true)
        }
    }

    private func stopWatching() {
        guard watching else { return }
        watching = false
        NotificationCenter.default.removeObserver(
            self, name: UIApplication.userDidTakeScreenshotNotification, object: nil)
        NotificationCenter.default.removeObserver(
            self, name: UIScreen.capturedDidChangeNotification, object: nil)
    }

    @objc private func screenshotTaken(_ notification: Notification) {
        // iOS doesn't tell apps where screenshots are saved.
        channel.invokeMethod("screenshot", arguments: "ios_not_supported")
    }

    @objc private func captureChanged(_ notification: Notification) {
        channel.invokeMethod("screenrecord", arguments: UIScreen.main.isCaptured)
    }
}
