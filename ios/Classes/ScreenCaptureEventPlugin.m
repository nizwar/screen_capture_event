#import "ScreenCaptureEventPlugin.h"
#if __has_include(<screen_capture_event/screen_capture_event-Swift.h>)
#import <screen_capture_event/screen_capture_event-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "screen_capture_event-Swift.h"
#endif

@implementation ScreenCaptureEventPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftScreenCaptureEventPlugin registerWithRegistrar:registrar];
}
@end
