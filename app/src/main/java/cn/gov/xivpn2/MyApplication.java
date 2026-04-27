package cn.gov.xivpn2;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.impl.Migration_16_17;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.Executors;

import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.ui.CrashActivity;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // crash
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("CRASH", "uncaught exception handler", throwable);

            String exceptionAsString = Utils.getExceptionString(throwable);

            Intent intent = new Intent(this, CrashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("EXCEPTION", exceptionAsString);
            startActivity(intent);
            System.exit(1);
        });

        // notification
        NotificationChannel channelVpnService = new NotificationChannel("XiVPNService", "Xi VPN Service", NotificationManager.IMPORTANCE_DEFAULT);
        channelVpnService.setSound(null, null);
        channelVpnService.setDescription("Xi VPN Background Service");
        NotificationChannel channelSubscriptions = new NotificationChannel("XiVPNSubscriptions", "Xi VPN Subscription Update", NotificationManager.IMPORTANCE_DEFAULT);
        channelSubscriptions.setSound(null, null);
        channelSubscriptions.setDescription("Xi VPN Subscription Update Worker");

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channelVpnService);
        notificationManager.createNotificationChannel(channelSubscriptions);

        // database
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "xivpn")
                .setQueryCallback((s, list) -> {
                    // Log.d("ROOM", s + list);
                }, Executors.newSingleThreadExecutor())
                .allowMainThreadQueries()
                .addMigrations(new Migration(1, 2) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE subscription ADD COLUMN type TEXT;");
                        db.execSQL("UPDATE subscription SET type = 'v2rayng';");
                    }
                }, new Migration(2, 3) {
                    @Override
                    public void migrate(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("ALTER TABLE subscription ADD COLUMN ignoreRoutingDns INTEGER NOT NULL DEFAULT 0;");
                    }
                })
                .build();
        AppDatabase.setInstance(db);

        db.proxyDao().addFreedom();
        db.proxyDao().addBlackhole();
        db.proxyDao().addDNSOutbound();

        // background work
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.cancelUniqueWork("SUBSCRIPTION");
        workManager.enqueueUniquePeriodicWork(
                "SUBSCRIPTION",
                ExistingPeriodicWorkPolicy.UPDATE,
                new PeriodicWorkRequest.Builder(SubscriptionWork.class, Duration.ofDays(1))
                        .build()
        );


        // copy assets
        writeAsset("default_rules.json", new File(getFilesDir(), "rules.json"));
        writeAsset("default_dns.json", new File(getFilesDir(), "dns.json"));
    }

    private void writeAsset(String asset, File out) {
        Log.i(TAG, "write assets " + asset + " => " + out.getAbsolutePath());
        if (!out.exists()) {
            Log.i(TAG, "copy " + asset + " => " + out.getAbsolutePath());
            try {
                AssetManager assets = getAssets();
                InputStream inputStream = assets.open(asset);
                FileUtils.copyToFile(inputStream, out);
                inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "write asset", e);
            }
        }
    }

}
