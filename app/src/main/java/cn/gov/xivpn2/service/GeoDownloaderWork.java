package cn.gov.xivpn2.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import cn.gov.xivpn2.NotificationID;
import cn.gov.xivpn2.R;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GeoDownloaderWork extends Worker {

    private final static String TAG = "GeoDownloadWork";


    public GeoDownloaderWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "do work");

        // start foreground service

        Notification foregroundNotification = new NotificationCompat.Builder(getApplicationContext(), "XiVPNService")
                .setSilent(true)
                .setProgress(100, 0, true)
                .setContentTitle(getApplicationContext().getString(R.string.downloading_geo_assets))
                .setOngoing(true)
                .setSmallIcon(R.drawable.baseline_refresh_24)
                .build();

        int id = NotificationID.getID();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setForegroundAsync(new ForegroundInfo(id, foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE));
        } else {
            setForegroundAsync(new ForegroundInfo(id, foregroundNotification));
        }

        SharedPreferences sp = getApplicationContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
        String geoip = sp.getString("GEOIP", "v2fly");
        String geosite = sp.getString("GEOSITE", "v2fly");

        String geoipUrl = "";
        String geositeUrl = "";

        switch (geoip) {
            case "v2fly":
                geoipUrl = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat";
                break;
            case "v2fly_cn":
                geoipUrl = "https://github.com/v2fly/geoip/releases/latest/download/geoip-only-cn-private.dat";
                break;
            case "ipinfo":
                geoipUrl = "https://github.com/Exclude0122/geoip/releases/latest/download/geoip.dat";
                break;
            case "ipinfo_cn":
                geoipUrl = "https://github.com/Exclude0122/geoip/releases/latest/download/geoip-only-cn-private.dat";
                break;
            case "ipinfo_ru":
                geoipUrl = "https://github.com/Exclude0122/geoip/releases/latest/download/geoip-only-ru-private.dat";
                break;
            case "ipinfo_ir":
                geoipUrl = "https://github.com/Exclude0122/geoip/releases/latest/download/geoip-only-ir-private.dat";
                break;
            case "custom":
                geoipUrl = sp.getString("GEOIP_CUSTOM", "");
                break;
        }

        switch (geosite) {
            case "v2fly":
                geositeUrl = "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat";
                break;
            case "custom":
                geositeUrl = sp.getString("GEOSITE_CUSTOM", "");
                break;
        }

        Log.i(TAG, "ip " + geoipUrl);
        Log.i(TAG, "site " + geositeUrl);

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();

        try {

            download(id, client, geoipUrl, new File(getApplicationContext().getFilesDir(), "geoip.dat"));
            download(id, client, geositeUrl, new File(getApplicationContext().getFilesDir(), "geosite.dat"));

        } catch (Exception e) {
            Log.e(TAG, "download error", e);

            // post error message
            Notification notification = new NotificationCompat.Builder(getApplicationContext(), "XiVPNSubscriptions")
                    .setSilent(true)
                    .setContentTitle(getApplicationContext().getString(R.string.downloading_error))
                    .setContentText(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .setSmallIcon(R.drawable.baseline_error_24)
                    .build();
            getApplicationContext().getSystemService(NotificationManager.class).notify(NotificationID.getID(), notification);

            return Result.failure();
        }

        return Result.success();
    }

    private void download(int id, OkHttpClient client, String url, File file) throws IOException {
        Log.i(TAG, "download " + url + " => " + file.getAbsolutePath());

        Request request = new Request.Builder().url(url).build();

        Response response = client.newCall(request).execute();

        if (response.code() != 200) {
            response.close();
            throw new IOException("unexpected status code: " + response.code());
        }

        if (response.body() == null) {
            response.close();
            throw new NullPointerException("null body");
        }

        int totalLength = -1;
        String contentLengthHeader = response.header("Content-Length");
        if (contentLengthHeader != null) {
            totalLength = Integer.parseInt(contentLengthHeader);
        }

        OutputStream outputStream = FileUtils.newOutputStream(file, false);
        byte[] buffer = new byte[4096];

        long lastUpdateProgress = 0;
        int length = 0;
        while (true) {
            InputStream inputStream = response.body().byteStream();
            int n = inputStream.read(buffer);
            if (n <= 0) break;

            length += n;
            if (System.currentTimeMillis() - lastUpdateProgress > 500) {
                int progress = (int) (Math.floor((double) length / totalLength * 100));
                updateProgress(id, "Downloading " + file.getName() + "...", progress, totalLength == -1);
                lastUpdateProgress = System.currentTimeMillis();
            }

            outputStream.write(buffer, 0, n);
            outputStream.flush();
        }

        outputStream.close();

        response.close();
    }

    private void updateProgress(int id, String content, int progress, boolean indeterminate) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "XiVPNSubscriptions")
                .setSilent(true)
                .setProgress(100, progress, indeterminate)
                .setContentTitle(getApplicationContext().getString(R.string.downloading_geo_assets))
                .setOngoing(true)
                .setSmallIcon(R.drawable.baseline_refresh_24);

        if (content != null) {
            builder.setContentText(content);
        }

        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.notify(id, builder.build());
    }

}
