package cn.gov.xivpn2;

import static org.junit.Assert.assertEquals;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.JsonParser;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import javax.net.ssl.X509TrustManager;

import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.service.sharelink.BadShareLinkException;
import cn.gov.xivpn2.service.sharelink.MarshalProxyException;
import cn.gov.xivpn2.service.sharelink.ShareLinkRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ShareLinkTest {

    private final static String TAG = "ShareLinkTest";

    @Test
    public void testMarshal() throws IOException, MarshalProxyException, BadShareLinkException {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .sslSocketFactory(Utils.trustAllSslSocketFactory, ((X509TrustManager) Utils.trustAllCerts[0]))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        // add subscription
        String subscription = Secret.SUBSCRIPTION_URL;
        Response response1 = httpClient.newCall(new Request.Builder().url(subscription).build()).execute();
        SubscriptionWork.parse(response1.body().string(), "unittest");
        response1.close();

        // list all proxies
        for (Proxy proxy : AppDatabase.getInstance().proxyDao().findAll()) {

            if (!proxy.subscription.equals("unittest")) {
                continue;
            }

            Log.i(TAG, "testing " + proxy.label + " @ " + proxy.subscription);

            String marshalled = ShareLinkRegistry.marshal(proxy);
            Proxy parsed = ShareLinkRegistry.parse(marshalled);

            Log.d(TAG, "parsed " + parsed.config);
            Log.d(TAG, "original " + proxy.config);

            assertEquals(JsonParser.parseString(parsed.config), JsonParser.parseString(proxy.config));
        }
    }

}
