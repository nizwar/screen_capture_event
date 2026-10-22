package id.nizwar.screen_capture_event;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * ScreenCaptureEventPlugin
 *
 * Performs all filesystem scanning and FileObserver setup off the main thread to
 * avoid the main-thread deadlock / ANR seen on Android 16, and uses the correct
 * media permission on Android 13+ (READ_MEDIA_IMAGES).
 */
public class ScreenCaptureEventPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    static int SCREEN_CAPTURE_PERMISSION = 101;
    private MethodChannel channel;
    private FileObserver fileObserver;
    private Timer timeout = new Timer();
    private final Map<String, FileObserver> watchModifier = new HashMap<>();
    private ActivityPluginBinding activityPluginBinding;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean screenRecording = false;
    private long tempSize = 0;

    // Executor to run heavy I/O work on a background thread.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "screencapture_method");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "prevent_screenshot":
                if ((boolean) call.arguments) {
                    activityPluginBinding.getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                } else {
                    activityPluginBinding.getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
                result.success(null);
                break;
            case "isRecording":
                result.success(screenRecording);
                break;
            case "request_permission":
                String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        ? Manifest.permission.READ_MEDIA_IMAGES
                        : Manifest.permission.READ_EXTERNAL_STORAGE;

                if (ContextCompat.checkSelfPermission(activityPluginBinding.getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activityPluginBinding.getActivity(), new String[]{permission}, 101);
                }
                result.success(null);
                break;
            case "watch":
                // Run the initial scan on a background thread to avoid freezing the app.
                executor.execute(this::updateScreenRecordStatus);

                if (Build.VERSION.SDK_INT >= 29) {
                    final List<File> files = new ArrayList<>();
                    final List<String> paths = new ArrayList<>();
                    for (Path path : Path.values()) {
                        files.add(new File(path.getPath()));
                        paths.add(path.getPath());
                    }
                    fileObserver = new FileObserver(files) {
                        @Override
                        public void onEvent(int event, final String filename) {
                            if (filename == null) return;
                            executor.execute(() -> handleFileEvent(event, filename, paths));
                        }
                    };
                } else {
                    // Logic for older Android versions.
                    for (final Path path : Path.values()) {
                        fileObserver = new FileObserver(path.getPath()) {
                            @Override
                            public void onEvent(int event, final String filename) {
                                if (filename == null) return;
                                List<String> p = new ArrayList<>();
                                p.add(path.getPath());
                                executor.execute(() -> handleFileEvent(event, filename, p));
                            }
                        };
                    }
                }
                if (fileObserver != null) fileObserver.startWatching();
                result.success(null);
                break;
            case "dispose":
                if (fileObserver != null) fileObserver.stopWatching();
                stopAllRecordWatcher();
                result.success(null);
                break;
            default:
                result.notImplemented();
        }
    }

    private void handleFileEvent(int event, String filename, List<String> paths) {
        for (String fullPath : paths) {
            File file = new File(fullPath + filename);
            if (file.exists()) {
                String mime = getMimeType(file.getPath());
                if (mime != null) {
                    if (event == FileObserver.CREATE || event == FileObserver.MODIFY) {
                        if (mime.contains("video")) {
                            setScreenRecordStatus(true);
                            updateScreenRecordStatus();
                        } else if (mime.contains("image")) {
                            handler.post(() -> channel.invokeMethod("screenshot", file.getPath()));
                        }
                    } else {
                        if (mime.contains("video")) {
                            stopAllRecordWatcher();
                        }
                    }
                }
            }
        }
    }

    private void stopAllRecordWatcher() {
        for (Map.Entry<String, FileObserver> stringObjectEntry : watchModifier.entrySet()) {
            stringObjectEntry.getValue().stopWatching();
        }
        watchModifier.clear();
        setScreenRecordStatus(false);
    }

    private void updateScreenRecordStatus() {
        // Runs entirely on the executor.
        for (Path path : Path.values()) {
            File newFile = getLastModified(path.getPath());
            if (newFile != null) {
                String mime = getMimeType(newFile.getPath());
                if (mime != null && mime.contains("video") && !watchModifier.containsKey(newFile.getPath())) {
                    handler.post(() -> {
                        FileObserver fo;
                        if (Build.VERSION.SDK_INT >= 29) {
                            fo = new FileObserver(newFile) {
                                @Override
                                public void onEvent(int event, @Nullable String p) {
                                    handleUpdateScreenRecordEvent(event, newFile);
                                }
                            };
                        } else {
                            fo = new FileObserver(newFile.getPath()) {
                                @Override
                                public void onEvent(int event, @Nullable String p) {
                                    handleUpdateScreenRecordEvent(event, newFile);
                                }
                            };
                        }
                        watchModifier.put(newFile.getPath(), fo);
                        fo.startWatching();
                    });
                }
            }
        }
    }

    private void handleUpdateScreenRecordEvent(int event, File newFile) {
        executor.execute(() -> {
            long curSize = newFile.length();
            if (curSize > tempSize) {
                if (timeout != null) {
                    try {
                        timeout.cancel();
                    } catch (Exception ignored) {}
                }
                setScreenRecordStatus(event == FileObserver.MODIFY);
                tempSize = curSize;
            }

            timeout = new Timer();
            timeout.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (watchModifier.containsKey(newFile.getPath())) {
                        setScreenRecordStatus(newFile.length() != tempSize);
                    }
                }
            }, 1500);
        });
    }

    void setScreenRecordStatus(boolean value) {
        if (screenRecording != value) {
            handler.post(() -> {
                screenRecording = value;
                channel.invokeMethod("screenrecord", value);
            });
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        executor.shutdown();
    }

    public static String getMimeType(String url) {
        int lastDotIndex = url.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < url.length() - 1) {
            String extension = url.substring(lastDotIndex + 1);
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return null;
    }

    public static File getLastModified(String directoryFilePath) {
        try {
            File directory = new File(directoryFilePath);

            // Verify the directory exists and is readable.
            if (!directory.exists() || !directory.isDirectory() || !directory.canRead()) {
                return null;
            }

            // Use a FileFilter to collect only readable files and reduce overhead.
            File[] files = directory.listFiles(new java.io.FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.isFile() && file.canRead();
                }
            });

            if (files == null || files.length == 0) {
                return null;
            }

            // Cap the number of files scanned to avoid freezing when a folder has many files.
            int maxFilesToCheck = Math.min(files.length, 200);

            // When there are many files, partially sort so the newest are prioritized.
            // Only sort as many as needed to avoid wasting time.
            if (files.length > 50) {
                // Use Arrays.sort with a comparator to sort by lastModified descending.
                java.util.Arrays.sort(files, 0, Math.min(files.length, 100),
                    (f1, f2) -> {
                        try {
                            long time1 = f1.lastModified();
                            long time2 = f2.lastModified();
                            return Long.compare(time2, time1); // descending
                        } catch (Exception e) {
                            return 0;
                        }
                    });
            }

            long lastModifiedTime = Long.MIN_VALUE;
            File chosenFile = null;

            // Scan the capped set of files, preferring the sorted ones.
            for (int i = 0; i < maxFilesToCheck; i++) {
                File file = files[i];
                try {
                    long modifiedTime = file.lastModified();
                    if (modifiedTime > lastModifiedTime) {
                        chosenFile = file;
                        lastModifiedTime = modifiedTime;
                    }
                } catch (Exception e) {
                    // Skip a problematic file and continue.
                    continue;
                }
            }

            return chosenFile;
        } catch (SecurityException e) {
            // No permission to access the directory.
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityPluginBinding = binding;
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {}

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activityPluginBinding = binding;
    }

    @Override
    public void onDetachedFromActivity() {
        activityPluginBinding = null;
    }

    public enum Path {
        DCIMSAMSUNG(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "Screen recordings" + File.separator),
        DCIM(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "Screenshots" + File.separator),
        PICTURES(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + "Screenshots" + File.separator);

        final private String path;
        public String getPath() { return path; }
        Path(String path) { this.path = path; }
    }
}
