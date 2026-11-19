package cn.local.xxjzrestore;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class RestoreHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.cxincx.xxjz";
    private static final String COMPONENT =
        PACKAGE + "/com.google.android.accessibility.selecttospeak.SelectToSpeakService";
    private static final String SERVICES = "enabled_accessibility_services";
    private static final String ENABLED = "accessibility_enabled";
    private static final String TAG = "XxjzRestore";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loaded) {
        boolean mainProcess = PACKAGE.equals(loaded.processName);
        boolean widgetProcess = (PACKAGE + ":widgetProvider").equals(loaded.processName);
        if (!PACKAGE.equals(loaded.packageName) || (!mainProcess && !widgetProcess)
                || UserHandle.getUserHandleForUid(android.os.Process.myUid()).hashCode() != 0) {
            return;
        }
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    start((Context) param.args[0], mainProcess);
                }
            });
    }

    private static void start(Context context, boolean watchSettings) {
        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        Handler worker = new Handler(thread.getLooper());
        ContentResolver resolver = context.getContentResolver();
        Runnable restore = () -> restore(context, resolver);
        if (!watchSettings) {
            worker.post(() -> {
                try {
                    restore.run();
                } finally {
                    thread.quitSafely();
                }
            });
            return;
        }
        ContentObserver observer = new ContentObserver(worker) {
            @Override
            public void onChange(boolean selfChange) {
                worker.removeCallbacks(restore);
                worker.postDelayed(restore, 350);
            }
        };
        resolver.registerContentObserver(Settings.Secure.getUriFor(SERVICES), false, observer);
        resolver.registerContentObserver(Settings.Secure.getUriFor(ENABLED), false, observer);
        worker.post(restore);
        Log.i(TAG, "Watching accessibility settings in " + PACKAGE);
    }

    private static boolean includesService(String services) {
        return services != null && Arrays.asList(services.split(":")).contains(COMPONENT);
    }

    private static void restore(Context context, ContentResolver resolver) {
        if (!context.getSystemService(UserManager.class).isUserUnlocked()) {
            return;
        }
        String services = Settings.Secure.getString(resolver, SERVICES);
        if (includesService(services) && "1".equals(Settings.Secure.getString(resolver, ENABLED))) {
            return;
        }
        try {
            // Read the current list with Root immediately before the write.
            services = root("get secure " + SERVICES);
            if ("null".equals(services)) {
                services = "";
            }
            if (!services.matches("[a-zA-Z0-9_.$:/-]*")) {
                throw new IOException("Unexpected accessibility service list");
            }
            if (!includesService(services)) {
                String updated = services.isEmpty() ? COMPONENT : services + ":" + COMPONENT;
                root("put secure " + SERVICES + " '" + updated + "'");
            }
            if (!"1".equals(root("get secure " + ENABLED))) {
                root("put secure " + ENABLED + " 1");
            }
            Log.i(TAG, "Restored bookkeeping accessibility");
        } catch (IOException e) {
            Log.w(TAG, "Root restore failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String root(String arguments) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("/system/bin/su", "-c",
                "/system/bin/settings --user 0 " + arguments).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IOException("Root command timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Root command exit code " + process.exitValue());
            }
            try (BufferedReader output = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = output.readLine();
                return line == null ? "" : line.trim();
            }
        } finally {
            process.destroy();
        }
    }
}

