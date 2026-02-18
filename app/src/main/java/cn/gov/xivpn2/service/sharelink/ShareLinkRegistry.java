package cn.gov.xivpn2.service.sharelink;

import android.net.Uri;

import java.net.URI;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.service.SubscriptionWork;


public class ShareLinkRegistry {

    private static final LegacyVmessShareLinkParser legacyVmess = new LegacyVmessShareLinkParser();
    private static final VMessShareLinkParser vmess = new VMessShareLinkParser();
    private static final VLessShareLinkParser vless = new VLessShareLinkParser();


    /**
     * Parse a share link URI into a Proxy.
     *
     * @param uri the full share link (e.g. "vless://uuid@host:port?params#label")
     * @return the parsed Proxy, or null if no parser handles this scheme
     * @throws Exception if parsing fails
     */
    public static Proxy parse(String uri) throws Exception {

        // fix illegal url from v2rayng
        uri = uri.replace(" ", "%20").replace("|", "%7C");

        if (uri.startsWith("vmess://")) {
            if (uri.lastIndexOf(':') > 6) {
                return vmess.parse(uri);
            } else {
                return legacyVmess.parse(uri);
            }
        } else if (uri.startsWith("vless://")) {
            return vless.parse(uri);
        }

        throw new Exception("share link not supported");
    }

    /**
     * Marshal a Proxy into a share link string.
     *
     * @param proxy the proxy to marshal
     * @return the share link string
     * @throws MarshalProxyException if the proxy's protocol is not supported
     *                                                 or contains unsupported options
     */
    public static String marshal(Proxy proxy) throws MarshalProxyException {
        if ("vmess".equals(proxy.protocol)) {
            return vmess.marshal(proxy);
        } else if ("vless".equals(proxy.protocol)) {
            return vless.marshal(proxy);
        }
        throw new MarshalProxyException("unsupported proxy protocol: " + proxy.protocol);
    }

}
