package com.uzair.securecap;

import android.content.Intent;
import android.app.PendingIntent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class CaptureTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Bridge.session ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(Bridge.session ? "Stop SecureCap" : "SecureCap");
            tile.setSubtitle(Bridge.session ? MediaFiles.duration(Bridge.status.getLong("elapsed")) : "Record screen");
            tile.updateTile();
        }
    }
    @Override public void onClick() {
        if (Bridge.session) { CaptureService.command(this, CaptureService.STOP); onStartListening(); return; }
        unlockAndRun(() -> {
            Intent intent = new Intent(this, MainActivity.class).putExtra("tileStart", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 8, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else startActivityAndCollapse(intent);
        });
    }
}
