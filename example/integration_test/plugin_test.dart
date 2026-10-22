import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:screen_capture_event/screen_capture_event.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('every call completes', (tester) async {
    const timeout = Duration(seconds: 5);
    final first = ScreenCaptureEvent(false);
    final second = ScreenCaptureEvent(false);

    first.watch();
    first.watch();
    second.watch();
    first.dispose();
    expect(await second.isRecording().timeout(timeout), isFalse);

    await first.preventAndroidScreenShot(true).timeout(timeout);
    await first.preventAndroidScreenShot(false).timeout(timeout);
    if (defaultTargetPlatform == TargetPlatform.iOS) {
      // On Android this would show the permission dialog.
      await first.storagePermission().timeout(timeout);
    }
    second.dispose();
    expect(await first.isRecording().timeout(timeout), isFalse);
  });
}
