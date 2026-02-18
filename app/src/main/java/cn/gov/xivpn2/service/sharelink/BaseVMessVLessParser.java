package cn.gov.xivpn2.service.sharelink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.xrayconfig.GRPCSettings;
import cn.gov.xivpn2.xrayconfig.HttpUpgradeSettings;
import cn.gov.xivpn2.xrayconfig.KcpSettings;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.RealitySettings;
import cn.gov.xivpn2.xrayconfig.StreamSettings;
import cn.gov.xivpn2.xrayconfig.TLSSettings;
import cn.gov.xivpn2.xrayconfig.WsSettings;
import cn.gov.xivpn2.xrayconfig.XHttpSettings;

/**
 * Abstract base class for VMess and VLESS share link parsers.
 * <p>
 * Handles parsing and marshaling of transport settings (ws, grpc, httpupgrade, xhttp)
 * and security settings (tls, reality) which are shared between the two protocols.
 */
public abstract class BaseVMessVLessParser implements ShareLinkParser {

    // ========================
    // Utility methods
    // ========================

    protected static String quote(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return queryPairs;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx < 0) continue;
            queryPairs.put(
                    URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            );
        }
        return queryPairs;
    }

    protected static String queryFromMap(Map<String, String> queries) {
        StringBuilder s = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : queries.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (first) {
                first = false;
                s.append("?");
            } else {
                s.append("&");
            }
            s.append(quote(entry.getKey()));
            s.append("=");
            s.append(quote(entry.getValue()));
        }
        return s.toString();
    }

    protected static String nullable(String s, String defaultValue) {
        if (defaultValue == null) throw new NullPointerException("defaultValue must not be null");
        return s == null ? defaultValue : s;
    }

    protected static String quoteHost(String host) {
        if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]")) {
            return "[" + host + "]";
        }
        return host;
    }

    /**
     * Throw if any of the given keys are present and non-empty in the query map.
     */
    protected static void rejectUnsupportedParams(Map<String, String> query, String... keys) {
        for (String key : keys) {
            String value = query.get(key);
            if (value != null && !value.isEmpty()) {
                throw new UnsupportedOperationException("unsupported share link parameter: " + key);
            }
        }
    }

    // ========================
    // Transport settings parsing
    // ========================

    /**
     * Parse transport-related query params into a StreamSettings object.
     *
     * @param query      parsed query parameters
     * @param remoteHost the remote host from the URI (used as default for host params)
     * @return a StreamSettings with network and transport-specific settings populated
     */
    protected StreamSettings parseStreamSettings(Map<String, String> query, String remoteHost) {
        StreamSettings streamSettings = new StreamSettings();

        // transport
        String type = query.getOrDefault("type", "tcp");
        assert type != null;
        parseTransportSettings(streamSettings, query, type);

        // security
        parseSecuritySettings(streamSettings, query, remoteHost);

        // finalmask
        if (query.containsKey("fm")) {
            streamSettings.finalmask = new GsonBuilder().setPrettyPrinting().create().fromJson(query.get("fm"), JsonObject.class);
        }

        return streamSettings;
    }

    private void parseTransportSettings(StreamSettings ss, Map<String, String> query, String type) {
        ss.network = type;

        switch (type) {
            case "tcp":
                // RAW transport, no extra settings needed
                break;

            case "ws":
                ss.wsSettings = new WsSettings();
                ss.wsSettings.path = query.getOrDefault("path", "/");
                ss.wsSettings.host = query.getOrDefault("host", "");
                break;

            case "grpc":
                ss.grpcSettings = new GRPCSettings();
                ss.grpcSettings.serviceName = query.getOrDefault("serviceName", "");
                ss.grpcSettings.authority = nullable(query.get("authority"), "");

                String grpcMode = query.getOrDefault("mode", "gun");
                switch (Objects.requireNonNullElse(grpcMode, "gun")) {
                    case "gun":
                        ss.grpcSettings.multiMode = false;
                        break;
                    case "multi":
                        ss.grpcSettings.multiMode = true;
                        break;
                    default:
                        throw new UnsupportedOperationException("unsupported gRPC mode: " + grpcMode);
                }
                break;

            case "httpupgrade":
                ss.httpupgradeSettings = new HttpUpgradeSettings();
                ss.httpupgradeSettings.path = query.getOrDefault("path", "/");
                ss.httpupgradeSettings.host = query.getOrDefault("host", "");
                break;

            case "xhttp":
                ss.xHttpSettings = new XHttpSettings();
                ss.xHttpSettings.path = query.getOrDefault("path", "/");
                ss.xHttpSettings.host = query.getOrDefault("host", "");
                ss.xHttpSettings.mode = query.getOrDefault("mode", "auto");

                // parse extra JSON if present
                String extra = query.get("extra");
                if (extra != null && !extra.isEmpty()) {
                    parseXHttpExtra(ss.xHttpSettings, extra);
                }
                break;

            case "kcp":
                ss.kcpSettings = new KcpSettings();
                ss.kcpSettings.mtu = 1350;
                ss.kcpSettings.tti = 50;
                ss.kcpSettings.uplinkCapacity = 5;
                ss.kcpSettings.downlinkCapacity = 20;
                ss.kcpSettings.congestion = true;
                break;

            case "http":
                throw new UnsupportedOperationException("unsupported transport: http");

            default:
                throw new IllegalArgumentException("unknown transport type: " + type);
        }
    }

    /**
     * Parse the XHTTP extra JSON and populate XHttpSettings fields.
     * <p>
     * The extra JSON is a free-form object that may contain downloadSettings and other fields.
     */
    @SuppressWarnings("unchecked")
    private void parseXHttpExtra(XHttpSettings xHttpSettings, String extraJson) {
        Gson gson = new Gson();
        Map<String, Object> extra = gson.fromJson(extraJson, new TypeToken<>() {
        });

        if (extra == null) return;

        // downloadSettings
        if (extra.containsKey("downloadSettings")) {
            Object ds = extra.get("downloadSettings");
            if (ds instanceof Map) {
                xHttpSettings.downloadSettings = (Map<String, Object>) ds;
            }
        }
    }

    private void parseSecuritySettings(StreamSettings ss, Map<String, String> query, String remoteHost) {
        String security = query.getOrDefault("security", "none");
        ss.security = security;

        switch (nullable(security, "none")) {
            case "none":
                break;

            case "tls":
                rejectUnsupportedParams(query, "ech");

                ss.tlsSettings = new TLSSettings();
                ss.tlsSettings.serverName = query.getOrDefault("sni", remoteHost);
                ss.tlsSettings.fingerprint = query.getOrDefault("fp", "chrome");

                if (query.containsKey("alpn")) {
                    ss.tlsSettings.alpn = Objects.requireNonNullElse(query.get("alpn"), "h2,http/1.1").split(",");
                } else {
                    ss.tlsSettings.alpn = new String[]{"h2", "http/1.1"};
                }

                // pinnedPeerCertSha256 (pcs)
                ss.tlsSettings.pinnedPeerCertSha256 = nullable(query.get("pcs"), "");
                if (ss.tlsSettings.pinnedPeerCertSha256.isEmpty()) {
                    ss.tlsSettings.pinnedPeerCertSha256 = null;
                }

                // verifyPeerCertByName (vcn)
                ss.tlsSettings.verifyPeerCertByName = nullable(query.get("vcn"), "");
                if (ss.tlsSettings.verifyPeerCertByName.isEmpty()) {
                    ss.tlsSettings.verifyPeerCertByName = null;
                }
                break;

            case "reality":

                ss.realitySettings = new RealitySettings();
                ss.realitySettings.serverName = query.getOrDefault("sni", remoteHost);
                ss.realitySettings.fingerprint = query.getOrDefault("fp", "chrome");
                ss.realitySettings.publicKey = query.getOrDefault("pbk", "");
                ss.realitySettings.shortId = query.getOrDefault("sid", "");

                // mldsa65Verify (pqv)
                String pqv = nullable(query.get("pqv"), "");
                ss.realitySettings.mldsa65Verify = pqv.isEmpty() ? null : pqv;
                break;

            default:
                throw new UnsupportedOperationException("unsupported security type: " + security);
        }
    }

    // ========================
    // Transport settings marshaling
    // ========================

    /**
     * Build query parameters from a StreamSettings object.
     * Populates transport-specific and security-specific params.
     *
     * @param streamSettings the stream settings to marshal
     * @return ordered map of query key-value pairs
     */
    protected Map<String, String> marshalStreamSettingsQueries(StreamSettings streamSettings) throws MarshalProxyException {
        Map<String, String> queries = new LinkedHashMap<>();

        // transport type
        queries.put("type", nullable(streamSettings.network, "tcp"));

        // transport-specific params
        marshalTransportQueries(queries, streamSettings);

        // security
        queries.put("security", nullable(streamSettings.security, "none"));

        // security-specific params
        marshalSecurityQueries(queries, streamSettings);

        return queries;
    }

    private void marshalTransportQueries(Map<String, String> queries, StreamSettings ss) throws MarshalProxyException {
        String network = nullable(ss.network, "tcp");

        switch (network) {
            case "tcp":
                break;

            case "ws":
                if (ss.wsSettings != null) {
                    queries.put("host", nullable(ss.wsSettings.host, ""));
                    queries.put("path", nullable(ss.wsSettings.path, "/"));
                }
                break;

            case "grpc":
                if (ss.grpcSettings != null) {
                    queries.put("serviceName", nullable(ss.grpcSettings.serviceName, ""));
                    queries.put("mode", ss.grpcSettings.multiMode ? "multi" : "gun");
                    queries.put("authority", nullable(ss.grpcSettings.authority, ""));
                }
                break;

            case "httpupgrade":
                if (ss.httpupgradeSettings != null) {
                    queries.put("host", nullable(ss.httpupgradeSettings.host, ""));
                    queries.put("path", nullable(ss.httpupgradeSettings.path, "/"));
                }
                break;

            case "xhttp":
                if (ss.xHttpSettings != null) {
                    queries.put("host", nullable(ss.xHttpSettings.host, ""));
                    queries.put("path", nullable(ss.xHttpSettings.path, "/"));
                    queries.put("mode", nullable(ss.xHttpSettings.mode, "auto"));

                    // marshal extra JSON if downloadSettings is present
                    String extraJson = marshalXHttpExtra(ss.xHttpSettings);
                    if (extraJson != null) {
                        queries.put("extra", extraJson);
                    }
                }
                break;

            default:
                throw new MarshalProxyException("unsupported transport: " + network);
        }
    }

    /**
     * Marshal XHttpSettings extra fields back into a JSON string for the extra query param.
     * Returns null if there are no extra fields to marshal.
     */
    private String marshalXHttpExtra(XHttpSettings xHttpSettings) {
        if (xHttpSettings.downloadSettings == null) {
            return null;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("downloadSettings", xHttpSettings.downloadSettings);

        return new Gson().toJson(extra);
    }

    private void marshalSecurityQueries(Map<String, String> queries, StreamSettings ss) throws MarshalProxyException {
        String security = nullable(ss.security, "none");

        switch (security) {
            case "none":
                break;

            case "tls":
                if (ss.tlsSettings != null) {
                    queries.put("sni", nullable(ss.tlsSettings.serverName, ""));
                    queries.put("fp", nullable(ss.tlsSettings.fingerprint, "chrome"));
                    queries.put("alpn", ss.tlsSettings.alpn != null ? String.join(",", ss.tlsSettings.alpn) : "");
                    queries.put("pcs", nullable(ss.tlsSettings.pinnedPeerCertSha256, ""));
                    queries.put("vcn", nullable(ss.tlsSettings.verifyPeerCertByName, ""));
                }
                break;

            case "reality":
                if (ss.realitySettings != null) {
                    queries.put("sni", nullable(ss.realitySettings.serverName, ""));
                    queries.put("fp", nullable(ss.realitySettings.fingerprint, "chrome"));
                    queries.put("pbk", nullable(ss.realitySettings.publicKey, ""));
                    queries.put("sid", nullable(ss.realitySettings.shortId, ""));
                    queries.put("pqv", nullable(ss.realitySettings.mldsa65Verify, ""));
                }
                break;

            default:
                throw new MarshalProxyException("unsupported security: " + security);
        }
    }
}
