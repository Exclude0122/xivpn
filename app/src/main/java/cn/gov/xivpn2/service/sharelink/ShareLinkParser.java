package cn.gov.xivpn2.service.sharelink;

import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.service.SubscriptionWork;

/**
 * Common interface for all share link parsers.
 * <p>
 * Each implementation handles a specific protocol scheme (e.g. "vmess", "vless", "shadowsocks").
 */
public interface ShareLinkParser {

    /**
     * Parse a share link URI into a Proxy object.
     *
     * @param uri the full share link URI (e.g. "vless://uuid@host:port?params#label")
     * @return a Proxy with protocol, label, and config fields populated
     * @throws BadShareLinkException if the URI is malformed or contains unsupported options
     */
    Proxy parse(String uri) throws BadShareLinkException;

    /**
     * Marshal a Proxy back into a share link string.
     *
     * @param proxy the proxy to marshal
     * @return the share link URI string
     * @throws MarshalProxyException if the proxy contains unsupported options
     */
    String marshal(Proxy proxy) throws MarshalProxyException;
}
