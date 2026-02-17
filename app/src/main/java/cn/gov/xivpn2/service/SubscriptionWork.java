package cn.gov.xivpn2.service;

import static android.content.Context.MODE_PRIVATE;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.net.ssl.X509TrustManager;

import cn.gov.xivpn2.NotificationID;
import cn.gov.xivpn2.R;
import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.database.Subscription;
import cn.gov.xivpn2.service.sharelink.MarshalProxyException;
import cn.gov.xivpn2.service.sharelink.ShareLinkRegistry;
import cn.gov.xivpn2.ui.CrashLogActivity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SubscriptionWork extends Worker {
    private static final String TAG = "SubscriptionWorker";

    public SubscriptionWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "doWork");

        // start foreground service

        Notification foregroundNotification = new Notification.Builder(getApplicationContext(), "XiVPNService")
                .setContentText(getApplicationContext().getString(R.string.subscription_updating))
                .setOngoing(true)
                .setSmallIcon(R.drawable.baseline_refresh_24)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setForegroundAsync(new ForegroundInfo(NotificationID.getID(), foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE));
        } else {
            setForegroundAsync(new ForegroundInfo(NotificationID.getID(), foregroundNotification));
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e(TAG, "sleep", e);
            return Result.success();
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("subscription_no_verify", false)) {
            Log.w(TAG, "subscription no verify");
            builder.sslSocketFactory(Utils.trustAllSslSocketFactory, ((X509TrustManager) Utils.trustAllCerts[0]));
            builder.hostnameVerifier((hostname, session) -> true);
        }

        OkHttpClient client = builder.build();
        for (Subscription subscription : AppDatabase.getInstance().subscriptionDao().findAll()) {

            // update subscription

            Log.i(TAG, "begin update: " + subscription.label + ", " + subscription.url);

            Response response = null;
            String body = null;
            int statusCode = -1;
            try {

                Request request = new Request.Builder()
                        .url(subscription.url)
                        .build();

                response = client.newCall(request).execute();
    

                // delete old proxies
                AppDatabase.getInstance().proxyDao().deleteBySubscription(subscription.label);

                // parse subscription and add proxies
                statusCode = response.code();
                body = response.body().string();
                parse(body, subscription.label);

                Notification notification = new Notification.Builder(getApplicationContext(), "XiVPNSubscriptions")
                        .setContentTitle(getApplicationContext().getString(R.string.subscription_updated) + subscription.label)
                        .setSmallIcon(R.drawable.outline_info_24)
                        .build();
                getApplicationContext().getSystemService(NotificationManager.class).notify(NotificationID.getID(), notification);

            } catch (Exception e) {

                Log.e(TAG, "update " + subscription.label, e);

                // save error message to a file
                String fileName = "subscription_" + UUID.randomUUID() + ".txt";
                File file = new File(getApplicationContext().getCacheDir(), fileName);

                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Could not update subscription: ").append(subscription.label).append("\n\n");

                    sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n\n");

                    if (statusCode > 0) {
                        sb.append("Response status code: ").append(statusCode).append("\n\n");
                        if (body != null) {
                            sb.append("Response Body:\n").append(body).append("\n\n");
                        }
                    }
                    FileOutputStream outputStream = new FileOutputStream(file);
                    outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    outputStream.close();
                } catch (IOException e2) {
                    Log.e(TAG, "write crash log", e2);
                }

                Intent intent = new Intent(getApplicationContext(), CrashLogActivity.class);
                intent.putExtra("FILE", fileName);

                // post error message
                Notification notification = new Notification.Builder(getApplicationContext(), "XiVPNSubscriptions")
                        .setContentTitle(getApplicationContext().getString(R.string.subscription_error) + subscription.label)
                        .setContentText(getApplicationContext().getString(R.string.click_for_details))
                        .setSmallIcon(R.drawable.baseline_error_24)
                        .setContentIntent(PendingIntent.getActivity(getApplicationContext(), NotificationID.getID(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                        .build();


                getApplicationContext().getSystemService(NotificationManager.class).notify(NotificationID.getID(), notification);

            } finally {
                if (response != null) {
                    try {
                        response.close();
                    } catch (Exception e) {
                        Log.e(TAG, "close response", e);
                    }
                }
            }

        }

        try {
            Rules.resetDeletedProxies(getApplicationContext().getSharedPreferences("XIVPN", MODE_PRIVATE), getApplicationContext().getFilesDir());
        } catch (IOException e) {
            Log.e(TAG, "reset deleted proxies", e);
        }

        XiVPNService.markConfigStale(getApplicationContext());

        Log.i(TAG, "doWork finish");
        return Result.success();
    }

    /**
     * Parse subscription text and add proxies
     *
     * @param text base64 encoded, one line per proxy
     */
    private void parse(String text, String label) {
        // decode base64
        String textDecoded = new String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8);

        String[] lines = textDecoded.split("\\r?\\n");

        for (String line : lines) {
            line = line.replace(" ", "%20").replace("|", "%7c");
            Log.i(TAG, "parse " + line);

            parseLine(line, label);
        }
    }

    public static boolean parseLine(String line, String subscription) {
        Proxy proxy = null;

        try {
            proxy = ShareLinkRegistry.parse(line);
        } catch (Exception e) {
            Log.e(TAG, "parse " + subscription + " " + line, e);
            return false;
        }

        if (proxy == null) return false;

        proxy.subscription = subscription;

        int n = 2;
        String baseLabel = proxy.label;

        if (proxy.label.matches("[\\s\\S]* \\d+$")) {
            int splitAt = proxy.label.lastIndexOf(" ");
            n = Integer.parseInt(proxy.label.substring(splitAt + 1)) + 1;
            baseLabel = proxy.label.substring(0, splitAt);
        }

        while (AppDatabase.getInstance().proxyDao().find(proxy.label, proxy.subscription) != null) {
            // add number to label if already exists
            proxy.label = baseLabel + " " + n;
            n++;
        }
        AppDatabase.getInstance().proxyDao().add(proxy);

        return true;
    }



    public static String marshalProxy(Proxy proxy) throws MarshalProxyException {
        return ShareLinkRegistry.marshal(proxy);
    }
}
