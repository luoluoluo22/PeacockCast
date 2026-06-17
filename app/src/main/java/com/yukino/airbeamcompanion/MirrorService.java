package com.yukino.airbeamcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class MirrorService extends Service {
    private static final String TAG = "MirrorService";
    private static final String CHANNEL_ID = "MirrorServiceChannel";
    private static final int NOTIFICATION_ID = 1002;

    public static MediaProjection mediaProjection;
    private static final List<VirtualDisplay> virtualDisplays = new ArrayList<>();
    
    private MediaProjectionManager projectionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaProjection.Callback mediaProjectionCallback;
    private int resultCode;
    private Intent resultData;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("START_MIRROR".equals(action)) {
                resultCode = intent.getIntExtra("resultCode", -1);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resultData = intent.getParcelableExtra("resultData", Intent.class);
                } else {
                    resultData = intent.getParcelableExtra("resultData");
                }
                Log.i(TAG, "START_MIRROR received: resultCode=" + resultCode + ", hasResultData=" + (resultData != null));
                
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("AR 屏幕镜像服务已启用")
                        .setContentText("正在将手机屏幕同步至 AR 独立空间内。")
                        .setSmallIcon(android.R.drawable.ic_menu_camera)
                        .setOngoing(true)
                        .build();
                
                startForeground(NOTIFICATION_ID, notification);
                
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    if (mediaProjection != null) {
                        mediaProjection.stop();
                    }
                    mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
                    Log.i(TAG, "MediaProjection created");
                    mediaProjectionCallback = new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            Log.i(TAG, "MediaProjection stopped");
                            stopVirtualDisplay();
                            mediaProjection = null;
                            stopSelf();
                        }
                    };
                    mediaProjection.registerCallback(mediaProjectionCallback, mainHandler);
                    
                    // 延迟 100ms 回调以确保系统在 AMS 中已彻底同步该服务的 MediaProjection 状态，避免底层权限异常
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (MainActivity.instance != null) {
                                MainActivity.instance.onMirrorServiceReady();
                            }
                        }
                    }, 100);
                } else {
                    Log.e(TAG, "START_MIRROR missing MediaProjection result data");
                    stopSelf();
                }
            } else if ("STOP_MIRROR".equals(action)) {
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }


    public static VirtualDisplay startVirtualDisplay(Surface surface, int width, int height, int dpi) {
        if (mediaProjection == null) {
            Log.w(TAG, "startVirtualDisplay ignored: MediaProjection is null");
            return null;
        }
        if (surface == null || !surface.isValid()) {
            Log.w(TAG, "startVirtualDisplay ignored: Surface is invalid");
            return null;
        }
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "startVirtualDisplay ignored: invalid size " + width + "x" + height);
            return null;
        }

        try {
            Log.i(TAG, "Creating VirtualDisplay: " + width + "x" + height + " dpi=" + dpi);
            VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                    "MirrorDisplay",
                    width,
                    height,
                    dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
            );
            Log.i(TAG, "VirtualDisplay created");
            if (virtualDisplay != null) {
                synchronized (virtualDisplays) {
                    virtualDisplays.add(virtualDisplay);
                }
            }
            return virtualDisplay;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create VirtualDisplay", e);
            e.printStackTrace();
            return null;
        }
    }

    public static void stopVirtualDisplay(VirtualDisplay virtualDisplay) {
        if (virtualDisplay == null) return;

        synchronized (virtualDisplays) {
            virtualDisplays.remove(virtualDisplay);
        }
        Log.i(TAG, "Releasing VirtualDisplay");
        virtualDisplay.release();
    }

    public static void stopAllVirtualDisplays() {
        synchronized (virtualDisplays) {
            for (VirtualDisplay virtualDisplay : new ArrayList<>(virtualDisplays)) {
                Log.i(TAG, "Releasing VirtualDisplay");
                virtualDisplay.release();
            }
            virtualDisplays.clear();
        }
    }

    public static void stopVirtualDisplay() {
        stopAllVirtualDisplays();
    }

    public static boolean hasMediaProjection() {
        return mediaProjection != null;
    }

    public static void stopProjection() {
        stopAllVirtualDisplays();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    public static void releaseVirtualDisplay(VirtualDisplay virtualDisplay) {
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }

    @Override
    public void onDestroy() {
        stopVirtualDisplay();
        if (mediaProjection != null) {
            if (mediaProjectionCallback != null) {
                mediaProjection.unregisterCallback(mediaProjectionCallback);
                mediaProjectionCallback = null;
            }
            mediaProjection.stop();
            mediaProjection = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mirror Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
