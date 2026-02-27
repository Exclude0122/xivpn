package cn.gov.xivpn2;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.ui.MainActivity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.action.ViewActions.*;

import javax.net.ssl.X509TrustManager;

@RunWith(AndroidJUnit4.class)
public class ProxyTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    private final static String TAG = "ProxyTest";

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

        // start vpn
        onView(withId(R.id.vpn_switch)).perform(click());
        Thread.sleep(1000);
        onView(withId(R.id.vpn_switch)).perform(click());

        // add subscription
        String subscription = Secret.SUBSCRIPTION_URL;
        Response response1 = httpClient.newCall(new Request.Builder().url(subscription).build()).execute();
        SubscriptionWork.parse(response1.body().string(), "unittest");
        response1.close();

        // list all proxies
        for (Proxy proxy : AppDatabase.getInstance().proxyDao().findAll()) {
            // set default proxy
            Rules.setCatchAll(sp, proxy.label, proxy.subscription);
            // reload config
            XiVPNService.markConfigStale(appContext);

            Log.i(TAG, "testing " + proxy.label + " @ " + proxy.subscription);

            if (proxy.label.equals("Block") || proxy.label.equals("Built-in DNS Server")) {
                continue;
            }

            Thread.sleep(500);

            // test connection
            Response response = httpClient.newCall(new Request.Builder().url("https://myip.wtf/text").build()).execute();
            Log.i(TAG, "ip address " +  response.body().string());
            response.close();

            assertEquals(200, response.code());

        }
    }
}
