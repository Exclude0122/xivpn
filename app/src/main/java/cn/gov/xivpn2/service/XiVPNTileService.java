package cn.gov.xivpn2.service;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.core.service.quicksettings.PendingIntentActivityWrapper;
import androidx.core.service.quicksettings.TileServiceCompat;

import cn.gov.xivpn2.aidl.IVPNListener;
import cn.gov.xivpn2.aidl.IVPNService;
import cn.gov.xivpn2.ui.MainActivity;

public class XiVPNTileService extends TileService {

    private static final String TAG = "XiVPNTileService";
    private IVPNService binder;
    private ServiceConnection serviceConnection;

    private IVPNListener.Stub vpnListener;

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
            try {
                // start vpn
                if (VPNStatus.valueOf(binder.getStatus()).equals(VPNStatus.DISCONNECTED)) {
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
                }

                // stop vpn
                if (VPNStatus.valueOf(binder.getStatus()).equals(VPNStatus.CONNECTED)) {
                    Intent intent2 = new Intent(this, XiVPNService.class);
                    intent2.setAction("cn.gov.xivpn2.STOP");
                    intent2.putExtra("always-on", false);
                    startService(intent2);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "on click", e);
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
                binder = IVPNService.Stub.asInterface(service);

                try {
                    binder.addListener(XiVPNTileService.this.vpnListener);
                    XiVPNTileService.this.setState(VPNStatus.valueOf(binder.getStatus()).equals(VPNStatus.CONNECTED));
                } catch (RemoteException e) {
                    Log.e(TAG, "on service connected", e);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "service disconnected");
                binder = null;
            }
        };

        vpnListener = new IVPNListener.Stub() {

            @Override
            public void onStatusChanged(String status) throws RemoteException {
                Log.d(TAG, "on status change " +  status);
                setState(VPNStatus.valueOf(status).equals(VPNStatus.CONNECTED));
            }

            @Override
            public void onMessage(String msg) throws RemoteException {

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
        try {
            binder.removeListener(XiVPNTileService.this.vpnListener);
        } catch (RemoteException e) {
            Log.e(TAG, "on stop listening", e);
        }
        unbindService(serviceConnection);
    }

    private void setState(boolean active) {
        Tile tile = getQsTile();
        tile.setState(active ? Tile.STATE_ACTIVE: Tile.STATE_INACTIVE);
        tile.updateTile();
    }

}
