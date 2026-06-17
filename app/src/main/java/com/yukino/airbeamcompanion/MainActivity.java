package com.yukino.airbeamcompanion;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // 静态实例，供 MirrorService 异步回调
    public static MainActivity instance;

    private Button btnToggle;
    private Button btnDemoToggle;
    private Button btnMirrorToggle;
    private Button btnLaunchApp;
    private Button btnSimToggle;
    private Button btnArrangeWindows;
    private Button btnFillRunningApps;
    private Button btnFillDefaultApps;
    private Button btnCompactDensity;
    private Button btnResetDensity;
    private EditText etWindowPackages;
    private TextView tvStatus;
    private FrameLayout touchPad;
    private GestureDetector gestureDetector;
    
    // 支持多个外接副屏 (包括真实的 AR 眼镜和模拟画中画副屏)
    private List<DemoPresentation> demoPresentations = new ArrayList<>();

    private MediaProjectionManager mediaProjectionManager;
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 2000;
    private boolean isMirroring = false;
    private boolean isSimulated = false;
    private boolean isRequestingMediaProjection = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        instance = this;

        btnToggle = findViewById(R.id.btn_toggle);
        btnDemoToggle = findViewById(R.id.btn_demo_toggle);
        btnMirrorToggle = findViewById(R.id.btn_mirror_toggle);
        btnLaunchApp = findViewById(R.id.btn_launch_app);
        btnSimToggle = findViewById(R.id.btn_sim_toggle);
        btnArrangeWindows = findViewById(R.id.btn_arrange_windows);
        btnFillRunningApps = findViewById(R.id.btn_fill_running_apps);
        btnFillDefaultApps = findViewById(R.id.btn_fill_default_apps);
        btnCompactDensity = findViewById(R.id.btn_compact_density);
        btnResetDensity = findViewById(R.id.btn_reset_density);
        etWindowPackages = findViewById(R.id.et_window_packages);
        tvStatus = findViewById(R.id.tv_status);
        touchPad = findViewById(R.id.touch_pad);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleService();
            }
        });

        // 启动或停止 AR 独立空间 Demo
        btnDemoToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDemo();
            }
        });

        btnMirrorToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMirror();
            }
        });

        btnLaunchApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchAppToPresentation();
            }
        });

        btnSimToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSimulatedDisplay();
            }
        });

        btnArrangeWindows.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                arrangeFreeformWindows();
            }
        });

        btnFillRunningApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fillRunningApps();
            }
        });

        btnFillDefaultApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fillDefaultApps();
            }
        });

        btnCompactDensity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyCompactDensity();
            }
        });

        btnResetDensity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetDensity();
            }
        });

        // 触摸板事件监听：滑动时将坐标发送到所有的 AR 屏幕上
        touchPad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (demoPresentations.size() > 0) {
                    float x = event.getX();
                    float y = event.getY();
                    for (DemoPresentation presentation : demoPresentations) {
                        presentation.updateCoordinates(x, y);
                    }
                }
                return true; // 拦截触摸事件
            }
        });

        // 注册双击恢复手势监听，以防万一音量键失效时可双击主屏亮屏
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (CompanionService.isRunning) {
                    stopService(new Intent(MainActivity.this, CompanionService.class));
                    Toast.makeText(MainActivity.this, "已双击恢复显示", Toast.LENGTH_SHORT).show();
                    updateUI();
                    return true;
                }
                return false;
            }
        });

        // 全局背景触摸以支持双击亮屏
        findViewById(R.id.root_layout).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 打开系统录屏授权弹窗时 Activity 也会暂停，此时不能关闭副屏 Presentation。
        if (!isRequestingMediaProjection && !isMirroring) {
            closeDemo();
        }
    }

    @Override
    protected void onDestroy() {
        instance = null;
        super.onDestroy();
        closeDemo();
    }

    private void toggleService() {
        Intent serviceIntent = new Intent(this, CompanionService.class);
        if (CompanionService.isRunning) {
            stopService(serviceIntent);
            Toast.makeText(this, "息屏助手已关闭", Toast.LENGTH_SHORT).show();
        } else {
            ContextCompat.startForegroundService(this, serviceIntent);
            Toast.makeText(this, "息屏助手已开启，手机即将黑屏", Toast.LENGTH_SHORT).show();
        }
        btnToggle.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUI();
            }
        }, 300);
    }

    private void toggleDemo() {
        if (demoPresentations.size() > 0) {
            closeDemo();
            btnDemoToggle.setText("启动 AR 独立画板");
            Toast.makeText(this, "AR 空间独立画板已关闭", Toast.LENGTH_SHORT).show();
        } else {
            // 获取系统的 DisplayManager 并找出所有可用的 Presentation 副屏
            DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager != null) {
                Display[] presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
                if (presentationDisplays.length > 0) {
                    // 遍历所有副屏投送 Presentation
                    for (Display display : presentationDisplays) {
                        DemoPresentation presentation = new DemoPresentation(MainActivity.this, display);
                        try {
                            presentation.show();
                            demoPresentations.add(presentation);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (demoPresentations.size() > 0) {
                        btnDemoToggle.setText("关闭 AR 独立画板");
                        Toast.makeText(this, "AR 空间已激活！检测到副屏数: " + demoPresentations.size(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "独立空间启动失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "未检测到外部显示屏！请先开启模拟副屏或连上您的 AR 眼镜", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void toggleSimulatedDisplay() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String cmd;
                if (isSimulated) {
                    cmd = "settings delete global overlay_display_devices";
                } else {
                    cmd = "settings put global overlay_display_devices 720x1280/160";
                }
                final boolean success = runRootCmd(cmd);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            isSimulated = !isSimulated;
                            if (isSimulated) {
                                btnSimToggle.setText("关闭模拟副屏");
                                btnSimToggle.setBackgroundTintList(ColorStateList.valueOf(0xFFDC3545)); // 红色
                                Toast.makeText(MainActivity.this, "已开启 720p 模拟副屏", Toast.LENGTH_SHORT).show();
                            } else {
                                btnSimToggle.setText("开启模拟副屏 (免眼镜调试)");
                                btnSimToggle.setBackgroundTintList(ColorStateList.valueOf(0xFF6C757D)); // 灰色
                                Toast.makeText(MainActivity.this, "已关闭模拟副屏", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "操作失败，请检查 Root 权限", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void toggleMirror() {
        Log.i(TAG, "toggleMirror: isMirroring=" + isMirroring + ", presentations=" + demoPresentations.size());
        if (demoPresentations.isEmpty()) {
            Toast.makeText(this, "请先启动 AR 独立画板！", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isMirroring) {
            for (DemoPresentation presentation : demoPresentations) {
                presentation.stopMirror();
            }
            
            Intent stopIntent = new Intent(this, MirrorService.class);
            stopIntent.setAction("STOP_MIRROR");
            stopService(stopIntent);
            
            isMirroring = false;
            btnMirrorToggle.setText("开启主屏镜像 (效果A)");
            Toast.makeText(this, "已停止主屏镜像", Toast.LENGTH_SHORT).show();
        } else {
            if (mediaProjectionManager != null) {
                isRequestingMediaProjection = true;
                Intent captureIntent;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    captureIntent = mediaProjectionManager.createScreenCaptureIntent(
                            MediaProjectionConfig.createConfigForDefaultDisplay()
                    );
                } else {
                    captureIntent = mediaProjectionManager.createScreenCaptureIntent();
                }
                startActivityForResult(captureIntent, REQUEST_CODE_MEDIA_PROJECTION);
            }
        }
    }

    private void launchAppToPresentation() {
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            Display[] presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            if (presentationDisplays.length > 0) {
                final int displayId = presentationDisplays[0].getDisplayId();
                // 在 Android 11 上，利用 Root 启动指定界面的指令
                final String cmd = "am start -a android.intent.action.VIEW -d \"https://www.baidu.com\" --display " + displayId;
                
                Toast.makeText(this, "正在向 AR 眼镜抛送独立网页...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean success = runRootCmd(cmd);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (success) {
                                    Toast.makeText(MainActivity.this, "效果B：网页已成功抛送至眼镜独立运行", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "抛送失败，请检查手机 Root 权限", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }).start();
            } else {
                Toast.makeText(this, "未检测到外部显示屏！请先开启模拟副屏或连上您的 AR 眼镜", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void arrangeFreeformWindows() {
        final List<String> packages = parsePackageInputs(etWindowPackages.getText().toString());
        if (packages.isEmpty()) {
            Toast.makeText(this, "请先输入要编排的 App 包名", Toast.LENGTH_SHORT).show();
            return;
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        Toast.makeText(this, "正在切换横屏并编排多窗口...", Toast.LENGTH_SHORT).show();
        btnArrangeWindows.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder report = new StringBuilder();
                runRootCmd("settings put system accelerometer_rotation 0\n"
                        + "settings put system user_rotation 1\n"
                        + "settings put global enable_freeform_support 1\n"
                        + "settings put global force_resizable_activities 1\n"
                        + "settings put global development_force_resizable_activities 1");
                sleepQuietly(1200);

                List<String> components = new ArrayList<>();
                for (String packageName : packages) {
                    String component = resolveLaunchComponent(packageName);
                    if (component == null) {
                        report.append(packageName).append(": 未找到启动 Activity\n");
                    } else {
                        components.add(component);
                    }
                }

                List<Rect> bounds = calculateLandscapeWindowBounds(components.size());
                for (int i = 0; i < components.size(); i++) {
                    String component = components.get(i);
                    String packageName = component.substring(0, component.indexOf('/'));
                    Rect rect = bounds.get(i);

                    String startOutput = runRootCmdWithOutput("am force-stop " + packageName + "\n"
                            + "am start-activity -S --windowingMode 5 --activity-multiple-task -f 0x10000000 -n " + component);
                    Log.i(TAG, "freeform start " + component + ": " + startOutput);
                    sleepQuietly(1200);

                    int taskId = findTaskIdForPackage(runRootCmdWithOutput("am stack list"), packageName);
                    if (taskId == -1) {
                        report.append(packageName).append(": 未找到 taskId\n");
                        continue;
                    }

                    String resizeCmd = "am task resizeable " + taskId + " 3\n"
                            + "am task resize " + taskId + " "
                            + rect.left + " " + rect.top + " " + rect.right + " " + rect.bottom;
                    String resizeOutput = runRootCmdWithOutput(resizeCmd);
                    Log.i(TAG, "freeform resize " + packageName + " task=" + taskId + ": " + resizeOutput);
                    report.append(packageName)
                            .append(": task ")
                            .append(taskId)
                            .append(resizeOutput.toLowerCase().contains("exception") ? " resize 失败\n" : " 已编排\n");
                    sleepQuietly(300);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnArrangeWindows.setEnabled(true);
                        String message = report.length() > 0 ? report.toString().trim() : "没有可编排的 App";
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void fillRunningApps() {
        Toast.makeText(this, "正在读取已打开 App...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> packages = parseRunningPackages(runRootCmdWithOutput("am stack list"));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (packages.isEmpty()) {
                            Toast.makeText(MainActivity.this, "未找到可填入的已打开 App", Toast.LENGTH_SHORT).show();
                        } else {
                            etWindowPackages.setText(joinLines(packages));
                        }
                    }
                });
            }
        }).start();
    }

    private void fillDefaultApps() {
        etWindowPackages.setText("com.android.settings\norg.videolan.vlc\ntv.danmaku.bili\ncom.ss.android.ugc.aweme");
    }

    private void applyCompactDensity() {
        Toast.makeText(this, "正在切换紧凑显示密度...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = runRootCmd("wm density 320");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                success ? "已切换紧凑显示，可重新编排窗口" : "紧凑显示失败，请检查 Root 权限",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void resetDensity() {
        Toast.makeText(this, "正在恢复默认显示密度...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = runRootCmd("wm density reset");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                success ? "已恢复默认显示密度" : "恢复显示密度失败，请检查 Root 权限",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private List<String> parsePackageInputs(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) return result;

        String[] tokens = raw.split("[\\s,，;；]+");
        for (String token : tokens) {
            String value = token.trim();
            if (value.length() == 0) continue;
            if (value.matches("[A-Za-z0-9_.$]+(/[A-Za-z0-9_.$]+)?")) {
                result.add(value);
            }
        }
        return result;
    }

    private String resolveLaunchComponent(String packageOrComponent) {
        if (packageOrComponent.contains("/")) {
            return packageOrComponent;
        }

        PackageManager packageManager = getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageOrComponent);
        if (launchIntent == null) {
            return resolveLaunchComponentWithShell(packageOrComponent);
        }

        ComponentName componentName = launchIntent.getComponent();
        if (componentName == null) {
            componentName = launchIntent.resolveActivity(packageManager);
        }
        if (componentName == null) {
            return resolveLaunchComponentWithShell(packageOrComponent);
        }
        return componentName.flattenToShortString();
    }

    private String resolveLaunchComponentWithShell(String packageName) {
        String output = runRootCmdWithOutput("cmd package resolve-activity --brief " + packageName);
        Log.i(TAG, "resolve-activity " + packageName + ": " + output);
        String[] lines = output.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith(packageName + "/")) {
                return line;
            }
        }
        return null;
    }

    private List<Rect> calculateWindowBounds(int count) {
        List<Rect> result = new ArrayList<>();
        if (count <= 0) return result;

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }

        int width = metrics.widthPixels > 0 ? metrics.widthPixels : 1080;
        int height = metrics.heightPixels > 0 ? metrics.heightPixels : 2340;
        int usableTop = Math.max(0, (int) (28 * getResources().getDisplayMetrics().density));
        int usableBottom = height - Math.max(0, (int) (16 * getResources().getDisplayMetrics().density));

        int windows = Math.min(count, 4);
        int columns = windows <= 2 ? 1 : 2;
        int rows = (int) Math.ceil(windows / (float) columns);
        int cellWidth = width / columns;
        int cellHeight = (usableBottom - usableTop) / rows;

        for (int i = 0; i < count; i++) {
            int slot = i % 4;
            int column = slot % columns;
            int row = slot / columns;
            int left = column * cellWidth;
            int top = usableTop + row * cellHeight;
            int right = column == columns - 1 ? width : (column + 1) * cellWidth;
            int bottom = row == rows - 1 ? usableBottom : usableTop + (row + 1) * cellHeight;
            result.add(new Rect(left, top, right, bottom));
        }
        return result;
    }

    private List<Rect> calculateLandscapeWindowBounds(int count) {
        List<Rect> result = new ArrayList<>();
        if (count <= 0) return result;

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }

        int rawWidth = metrics.widthPixels > 0 ? metrics.widthPixels : 1080;
        int rawHeight = metrics.heightPixels > 0 ? metrics.heightPixels : 2340;
        int width = Math.max(rawWidth, rawHeight);
        int height = Math.min(rawWidth, rawHeight);
        float density = getResources().getDisplayMetrics().density;
        int margin = Math.max(8, (int) (8 * density));
        int usableTop = Math.max(0, (int) (24 * density));
        int usableBottom = height - margin;

        int windows = Math.min(count, 4);
        int columns = windows == 1 ? 1 : Math.min(windows, 4);
        int rows = windows <= 4 ? 1 : 2;
        int cellWidth = width / columns;
        int cellHeight = (usableBottom - usableTop) / rows;

        for (int i = 0; i < count; i++) {
            int slot = i % 8;
            int column = slot % columns;
            int row = slot / columns;
            int left = column * cellWidth + margin;
            int top = usableTop + row * cellHeight + margin;
            int right = (column == columns - 1 ? width : (column + 1) * cellWidth) - margin;
            int bottom = (row == rows - 1 ? usableBottom : usableTop + (row + 1) * cellHeight) - margin;
            result.add(new Rect(left, top, right, bottom));
        }
        return result;
    }

    private int findTaskIdForPackage(String stackOutput, String packageName) {
        if (stackOutput == null) return -1;

        String[] lines = stackOutput.split("\\r?\\n");
        for (String line : lines) {
            if (!line.contains("taskId=") || !line.contains(packageName)) continue;

            int start = line.indexOf("taskId=") + "taskId=".length();
            int end = line.indexOf(':', start);
            if (end == -1) continue;
            try {
                return Integer.parseInt(line.substring(start, end).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private List<String> parseRunningPackages(String stackOutput) {
        List<String> result = new ArrayList<>();
        if (stackOutput == null) return result;

        String[] lines = stackOutput.split("\\r?\\n");
        for (String line : lines) {
            if (!line.contains("taskId=") || !line.contains(": ")) continue;
            int marker = line.indexOf(": ");
            String rest = line.substring(marker + 2).trim();
            int slash = rest.indexOf('/');
            if (slash <= 0) continue;

            String packageName = rest.substring(0, slash).trim();
            if (packageName.length() == 0) continue;
            if ("unknown".equals(packageName)) continue;
            if ("net.oneplus.launcher".equals(packageName)) continue;
            if (getPackageName().equals(packageName)) continue;
            if (!result.contains(packageName)) {
                result.add(packageName);
            }
            if (result.size() >= 4) break;
        }
        return result;
    }

    private String joinLines(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(value);
        }
        return builder.toString();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            isRequestingMediaProjection = false;
            Log.i(TAG, "onActivityResult: resultCode=" + resultCode + ", hasData=" + (data != null)
                    + ", presentations=" + demoPresentations.size());
            if (resultCode == RESULT_OK && data != null) {
                if (!demoPresentations.isEmpty()) {
                    // 启动 MirrorService 前台服务以托管 MediaProjection
                    Intent serviceIntent = new Intent(this, MirrorService.class);
                    serviceIntent.setAction("START_MIRROR");
                    serviceIntent.putExtra("resultCode", resultCode);
                    serviceIntent.putExtra("resultData", data);
                    ContextCompat.startForegroundService(this, serviceIntent);
                    // 镜像流将在 MirrorService 初始化完毕后的回调中安全开启
                } else {
                    Toast.makeText(this, "副屏画板已关闭，请重新启动后再镜像", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "主屏录制授权失败，无法镜像", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // MirrorService 成功创建 MediaProjection 并提升为前台服务后的时序回调
    public void onMirrorServiceReady() {
        Log.i(TAG, "onMirrorServiceReady: presentations=" + demoPresentations.size());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!demoPresentations.isEmpty()) {
                    for (DemoPresentation presentation : demoPresentations) {
                        presentation.startMirror();
                    }
                    isMirroring = true;
                    btnMirrorToggle.setText("关闭主屏镜像 (效果A)");
                    Toast.makeText(MainActivity.this, "效果A：主屏已镜像投射至眼镜小窗口", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void closeDemo() {
        for (DemoPresentation presentation : demoPresentations) {
            presentation.dismiss();
        }
        demoPresentations.clear();

        if (isMirroring) {
            Intent stopIntent = new Intent(this, MirrorService.class);
            stopIntent.setAction("STOP_MIRROR");
            stopService(stopIntent);
            isMirroring = false;
        }
        if (btnMirrorToggle != null) {
            btnMirrorToggle.setText("开启主屏镜像 (效果A)");
        }
    }

    private void updateUI() {
        if (CompanionService.isRunning) {
            btnToggle.setText("停止息屏 (亮屏)");
            tvStatus.setText("状态：运行中 (屏幕已全黑，触摸保留)\n\n• 您可以在 AR 眼镜中看见画面并触控操作手机。\n• 点亮主屏：按手机任意【音量键】或双击手机屏幕。");
        } else {
            btnToggle.setText("开启息屏 (黑屏)");
            tvStatus.setText("状态：已关闭\n\n• 点击按钮后手机屏幕将变黑，但触摸仍可使用。\n• 适用于连接 AR 眼镜投屏时的省电与防误触。");
        }
    }

    // 执行 Root 命令行
    private static boolean runRootCmd(String cmd) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }

    private static String runRootCmdWithOutput(String cmd) {
        Process process = null;
        DataOutputStream os = null;
        BufferedReader stdout = null;
        BufferedReader stderr = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();

            String line;
            while ((line = stdout.readLine()) != null) {
                output.append(line).append('\n');
            }
            while ((line = stderr.readLine()) != null) {
                output.append(line).append('\n');
            }
            process.waitFor();
        } catch (Exception e) {
            output.append(e.getMessage());
        } finally {
            try {
                if (stdout != null) stdout.close();
                if (stderr != null) stderr.close();
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
        return output.toString();
    }
}
