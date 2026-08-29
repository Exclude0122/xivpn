package cn.gov.xivpn2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.service.XiVPNService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.X509TrustManager;

@RunWith(AndroidJUnit4.class)
public class ProxyTest {

    private final static String TAG = "ProxyTest";

    private static final long CONSENT_TIMEOUT_MS = 60_000;
    private static final long CONNECTED_TIMEOUT_MS = 30_000;
    private static final long POLL_INTERVAL_MS = 500;

    /**
     * Import and test all proxies from Secret.SUBSCRIPTION_URL
     */
    @Test
    public void testAllOutbounds() throws IOException, InterruptedException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences sp = appContext.getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(Utils.trustAllSslSocketFactory, ((X509TrustManager) Utils.trustAllCerts[0]))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        try {
            // start vpn

            boolean finished = false;
            Intent consent = XiVPNService.prepare(appContext);
            if (consent != null) {// launch the consent dialog; it needs an activity task
                consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(consent);
                long deadline = System.currentTimeMillis() + CONSENT_TIMEOUT_MS;
                while (System.currentTimeMillis() < deadline) {
                    if (XiVPNService.prepare(appContext) == null) {
                        Log.i(TAG, "vpn consent granted");
                        finished = true;
                        break;
                    }
                    Thread.sleep(POLL_INTERVAL_MS);
                }
                if (!finished) {
                    fail("VPN consent was not granted within " + (CONSENT_TIMEOUT_MS / 1000) + "s. " +
                            "Accept the system VPN consent dialog on the device, " +
                            "or pre-grant it with: adb shell appops set " + appContext.getPackageName() + " ACTIVATE_VPN allow");
                }
            }

            Intent intent = new Intent(appContext, XiVPNService.class);
            intent.setAction("cn.gov.xivpn2.START");
            intent.putExtra("always-on", false);
            appContext.startForegroundService(intent);
            awaitState(appContext, XiVPNService.VPNState.CONNECTED, CONNECTED_TIMEOUT_MS);

            // add subscription
            String subscription = Secret.SUBSCRIPTION_URL;
            Response response1 = httpClient.newCall(new Request.Builder().url(subscription).build()).execute();
            SubscriptionWork.parseV2rayng(response1.body().string(), "unittest");
            response1.close();

            // list all proxies
            for (Proxy proxy : AppDatabase.getInstance().proxyDao().findAll()) {
                if (proxy.label.equals("Block") || proxy.label.equals("Built-in DNS Server")) {
                    continue;
                }

                // set default proxy
                Rules.setCatchAll(sp, proxy.label, proxy.subscription);
                // reload config
                XiVPNService.markConfigStale(appContext);

                Log.i(TAG, "testing " + proxy.label + " @ " + proxy.subscription);

                Thread.sleep(500);

                // test connection
                Response response = httpClient.newCall(new Request.Builder().url("https://myip.wtf/text").build()).execute();
                Log.i(TAG, "ip address " + response.body().string());
                response.close();

                assertEquals(200, response.code());
            }
        } finally {
            // stop vpn
            try {
                Intent intent = new Intent(appContext, XiVPNService.class);
                intent.setAction("cn.gov.xivpn2.STOP");
                intent.putExtra("always-on", false);
                appContext.startService(intent);
                awaitState(appContext, XiVPNService.VPNState.DISCONNECTED, CONNECTED_TIMEOUT_MS);
            } catch (Throwable t) {
                Log.e(TAG, "stop vpn", t);
            }
        }
    }

    /**
     * Bind to the vpn service and poll its state until it reaches the expected state.
     */
    private void awaitState(Context context, XiVPNService.VPNState expected, long timeoutMs) throws InterruptedException {
        CountDownLatch connected = new CountDownLatch(1);
        XiVPNService.XiVPNBinder[] binderRef = new XiVPNService.XiVPNBinder[1];

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binderRef[0] = (XiVPNService.XiVPNBinder) service;
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binderRef[0] = null;
            }
        };

        context.bindService(new Intent(context, XiVPNService.class), connection, Context.BIND_AUTO_CREATE);
        try {
            if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("could not bind to " + XiVPNService.class.getSimpleName() + " within " + (timeoutMs / 1000) + "s");
            }

            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                XiVPNService.XiVPNBinder binder = binderRef[0];
                if (binder != null) {
                    XiVPNService.VPNState state = binder.getState();
                    Log.i(TAG, "vpn state " + state.name());
                    if (state == expected) {
                        return;
                    }
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }

            fail("vpn did not reach " + expected.name() + " within " + (timeoutMs / 1000) + "s");
        } finally {
            context.unbindService(connection);
        }
    }
}
