package id.nizwar.screen_capture_event;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/**
 * Reports screenshots and screen recordings to Dart.
 *
 * <p>Screenshots: Android 14 and later report them through the system's screen capture
 * callback (screenshots of this app while it is visible, no permission prompt). Older versions
 * watch the screenshot folders, which needs read access to them. In both cases the file's path
 * comes from the screenshot folders when the app can read them.
 *
 * <p>Screen recordings: Android 15 and later use the system's screen recording callback. Older
 * versions watch the folders that some recorders (Samsung, Xiaomi, Huawei) write into while
 * recording; recorders that only save the file once they stop can't be detected there.
 *
 * <p>All file system work and detection state live on {@link #worker}; the main thread handles
 * method calls and delivers events.
 */
public class ScreenCaptureEventPlugin
        implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    private static final String TAG = "ScreenCaptureEvent";
    private static final int PERMISSION_REQUEST_CODE = 101;
    /** How long to wait for the screenshot's file after Android 14's callback. */
    private static final long SCREENSHOT_FILE_TIMEOUT_MS = 3000;
    /** A screenshot file is reported once, although several events announce it. */
    private static final long SCREENSHOT_DEDUP_MS = 10000;
    /** A recording counts as stopped once its file hasn't been written to for this long. */
    private static final long RECORDING_IDLE_MS = 5000;
    private static final int IN_ISDIR = 0x40000000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MethodChannel channel;
    private Context context;
    private ScheduledExecutorService worker;

    // Main thread (watching is also read on the worker thread).
    @Nullable private ActivityPluginBinding activityBinding;
    private volatile boolean watching;
    private boolean secure;
    @Nullable private Activity captureActivity;
    @Nullable private Object screenCaptureCallback;
    @Nullable private Consumer<Integer> screenRecordingCallback;

    // Worker thread.
    private final List<FileObserver> observers = new ArrayList<>();
    private final Map<String, Long> reportedScreenshots = new HashMap<>();
    private boolean observing;
    @Nullable private ScheduledFuture<?> pendingScreenshot;
    @Nullable private String lastScreenshotFile;
    private long lastScreenshotFileAt;
    @Nullable private ScheduledFuture<?> recordingIdle;

    // Written on the worker thread, read by isRecording.
    private volatile boolean recording;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        context = binding.getApplicationContext();
        worker = Executors.newSingleThreadScheduledExecutor();
        channel = new MethodChannel(binding.getBinaryMessenger(), "screencapture_method");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        stopWatching();
        channel.setMethodCallHandler(null);
        worker.shutdown();
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "prevent_screenshot":
                secure = Boolean.TRUE.equals(call.arguments);
                applySecureFlag();
                result.success(null);
                break;
            case "isRecording":
                result.success(recording);
                break;
            case "request_permission":
                requestPermission();
                result.success(null);
                break;
            case "watch":
                startWatching();
                result.success(null);
                break;
            case "dispose":
                stopWatching();
                result.success(null);
                break;
            default:
                result.notImplemented();
        }
    }

    // ---------------------------------------------------------------- activity

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        binding.addRequestPermissionsResultListener(this);
        applySecureFlag();
        if (watching) registerScreenCaptureCallback();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        unregisterScreenCaptureCallback();
        if (activityBinding != null) activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) return false;
        // Folders that couldn't be read before can be watched now.
        if (watching) worker.execute(this::watchFolders);
        return true;
    }

    private void applySecureFlag() {
        if (activityBinding == null) return;
        if (secure) {
            activityBinding.getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activityBinding.getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    // ------------------------------------------------------------- permissions

    /**
     * Asks for read access to the screenshot and recording folders. Only permissions the app
     * declares are requested: undeclared ones are denied without a prompt, and declaring the
     * Android 13+ media permissions is left to the app because of Google Play's policy on them.
     */
    private void requestPermission() {
        if (activityBinding == null) return;
        Activity activity = activityBinding.getActivity();
        List<String> wanted = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            wanted.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (Build.VERSION.SDK_INT < 35) wanted.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        Set<String> declared = declaredPermissions();
        List<String> missing = new ArrayList<>();
        for (String permission : wanted) {
            if (declared.contains(permission)
                    && ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private Set<String> declaredPermissions() {
        Set<String> declared = new LinkedHashSet<>();
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) declared.addAll(Arrays.asList(info.requestedPermissions));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return declared;
    }

    private boolean canReadImages() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------------------------------------------------------------- watching

    private void startWatching() {
        if (watching) return;
        watching = true;
        registerScreenCaptureCallback();
        registerScreenRecordingCallback();
        worker.execute(this::watchFolders);
    }

    private void stopWatching() {
        if (!watching) return;
        watching = false;
        unregisterScreenCaptureCallback();
        unregisterScreenRecordingCallback();
        worker.execute(this::stopDetection);
    }

    private void registerScreenCaptureCallback() {
        if (Build.VERSION.SDK_INT < 34 || screenCaptureCallback != null || activityBinding == null) return;
        Activity activity = activityBinding.getActivity();
        Activity.ScreenCaptureCallback callback = this::onScreenCaptured;
        try {
            activity.registerScreenCaptureCallback(worker, callback);
            captureActivity = activity;
            screenCaptureCallback = callback;
        } catch (SecurityException e) {
            Log.w(TAG, "Screenshot callback unavailable: " + e.getMessage());
        }
    }

    private void unregisterScreenCaptureCallback() {
        if (Build.VERSION.SDK_INT < 34 || screenCaptureCallback == null || captureActivity == null) return;
        captureActivity.unregisterScreenCaptureCallback((Activity.ScreenCaptureCallback) screenCaptureCallback);
        screenCaptureCallback = null;
        captureActivity = null;
    }

    private void registerScreenRecordingCallback() {
        if (Build.VERSION.SDK_INT < 35 || screenRecordingCallback != null) return;
        Consumer<Integer> callback = state -> setRecording(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE);
        try {
            int state = windowManager().addScreenRecordingCallback(worker, callback);
            screenRecordingCallback = callback;
            boolean visible = state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE;
            worker.execute(() -> setRecording(visible));
        } catch (SecurityException e) {
            Log.w(TAG, "Screen recording callback unavailable: " + e.getMessage());
        }
    }

    private void unregisterScreenRecordingCallback() {
        if (Build.VERSION.SDK_INT < 35 || screenRecordingCallback == null) return;
        windowManager().removeScreenRecordingCallback(screenRecordingCallback);
        screenRecordingCallback = null;
    }

    @RequiresApi(35)
    private WindowManager windowManager() {
        return context.getSystemService(WindowManager.class);
    }

    // --------------------------------------------------- worker: screenshots

    /** Android 14+: a screenshot of this app was taken; its file shows up a moment later. */
    private void onScreenCaptured() {
        if (!canReadImages()) {
            sendScreenshot("");
            return;
        }
        long now = System.currentTimeMillis();
        if (lastScreenshotFile != null && now - lastScreenshotFileAt < SCREENSHOT_FILE_TIMEOUT_MS) {
            String path = lastScreenshotFile;
            lastScreenshotFile = null;
            sendScreenshot(path);
            return;
        }
        if (pendingScreenshot != null) pendingScreenshot.cancel(false);
        pendingScreenshot = worker.schedule(() -> {
            pendingScreenshot = null;
            sendScreenshot("");
        }, SCREENSHOT_FILE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void onScreenshotFile(String path) {
        long now = System.currentTimeMillis();
        for (Iterator<Long> it = reportedScreenshots.values().iterator(); it.hasNext(); ) {
            if (now - it.next() > SCREENSHOT_DEDUP_MS) it.remove();
        }
        if (reportedScreenshots.containsKey(path)) return;
        reportedScreenshots.put(path, now);

        if (Build.VERSION.SDK_INT >= 34) {
            // Reported through the system callback; the file only tells which one it was.
            if (pendingScreenshot == null) {
                lastScreenshotFile = path;
                lastScreenshotFileAt = now;
                return;
            }
            pendingScreenshot.cancel(false);
            pendingScreenshot = null;
        }
        sendScreenshot(path);
    }

    private void sendScreenshot(String path) {
        mainHandler.post(() -> channel.invokeMethod("screenshot", path));
    }

    // ---------------------------------------------------- worker: recordings

    private void onRecordingWritten() {
        setRecording(true);
        if (recordingIdle != null) recordingIdle.cancel(false);
        recordingIdle = worker.schedule(this::onRecordingFinished, RECORDING_IDLE_MS, TimeUnit.MILLISECONDS);
    }

    private void onRecordingFinished() {
        if (recordingIdle != null) recordingIdle.cancel(false);
        recordingIdle = null;
        setRecording(false);
    }

    private void setRecording(boolean value) {
        if (recording == value) return;
        recording = value;
        mainHandler.post(() -> channel.invokeMethod("screenrecord", value));
    }

    // ------------------------------------------------------ worker: folders

    private static File publicDirectory(String type) {
        return Environment.getExternalStoragePublicDirectory(type);
    }

    private static List<File> watchedDirectories() {
        List<File> directories = new ArrayList<>(Arrays.asList(
                new File(publicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
                new File(publicDirectory(Environment.DIRECTORY_DCIM), "Screenshots")));
        if (Build.VERSION.SDK_INT < 35) {
            // Recorders that write into a public folder while recording.
            directories.add(new File(publicDirectory(Environment.DIRECTORY_DCIM), "Screen recordings"));
            directories.add(new File(publicDirectory(Environment.DIRECTORY_DCIM), "ScreenRecorder"));
        }
        return directories;
    }

    /** (Re)creates the folder observers; screenshots and recordings in progress are kept. */
    private void watchFolders() {
        unwatchFolders();
        if (!watching) return;
        Set<File> parents = new LinkedHashSet<>();
        for (File directory : watchedDirectories()) {
            if (directory.isDirectory()) {
                observers.add(DirectoryObserver.create(this, directory, false));
            } else {
                // Watch the parent until the folder exists: it is created with the first file.
                File parent = directory.getParentFile();
                if (parent != null && parent.isDirectory()) parents.add(parent);
            }
        }
        for (File parent : parents) observers.add(DirectoryObserver.create(this, parent, true));
        for (FileObserver observer : observers) observer.startWatching();
        observing = true;
    }

    private void unwatchFolders() {
        for (FileObserver observer : observers) observer.stopWatching();
        observers.clear();
        observing = false;
    }

    /** Stops watching and forgets the screenshots and recordings in progress. */
    private void stopDetection() {
        unwatchFolders();
        if (pendingScreenshot != null) pendingScreenshot.cancel(false);
        pendingScreenshot = null;
        lastScreenshotFile = null;
        if (recordingIdle != null) recordingIdle.cancel(false);
        recordingIdle = null;
        if (Build.VERSION.SDK_INT < 35) setRecording(false);
    }

    /** A watched folder was just created: watch it, and catch a file that is already complete. */
    private void onFolderCreated(File folder) {
        watchFolders();
        File[] files = folder.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File file : files) {
            String mime = mimeType(file.getName());
            if (mime != null && mime.startsWith("image/") && !file.getName().startsWith(".")
                    && now - file.lastModified() < SCREENSHOT_DEDUP_MS) {
                onScreenshotFile(file.getPath());
            }
        }
    }

    private void onFileEvent(File directory, boolean parentOnly, int event, String name) {
        if (!observing) return;
        boolean isDirectory = (event & IN_ISDIR) != 0;
        int type = event & FileObserver.ALL_EVENTS;
        if (parentOnly) {
            File folder = new File(directory, name);
            if (isDirectory && (type == FileObserver.CREATE || type == FileObserver.MOVED_TO)
                    && watchedDirectories().contains(folder)) {
                onFolderCreated(folder);
            }
            return;
        }
        if (isDirectory) return;

        String mime = mimeType(name);
        if (mime == null) return;
        boolean finished = type == FileObserver.CLOSE_WRITE || type == FileObserver.MOVED_TO;
        if (mime.startsWith("image/")) {
            // Pending files (".pending-...") are renamed once they are complete.
            if (finished && !name.startsWith(".")) onScreenshotFile(new File(directory, name).getPath());
        } else if (mime.startsWith("video/") && Build.VERSION.SDK_INT < 35) {
            if (type == FileObserver.CREATE || type == FileObserver.MODIFY) {
                onRecordingWritten();
            } else if (finished) {
                onRecordingFinished();
            }
        }
    }

    @Nullable
    private static String mimeType(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    /** Forwards one folder's events to the worker thread; kept referenced so it isn't collected. */
    private static final class DirectoryObserver extends FileObserver {
        private static final int FILE_EVENTS =
                FileObserver.CREATE | FileObserver.MODIFY | FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO;
        private static final int FOLDER_EVENTS = FileObserver.CREATE | FileObserver.MOVED_TO;

        private final ScreenCaptureEventPlugin plugin;
        private final File directory;
        private final boolean parentOnly;

        static DirectoryObserver create(ScreenCaptureEventPlugin plugin, File directory, boolean parentOnly) {
            int mask = parentOnly ? FOLDER_EVENTS : FILE_EVENTS;
            return Build.VERSION.SDK_INT >= 29
                    ? new DirectoryObserver(plugin, directory, mask, parentOnly)
                    : new DirectoryObserver(plugin, directory.getPath(), mask, parentOnly);
        }

        @RequiresApi(29)
        private DirectoryObserver(ScreenCaptureEventPlugin plugin, File directory, int mask, boolean parentOnly) {
            super(directory, mask);
            this.plugin = plugin;
            this.directory = directory;
            this.parentOnly = parentOnly;
        }

        @SuppressWarnings("deprecation")
        private DirectoryObserver(ScreenCaptureEventPlugin plugin, String path, int mask, boolean parentOnly) {
            super(path, mask);
            this.plugin = plugin;
            this.directory = new File(path);
            this.parentOnly = parentOnly;
        }

        @Override
        public void onEvent(int event, @Nullable String name) {
            if (name == null) return;
            try {
                plugin.worker.execute(() -> plugin.onFileEvent(directory, parentOnly, event, name));
            } catch (RejectedExecutionException ignored) {
                // The engine is gone.
            }
        }
    }
}
