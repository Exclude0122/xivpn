package cn.gov.xivpn2.service;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.service.quicksettings.PendingIntentActivityWrapper;
import androidx.core.service.quicksettings.TileServiceCompat;

import cn.gov.xivpn2.ui.MainActivity;

public class XiVPNTileService extends TileService implements XiVPNService.VPNStateListener {

    private static final String TAG = "XiVPNTileService";
    private XiVPNService.XiVPNBinder binder;
    private ServiceConnection serviceConnection;

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onClick() {
        if (binder != null) {
            // start vpn
            if (binder.getState() != XiVPNService.VPNState.CONNECTED) {
                Intent intent = XiVPNService.prepare(this);
                if (intent != null) {
                    TileServiceCompat.startActivityAndCollapse(
                            this,
                            new PendingIntentActivityWrapper(this, 30, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT, false)
                    );
                    return;
                }

                Intent intent2 = new Intent(this, XiVPNService.class);
                intent2.setAction("cn.gov.xivpn2.START");
                intent2.putExtra("always-on", false);
                startForegroundService(intent2);
            } else {
                Intent intent2 = new Intent(this, XiVPNService.class);
                intent2.setAction("cn.gov.xivpn2.STOP");
                intent2.putExtra("always-on", false);
                startService(intent2);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "service connected");
                binder = (XiVPNService.XiVPNBinder) service;
                binder.addListener(XiVPNTileService.this);
                XiVPNTileService.this.setState(binder.getState() == XiVPNService.VPNState.CONNECTED);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "service disconnected");
                binder = null;
            }
        };
    }

    @Override
    public void onStartListening() {
        Log.d(TAG, "on start listening");
        bindService(new Intent(this, XiVPNService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStopListening() {
        Log.d(TAG, "on stop listener");
        if (binder != null) binder.removeListener(this);
        unbindService(serviceConnection);
    }

    private void setState(boolean active) {
        Tile tile = getQsTile();
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStateChanged(XiVPNService.VPNState state) {
        Log.d(TAG, "on state change " + state.toString());
        setState(state == XiVPNService.VPNState.CONNECTED);
    }

    @Override
    public void onMessage(String msg) {

    }
}
