package com.yukino.airbeamcompanion;

import android.app.Presentation;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

public class DemoPresentation extends Presentation {
    private static final String TAG = "DemoPresentation";

    private TextView tvCoordinates;
    private TextureView mirrorTextureView;
    private Surface mirrorSurface;
    private VirtualDisplay mirrorVirtualDisplay;
    private boolean mirrorRequested = false;

    public DemoPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.presentation_layout);
        tvCoordinates = findViewById(R.id.tv_coordinates);
        mirrorTextureView = findViewById(R.id.mirror_texture_view);
        if (mirrorTextureView != null) {
            mirrorTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    Log.i(TAG, "SurfaceTexture available: " + width + "x" + height);
                    if (mirrorRequested) {
                        setupVirtualDisplay(width, height);
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    Log.i(TAG, "SurfaceTexture size changed: " + width + "x" + height);
                    if (mirrorRequested) {
                        setupVirtualDisplay(width, height);
                    }
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    Log.i(TAG, "SurfaceTexture destroyed");
                    releaseSurface();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    // 提供给 MainActivity 调用，用于实时更新眼镜画面中的坐标值
    public void updateCoordinates(float x, float y) {
        if (tvCoordinates != null) {
            tvCoordinates.setText(String.format(Locale.getDefault(), "X: %.1f , Y: %.1f", x, y));
        }
    }

    public void startMirror() {
        if (mirrorTextureView != null) {
            mirrorRequested = true;
            mirrorTextureView.setVisibility(View.VISIBLE);
            mirrorTextureView.post(new Runnable() {
                @Override
                public void run() {
                    int width = mirrorTextureView.getWidth();
                    int height = mirrorTextureView.getHeight();
                    Log.i(TAG, "startMirror layout ready: available=" + mirrorTextureView.isAvailable()
                            + " size=" + width + "x" + height);
                    if (mirrorTextureView.isAvailable() && width > 0 && height > 0) {
                        setupVirtualDisplay(width, height);
                    }
                }
            });
        }
    }

    private void setupVirtualDisplay(int width, int height) {
        if (mirrorTextureView == null) return;
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "setupVirtualDisplay ignored: invalid size " + width + "x" + height);
            return;
        }
        
        releaseSurface();

        SurfaceTexture texture = mirrorTextureView.getSurfaceTexture();
        if (texture == null) {
            Log.w(TAG, "setupVirtualDisplay ignored: SurfaceTexture is null");
            return;
        }
        
        texture.setDefaultBufferSize(width, height);
        mirrorSurface = new Surface(texture);

        int densityDpi = getContext().getResources().getDisplayMetrics().densityDpi;
        // 调用 MirrorService 前台服务接口关联并渲染虚拟屏幕
        mirrorVirtualDisplay = MirrorService.startVirtualDisplay(mirrorSurface, width, height, densityDpi);
        Log.i(TAG, "VirtualDisplay start result=" + (mirrorVirtualDisplay != null));
    }

    private void releaseSurface() {
        MirrorService.stopVirtualDisplay(mirrorVirtualDisplay);
        mirrorVirtualDisplay = null;
        if (mirrorSurface != null) {
            mirrorSurface.release();
            mirrorSurface = null;
        }
    }

    public void stopMirror() {
        mirrorRequested = false;
        releaseSurface();
        if (mirrorTextureView != null) {
            mirrorTextureView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        stopMirror();
        super.onDetachedFromWindow();
    }
}
