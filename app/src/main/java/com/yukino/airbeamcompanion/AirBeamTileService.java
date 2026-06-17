package com.yukino.airbeamcompanion;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.core.content.ContextCompat;

public class AirBeamTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent serviceIntent = new Intent(this, CompanionService.class);
        if (CompanionService.isRunning) {
            stopService(serviceIntent);
        } else {
            ContextCompat.startForegroundService(this, serviceIntent);
        }
        
        // 延时更新Tile状态
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                updateTile();
            }
        }, 300);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile != null) {
            if (CompanionService.isRunning) {
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("AR息屏 (开启)");
            } else {
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel("AR息屏 (关闭)");
            }
            tile.updateTile();
        }
    }
}
