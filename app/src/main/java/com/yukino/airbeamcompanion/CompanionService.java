package com.yukino.airbeamcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.DataOutputStream;

public class CompanionService extends Service {
    public static boolean isRunning = false;
    private static final String CHANNEL_ID = "AirBeamCompanionChannel";
    private static final int NOTIFICATION_ID = 1001;

    private PowerManager.WakeLock wakeLock;
    private Thread monitorThread;
    private volatile boolean isStopped = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        createNotificationChannel();

        // 1. 获取 WakeLock 防止手机在黑屏时 CPU 进入深睡眠休眠
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AirBeamCompanion::WakeLock");
            wakeLock.acquire();
        }

        // 2. 启动前台服务
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AirBeam 息屏助手已启用")
                .setContentText("屏幕已熄灭但触控仍保留，按【音量键】或点击此通知可恢复屏幕。")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // 3. 开启后台线程执行 Root 控制与音量键监听
        startMonitor();
    }

    private void startMonitor() {
        monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // (1) 将亮度写入 0
                runRootCmd("echo 0 > /sys/class/backlight/panel0-backlight/brightness");

                // (2) 阻塞监听音量键事件 (/dev/input/event11)
                // 一旦用户按下音量键，getevent 将会返回输出，从而跳过此行
                runRootCmd("getevent -c 1 /dev/input/event11");

                if (!isStopped) {
                    // (3) 退出黑屏，恢复正常亮度 (200)
                    runRootCmd("echo 200 > /sys/class/backlight/panel0-backlight/brightness");
                    
                    // 在主线程结束自己
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            stopSelf();
                        }
                    });
                }
            }
        });
        monitorThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        isStopped = true;
        isRunning = false;
        
        // 释放锁并强制恢复亮度，确保安全
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // 强行恢复亮度，防止手机黑屏被卡住
        new Thread(new Runnable() {
            @Override
            public void run() {
                runRootCmd("echo 200 > /sys/class/backlight/panel0-backlight/brightness");
            }
        }).start();

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
                    "AirBeam Companion Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
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
}
