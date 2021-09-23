import Flutter
import UIKit

public class SwiftScreenCaptureEventPlugin: NSObject, FlutterPlugin {
    private var sink : FlutterEventSink?
    static var channel: FlutterMethodChannel?
    static var preventScreenshot: Bool = false
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        channel = FlutterMethodChannel(name: "screencapture_method", binaryMessenger: registrar.messenger())
        let instance = SwiftScreenCaptureEventPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel!)
        
        channel!.setMethodCallHandler { (call: FlutterMethodCall, result:@escaping FlutterResult) -> Void in
            switch call.method {
            case "isRecording":
                result.self(checkCaptured())
                break
            case "watch":
                if checkCaptured() {channel!.invokeMethod("screenrecord", arguments: true)}
                NotificationCenter.default.addObserver(self, selector: #selector(screenshotChange(_:)), name: UIApplication.userDidTakeScreenshotNotification, object: nil)
                if #available(iOS 11.0, *) {
                    NotificationCenter.default.addObserver(self, selector: #selector(screenrecordChange(_:)), name: UIScreen.capturedDidChangeNotification, object: nil)
                }
                break;
            case "dispose":
                NotificationCenter.default.removeObserver(self, name: UIApplication.userDidTakeScreenshotNotification, object: nil)
                if #available(iOS 11.0, *) {
                    NotificationCenter.default.removeObserver(self, name: UIScreen.capturedDidChangeNotification, object: nil)
                }
                break;
            default:
                break
            }
        }
    }
    
    @objc static func screenshotChange(_ isCaptured: Bool) {
        channel!.invokeMethod("screenshot", arguments: "ios_not_supported")
    }
    
    @objc static func screenrecordChange(_ isCaptured: Bool) {
        channel!.invokeMethod("screenrecord", arguments: checkCaptured())
    }
    
    public static func checkCaptured() -> Bool{
        let ui = UIScreen.main
        if #available(iOS 11.0, *){
            if ui.isCaptured{
                return true
            }
        }
        return false
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self, name: UIApplication.userDidTakeScreenshotNotification, object: nil)
        if #available(iOS 11.0, *) {
            NotificationCenter.default.removeObserver(self, name: UIScreen.capturedDidChangeNotification, object: nil)
        }
    }
}
