package cn.gov.xivpn2.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.ProxyDao;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.GRPCSettings;
import cn.gov.xivpn2.xrayconfig.HttpUpgradeSettings;
import cn.gov.xivpn2.xrayconfig.HysteriaHop;
import cn.gov.xivpn2.xrayconfig.HysteriaTransportSettings;
import cn.gov.xivpn2.xrayconfig.KcpSettings;
import cn.gov.xivpn2.xrayconfig.MuxSettings;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.RawHeader;
import cn.gov.xivpn2.xrayconfig.RawHttpHeaderRequest;
import cn.gov.xivpn2.xrayconfig.RawSettings;
import cn.gov.xivpn2.xrayconfig.RealitySettings;
import cn.gov.xivpn2.xrayconfig.StreamSettings;
import cn.gov.xivpn2.xrayconfig.TLSSettings;
import cn.gov.xivpn2.xrayconfig.WsSettings;
import cn.gov.xivpn2.xrayconfig.XHttpExtraSettings;
import cn.gov.xivpn2.xrayconfig.XHttpSettings;
import cn.gov.xivpn2.xrayconfig.XHttpStreamSettings;
import cn.gov.xivpn2.xrayconfig.XMuxSettings;

public abstract class ProxyActivity<T> extends AppCompatActivity {

    private final static String TAG = "ProxyActivity";

    protected ProxyEditTextAdapter adapter;

    private String label;
    private String subscription;
    private boolean inline;

    private String xhttpDownload = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BlackBackground.apply(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_proxy);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        label = getIntent().getStringExtra("LABEL");
        subscription = getIntent().getStringExtra("SUBSCRIPTION");
        String config = getIntent().getStringExtra("CONFIG");
        inline = getIntent().getBooleanExtra("INLINE", false);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(label);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProxyEditTextAdapter();
        recyclerView.setAdapter(adapter);

        recyclerView.setItemAnimator(null);

        // initialize inputs
        adapter.addGroupTitle("GROUP_PROXY", "Proxy settings");
        initializeInputs(adapter);
        if (hasStreamSettings()) {
            adapter.addGroupTitle("GROUP_NETWORK", "Transport");
            adapter.addInput("NETWORK", "Network", Arrays.asList("tcp", "ws", "httpupgrade", "xhttp", "hysteria", "grpc", "kcp"));
            adapter.addInputAfter("NETWORK", "NETWORK_TCP_HEADER", "TCP Header", Arrays.asList("none", "http"));
            adapter.addGroupTitle("GROUP_SECURITY", "Security");
            adapter.addInput("SECURITY", "Security", Arrays.asList("none", "tls", "reality"));
        }
        adapter.addGroupTitle("GROUP_MUX", "Multiplex");
        adapter.addInput("MUX_ENABLED", "Multiplex", Arrays.asList("disabled", "enabled"));

        adapter.addGroupTitle("GROUP_FINALMASK", "Finalmask");
        adapter.addTextAreaInput("FINALMASK", "Finalmask JSON", "Input finalmask JSON object here. Read Xray document for more information.");


        afterInitializeInputs(adapter);

        adapter.setOnInputChangedListener((k, v) -> {
            onInputChanged(adapter, k, v);
        });

        // set existing values
        if (config != null) {
            Gson gson = new Gson();
            Outbound<T> outbound = gson.fromJson(config, getType());
            LinkedHashMap<String, String> initials = decodeOutboundConfig(outbound);
            initials.forEach((k, v) -> {
                adapter.setValue(k, v);
            });
        }
    }

    /**
     * This method may be overridden to disable stream settings (network and security).
     */
    protected boolean hasStreamSettings() {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        } else if (item.getItemId() == R.id.save) {

            // save

            // validation
            if (validate()) {
                Outbound<T> outbound = buildOutboundConfig(this.adapter);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(outbound);
                Log.d(TAG, json);

                if (!inline) {
                    ProxyDao proxyDao = AppDatabase.getInstance().proxyDao();
                    if (proxyDao.exists(label, subscription) > 0) {
                        // update
                        proxyDao.updateConfig(label, subscription, json);
                    } else {
                        // insert
                        Proxy proxy = new Proxy();
                        proxy.subscription = subscription;
                        proxy.label = label;
                        proxy.config = json;
                        proxy.protocol = getProtocolName();
                        proxyDao.add(proxy);
                    }

                    XiVPNService.markConfigStale(this);
                } else {
                    Log.i(TAG, "inline result: " + json);
                    Intent intent = new Intent();
                    intent.putExtra("CONFIG", json);
                    setResult(RESULT_OK, intent);
                }

                finish();
            }

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.proxy_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Return true if all inputs are valid
     */
    private boolean validate() {
        boolean valid = true;
        for (int i = 0; i < adapter.getInputs().size(); i++) {
            ProxyEditTextAdapter.Input input = adapter.getInputs().get(i);

            boolean old = input.validated;

            if (input instanceof ProxyEditTextAdapter.SelectInput) {
                input.validated = validateField(input.key, ((ProxyEditTextAdapter.SelectInput) input).value);
            } else if (input instanceof ProxyEditTextAdapter.TextInput) {
                input.validated = validateField(input.key, ((ProxyEditTextAdapter.TextInput) input).value);
            } else if (input instanceof ProxyEditTextAdapter.ButtonInput) {
                input.validated = validateField(input.key, "");
            } else if (input instanceof ProxyEditTextAdapter.TextAreaInput) {
                input.validated = validateField(input.key, ((ProxyEditTextAdapter.TextAreaInput) input).value);
            }
            if (!input.validated) {
                valid = false;
            }
            if (old != input.validated) {
                adapter.notifyItemChanged(i);
            }

        }
        return valid;
    }

    protected boolean validateField(String key, String value) {
        if (key.equals("NETWORK_XHTTP_EXTRA_DOWNLOAD_BTN") && adapter.getValue("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD").equals("True")) {
            return !xhttpDownload.isEmpty();
        }
        switch (key) {
            case "SECURITY_REALITY_PUBLIC_KEY":
            case "NETWORK_XHTTP_EXTRA_DOWNLOAD_ADDRESS":
                return !value.isEmpty();
            case "NETWORK_XHTTP_EXTRA_DOWNLOAD_PORT":
                return Utils.isValidPort(value);
            case "MUX_XUDP_CONCURRENCY":
            case "MUX_CONCURRENCY":
            case "NETWORK_HYSTERIA_HOP_INTERVAL":
            case "NETWORK_KCP_UPLINK":
            case "NETWORK_KCP_DOWNLINK":
            case "NETWORK_KCP_TTI":
            case "NETWORK_KCP_MTU":
            case "NETWORK_XHTTP_EXTRA_SC_MAX_BUFFERED_POSTS":
            case "NETWORK_XHTTP_EXTRA_SERVER_MAX_HEADER_BYTES":

                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return false;
                }

                return true;

            case "FINALMASK":
            case "NETWORK_XHTTP_EXTRA_JSON":
                if (!value.isBlank()) {
                    try {
                        new Gson().fromJson(value, JsonObject.class);
                        return true;
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "validate" + key, e);
                        return false;
                    }
                }
                return true;
        }


        return true;
    }

    /**
     * @return type of T
     */
    abstract protected Type getType();

    /**
     * Build xray outbound object
     */
    protected Outbound<T> buildOutboundConfig(IProxyEditor adapter) {
        Outbound<T> outbound = new Outbound<>();
        outbound.protocol = getProtocolName();
        outbound.settings = buildProtocolSettings(adapter);


        outbound.streamSettings = new StreamSettings();

        String finalmaskString = Objects.requireNonNullElse(this.adapter.getValue("FINALMASK"), "");
        if (!finalmaskString.isBlank()) {
            try {
                outbound.streamSettings.finalmask = new Gson().fromJson(finalmaskString, JsonObject.class);
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "parse finalmask", e);
            }
        }


        if (!hasStreamSettings()) return outbound;

        String network = this.adapter.getValue("NETWORK");
        outbound.streamSettings.network = network;
        switch (network) {
            case "tcp":
                outbound.streamSettings.rawSettings = new RawSettings();
                if (adapter.getValue("NETWORK_TCP_HEADER").equals("http")) {
                    outbound.streamSettings.rawSettings.header = new RawHeader();
                    outbound.streamSettings.rawSettings.header.type = "http";
                    outbound.streamSettings.rawSettings.header.request = new RawHttpHeaderRequest();
                    if (!adapter.getValue("NETWORK_TCP_HEADER_HTTP_HOST").isBlank()) {
                        outbound.streamSettings.rawSettings.header.request.headers.put("Host", adapter.getValue("NETWORK_TCP_HEADER_HTTP_HOST"));
                    }
                } else {
                    outbound.streamSettings.rawSettings.header = null;
                }
                break;
            case "ws":
                outbound.streamSettings.wsSettings = new WsSettings();
                outbound.streamSettings.wsSettings.path = adapter.getValue("NETWORK_WS_PATH");
                outbound.streamSettings.wsSettings.host = adapter.getValue("NETWORK_WS_HOST");
                break;
            case "httpupgrade":
                outbound.streamSettings.httpupgradeSettings = new HttpUpgradeSettings();
                outbound.streamSettings.httpupgradeSettings.path = adapter.getValue("NETWORK_HTTPUPGRADE_PATH");
                outbound.streamSettings.httpupgradeSettings.host = adapter.getValue("NETWORK_HTTPUPGRADE_HOST");
                break;
            case "xhttp":
                outbound.streamSettings.xHttpSettings = new XHttpSettings();
                outbound.streamSettings.xHttpSettings.mode = adapter.getValue("NETWORK_XHTTP_MODE");
                outbound.streamSettings.xHttpSettings.path = adapter.getValue("NETWORK_XHTTP_PATH");
                outbound.streamSettings.xHttpSettings.host = adapter.getValue("NETWORK_XHTTP_HOST");

                if ("Manual Input".equals(adapter.getValue("NETWORK_XHTTP_EXTRA"))) {
                    XHttpExtraSettings extra = new XHttpExtraSettings();

                    extra.headers = new HashMap<>();
                    for (String line : adapter.getValue("NETWORK_XHTTP_EXTRA_HEADERS").split("\\n")) {
                        if (line.isBlank()) continue;
                        String[] h = line.split(":", 2);
                        if (h.length < 2) continue;

                        extra.headers.put(h[0].strip(), h[1].strip());
                    }

                    extra.xPaddingBytes = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_BYTES");
                    extra.xPaddingObfsMode = "True".equals(adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_OBFS_MODE"));
                    extra.xPaddingHeader = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_HEADER");
                    extra.xPaddingKey = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_KEY");
                    extra.xPaddingMethod = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_METHOD");
                    extra.xPaddingPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_PLACEMENT");

                    extra.uplinkHTTPMethod = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_HTTP_METHOD");
                    extra.sessionPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_SESSION_PLACEMENT");
                    extra.sessionKey = adapter.getValue("NETWORK_XHTTP_EXTRA_SESSION_KEY");
                    extra.seqPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_SEQ_PLACEMENT");
                    extra.seqKey = adapter.getValue("NETWORK_XHTTP_EXTRA_SEQ_KEY");
                    extra.uplinkDataPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_DATA_PLACEMENT");
                    extra.uplinkDataKey = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_DATA_KEY");
                    extra.uplinkChunkSize = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_CHUNK_SIZE");

                    extra.noGRPCHeader = "True".equals(adapter.getValue("NETWORK_XHTTP_EXTRA_NO_GRPC_HEADER")) ? true : null;
                    extra.noSSEHeader = "True".equals(adapter.getValue("NETWORK_XHTTP_EXTRA_NO_SSE_HEADER")) ? true : null;
                    extra.scMaxEachPostBytes = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_MAX_EACH_POST_BYTES");
                    extra.scMinPostsIntervalMs = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_MIN_POSTS_INTERVAL_MS");
                    extra.scStreamUpServerSecs = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_STREAM_UP_SERVER_SECS");

                    String scMaxBufferedPostsStr = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_MAX_BUFFERED_POSTS");
                    if (!scMaxBufferedPostsStr.isBlank()) {
                        try { extra.scMaxBufferedPosts = Long.parseLong(scMaxBufferedPostsStr); } catch (NumberFormatException ignored) {}
                    }
                    String serverMaxHeaderBytesStr = adapter.getValue("NETWORK_XHTTP_EXTRA_SERVER_MAX_HEADER_BYTES");
                    if (!serverMaxHeaderBytesStr.isBlank()) {
                        try { extra.serverMaxHeaderBytes = Integer.parseInt(serverMaxHeaderBytesStr); } catch (NumberFormatException ignored) {}
                    }

                    if (adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX").equals("Enabled")) {
                        extra.xmux = new XMuxSettings();
                        extra.xmux.maxConcurrency = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_MAX_CONCURRENCY");
                        extra.xmux.maxConnections = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_MAX_CONNECTIONS");
                        extra.xmux.cMaxReuseTimes = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_C_MAX_REUSE_TIMES");
                        extra.xmux.hMaxRequestTimes = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REQUEST_TIMES");
                        extra.xmux.hMaxReusableSecs = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REUSABLE_SECS");
                        extra.xmux.hKeepAlivePeriod = Long.valueOf(adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_H_KEEPALIVE_PEROID"));
                    }

                    if (adapter.getValue("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD").equals("True") && xhttpDownload != null && !xhttpDownload.isEmpty()) {
                        Gson gson = new Gson();
                        JsonElement downloadSettings = gson.fromJson(xhttpDownload, JsonObject.class).get("streamSettings");
                        if (downloadSettings != null) {
                            extra.downloadSettings = gson.fromJson(downloadSettings, XHttpStreamSettings.class);

                            extra.downloadSettings.network = "xhttp";
                            extra.downloadSettings.address = adapter.getValue("NETWORK_XHTTP_EXTRA_DOWNLOAD_ADDRESS");
                            extra.downloadSettings.port = Integer.valueOf(adapter.getValue("NETWORK_XHTTP_EXTRA_DOWNLOAD_PORT"));

                            extra.downloadSettings.xHttpSettings = new XHttpSettings();
                            extra.downloadSettings.xHttpSettings.path = adapter.getValue("NETWORK_XHTTP_PATH");
                        }
                    }

                    outbound.streamSettings.xHttpSettings.extra = new Gson().toJsonTree(extra).getAsJsonObject();
                } else if (!adapter.getValue("NETWORK_XHTTP_EXTRA_JSON").isEmpty()) {
                    outbound.streamSettings.xHttpSettings.extra = new Gson().fromJson(adapter.getValue("NETWORK_XHTTP_EXTRA_JSON"), JsonObject.class);
                }
                break;
            case "kcp":
                outbound.streamSettings.kcpSettings = new KcpSettings();
                outbound.streamSettings.kcpSettings.congestion = "True".equals(adapter.getValue("NETWORK_KCP_CONGESTION"));
                outbound.streamSettings.kcpSettings.mtu = Integer.parseInt(adapter.getValue("NETWORK_KCP_MTU"));
                outbound.streamSettings.kcpSettings.tti = Integer.parseInt(adapter.getValue("NETWORK_KCP_TTI"));
                outbound.streamSettings.kcpSettings.uplinkCapacity = Integer.parseInt(adapter.getValue("NETWORK_KCP_UPLINK"));
                outbound.streamSettings.kcpSettings.downlinkCapacity = Integer.parseInt(adapter.getValue("NETWORK_KCP_DOWNLINK"));
                break;
            case "grpc":
                outbound.streamSettings.grpcSettings = new GRPCSettings();
                outbound.streamSettings.grpcSettings.authority = adapter.getValue("NETWORK_GRPC_AUTHORITY");
                outbound.streamSettings.grpcSettings.multiMode = "multi".equals(adapter.getValue("NETWORK_GRPC_MODE"));
                outbound.streamSettings.grpcSettings.serviceName = adapter.getValue("NETWORK_GRPC_SERVICE_NAME");
                break;
            case "hysteria":
                outbound.streamSettings.hysteriaSettings = new HysteriaTransportSettings();
                outbound.streamSettings.hysteriaSettings.auth = adapter.getValue("NETWORK_HYSTERIA_AUTH");
                outbound.streamSettings.hysteriaSettings.down = adapter.getValue("NETWORK_HYSTERIA_DOWN");
                outbound.streamSettings.hysteriaSettings.up = adapter.getValue("NETWORK_HYSTERIA_UP");
                if ("True".equals(adapter.getValue("NETWORK_HYSTERIA_HOP"))) {
                    outbound.streamSettings.hysteriaSettings.udphop = new HysteriaHop();
                    outbound.streamSettings.hysteriaSettings.udphop.port = adapter.getValue("NETWORK_HYSTERIA_HOP_PORTS");
                    outbound.streamSettings.hysteriaSettings.udphop.interval = Integer.parseInt(adapter.getValue("NETWORK_HYSTERIA_HOP_INTERVAL"));

                }

        }

        String security = this.adapter.getValue("SECURITY");
        if (security.equals("tls")) {
            outbound.streamSettings.security = "tls";
            outbound.streamSettings.tlsSettings = new TLSSettings();
            if (adapter.getValue("SECURITY_TLS_FINGERPRINT").equals("None")) {
                outbound.streamSettings.tlsSettings.fingerprint = "";
            } else {
                outbound.streamSettings.tlsSettings.fingerprint = adapter.getValue("SECURITY_TLS_FINGERPRINT");
            }
            outbound.streamSettings.tlsSettings.serverName = adapter.getValue("SECURITY_TLS_SNI");
            outbound.streamSettings.tlsSettings.alpn = adapter.getValue("SECURITY_TLS_ALPN").split(",");
            if (!adapter.getValue("SECURITY_TLS_VERIFY_PEER_CERT_BY_NAME").isBlank()) {
                outbound.streamSettings.tlsSettings.verifyPeerCertByName = adapter.getValue("SECURITY_TLS_VERIFY_PEER_CERT_BY_NAME");
            }
            if (!adapter.getValue("SECURITY_TLS_PINNED_PEER_CERT_SHA256").isBlank()) {
                outbound.streamSettings.tlsSettings.pinnedPeerCertSha256 = adapter.getValue("SECURITY_TLS_PINNED_PEER_CERT_SHA256");
            }
        } else if (security.equals("reality")) {
            outbound.streamSettings.security = "reality";
            outbound.streamSettings.realitySettings = new RealitySettings();
            outbound.streamSettings.realitySettings.shortId = adapter.getValue("SECURITY_REALITY_SHORTID");
            outbound.streamSettings.realitySettings.fingerprint = adapter.getValue("SECURITY_REALITY_FINGERPRINT");
            outbound.streamSettings.realitySettings.serverName = adapter.getValue("SECURITY_REALITY_SNI");
            outbound.streamSettings.realitySettings.publicKey = adapter.getValue("SECURITY_REALITY_PUBLIC_KEY");
            if (adapter.getValue("SECURITY_REALITY_FINGERPRINT").equals("None")) {
                outbound.streamSettings.realitySettings.fingerprint = null;
            } else {
                outbound.streamSettings.realitySettings.fingerprint = adapter.getValue("SECURITY_REALITY_FINGERPRINT");
            }
            if (!adapter.getValue("SECURITY_REALITY_MLDSA65VERIFY").isBlank()) {
                outbound.streamSettings.realitySettings.mldsa65Verify = adapter.getValue("SECURITY_REALITY_MLDSA65VERIFY");
            } else {
                outbound.streamSettings.realitySettings.mldsa65Verify = null;
            }
        }

        if ("enabled".equals(this.adapter.getValue("MUX_ENABLED"))) {
            outbound.mux = new MuxSettings();
            outbound.mux.enabled = true;
            outbound.mux.concurrency = Integer.parseInt(adapter.getValue("MUX_CONCURRENCY"));
            outbound.mux.xudpConcurrency = Integer.parseInt(adapter.getValue("MUX_XUDP_CONCURRENCY"));
            outbound.mux.xudpProxyUDP443 = adapter.getValue("MUX_XUDP_PROXY_UDP443");
        }

        return outbound;
    }

    /**
     * Extract configuration values from outbound object
     */
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<T> outbound) {
        LinkedHashMap<String, String> initials = new LinkedHashMap<>();

        if (outbound.streamSettings != null && outbound.streamSettings.finalmask != null) {
            initials.put("FINALMASK", new GsonBuilder().setPrettyPrinting().create().toJson(outbound.streamSettings.finalmask));
        } else {
            initials.put("FINALMASK", "");
        }

        if (!hasStreamSettings()) return initials;

        initials.put("NETWORK", outbound.streamSettings.network);
        switch (outbound.streamSettings.network) {
            case "tcp":
                if (outbound.streamSettings.rawSettings != null && outbound.streamSettings.rawSettings.header != null && "http".equals(outbound.streamSettings.rawSettings.header.type)) {
                    initials.put("NETWORK_TCP_HEADER", "http");
                    if (outbound.streamSettings.rawSettings.header.request != null && outbound.streamSettings.rawSettings.header.request.headers != null && outbound.streamSettings.rawSettings.header.request.headers.containsKey("Host")) {
                        initials.put("NETWORK_TCP_HEADER_HTTP_HOST", outbound.streamSettings.rawSettings.header.request.headers.get("Host"));
                    } else {
                        initials.put("NETWORK_TCP_HEADER_HTTP_HOST", "");
                    }
                } else {
                    initials.put("NETWORK_TCP_HEADER", "none");
                }
                break;
            case "ws":
                initials.put("NETWORK_WS_PATH", outbound.streamSettings.wsSettings.path);
                initials.put("NETWORK_WS_HOST", outbound.streamSettings.wsSettings.host);
                break;
            case "httpupgrade":
                initials.put("NETWORK_HTTPUPGRADE_PATH", outbound.streamSettings.httpupgradeSettings.path);
                initials.put("NETWORK_HTTPUPGRADE_HOST", outbound.streamSettings.httpupgradeSettings.host);
                break;
            case "xhttp":
                if (inline) break;
                initials.put("NETWORK_XHTTP_MODE", outbound.streamSettings.xHttpSettings.mode);
                initials.put("NETWORK_XHTTP_PATH", outbound.streamSettings.xHttpSettings.path);
                initials.put("NETWORK_XHTTP_HOST", outbound.streamSettings.xHttpSettings.host);

                try {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();

                    initials.put("NETWORK_XHTTP_EXTRA", "Edit JSON");
                    initials.put("NETWORK_XHTTP_EXTRA_JSON", gson.toJson(outbound.streamSettings.xHttpSettings.extra));


                } catch (JsonSyntaxException ignored) {
                } catch (NullPointerException ignored) {
                    Log.i(TAG, "ぬるぽ");
                }
                break;
            case "hysteria":
                initials.put("NETWORK_HYSTERIA_AUTH", outbound.streamSettings.hysteriaSettings.auth);
                initials.put("NETWORK_HYSTERIA_DOWN", outbound.streamSettings.hysteriaSettings.down);
                initials.put("NETWORK_HYSTERIA_UP", outbound.streamSettings.hysteriaSettings.up);
                if (outbound.streamSettings.hysteriaSettings.udphop != null) {
                    initials.put("NETWORK_HYSTERIA_HOP", "True");
                    initials.put("NETWORK_HYSTERIA_HOP_PORTS", Objects.requireNonNullElse(outbound.streamSettings.hysteriaSettings.udphop.port, "443"));
                    initials.put("NETWORK_HYSTERIA_HOP_INTERVAL", String.valueOf(outbound.streamSettings.hysteriaSettings.udphop.interval));
                } else {
                    initials.put("NETWORK_HYSTERIA_HOP", "False");
                }
                break;
            case "grpc":
                initials.put("NETWORK_GRPC_AUTHORITY", outbound.streamSettings.grpcSettings.authority);
                initials.put("NETWORK_GRPC_SERVICE_NAME", outbound.streamSettings.grpcSettings.serviceName);
                initials.put("NETWORK_GRPC_MODE", outbound.streamSettings.grpcSettings.multiMode ? "multi" : "gun");
                break;
            case "kcp":
                initials.put("NETWORK_KCP_MTU", String.valueOf(outbound.streamSettings.kcpSettings.mtu));
                initials.put("NETWORK_KCP_TTI", String.valueOf(outbound.streamSettings.kcpSettings.tti));
                initials.put("NETWORK_KCP_UPLINK", String.valueOf(outbound.streamSettings.kcpSettings.uplinkCapacity));
                initials.put("NETWORK_KCP_DOWNLINK", String.valueOf(outbound.streamSettings.kcpSettings.downlinkCapacity));
                initials.put("NETWORK_KCP_CONGESTION", outbound.streamSettings.kcpSettings.congestion ? "True" : "False");
                break;
        }

        if (outbound.streamSettings.security == null || outbound.streamSettings.security.isEmpty()) {

        } else if (outbound.streamSettings.security.equals("tls")) {
            initials.put("SECURITY", "tls");
            initials.put("SECURITY_TLS_SNI", outbound.streamSettings.tlsSettings.serverName);
            initials.put("SECURITY_TLS_ALPN", String.join(",", outbound.streamSettings.tlsSettings.alpn));
            if (outbound.streamSettings.tlsSettings.fingerprint.isEmpty()) {
                initials.put("SECURITY_TLS_FINGERPRINT", "None");
            } else {
                initials.put("SECURITY_TLS_FINGERPRINT", outbound.streamSettings.tlsSettings.fingerprint);
            }
            initials.put("SECURITY_TLS_VERIFY_PEER_CERT_BY_NAME", Objects.requireNonNullElse(outbound.streamSettings.tlsSettings.verifyPeerCertByName, ""));
            initials.put("SECURITY_TLS_PINNED_PEER_CERT_SHA256", Objects.requireNonNullElse(outbound.streamSettings.tlsSettings.pinnedPeerCertSha256, ""));
        } else if (outbound.streamSettings.security.equals("reality")) {
            initials.put("SECURITY", "reality");
            initials.put("SECURITY_REALITY_SNI", outbound.streamSettings.realitySettings.serverName);
            initials.put("SECURITY_REALITY_SHORTID", outbound.streamSettings.realitySettings.shortId);
            initials.put("SECURITY_REALITY_PUBLIC_KEY", outbound.streamSettings.realitySettings.publicKey);
            initials.put("SECURITY_REALITY_FINGERPRINT", Objects.requireNonNullElse(outbound.streamSettings.realitySettings.fingerprint, "None"));
            initials.put("SECURITY_REALITY_MLDSA65VERIFY", Objects.requireNonNullElse(outbound.streamSettings.realitySettings.mldsa65Verify, ""));
        }

        if (outbound.mux == null) {

        } else {
            initials.put("MUX_ENABLED", "enabled");
            initials.put("MUX_CONCURRENCY", String.valueOf(outbound.mux.concurrency));
            initials.put("MUX_XUDP_CONCURRENCY", String.valueOf(outbound.mux.xudpConcurrency));
            initials.put("MUX_XUDP_PROXY_UDP443", outbound.mux.xudpProxyUDP443);
        }


        return initials;
    }

    /**
     * Build setting object for the proxy protocol
     */
    abstract protected T buildProtocolSettings(IProxyEditor adapter);

    abstract protected String getProtocolName();

    abstract protected void initializeInputs(IProxyEditor adapter);

    protected void afterInitializeInputs(IProxyEditor adapter) {

    }

    /**
     * Called when user changes a configuration field
     */
    protected void onInputChanged(IProxyEditor adapter, String key, String value) {
        if (!hasStreamSettings()) return;

        switch (key) {
            case "NETWORK":
                adapter.removeInputByPrefix("NETWORK_");
                switch (value) {
                    case "tcp":
                        adapter.addInputAfter("NETWORK", "NETWORK_TCP_HEADER", "TCP Header", Arrays.asList("none", "http"));
                        break;
                    case "ws":
                        adapter.addInputAfter("NETWORK", "NETWORK_WS_PATH", "Websocket Path", "/");
                        adapter.addInputAfter("NETWORK", "NETWORK_WS_HOST", "Websocket Host");
                        break;
                    case "httpupgrade":
                        adapter.addInputAfter("NETWORK", "NETWORK_HTTPUPGRADE_PATH", "HttpUpgrade Path", "/");
                        adapter.addInputAfter("NETWORK", "NETWORK_HTTPUPGRADE_HOST", "HttpUpgrade Host");
                        break;
                    case "xhttp":
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_EXTRA", "XHTTP Extra", List.of("Edit JSON", "Manual Input"));
                        adapter.addGroupTitleAfter("NETWORK", "NETWORK_XHTTP_TITLE_EXTRA", "XHTTP Extra");
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_HOST", "XHTTP Host", "");
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_PATH", "XHTTP Path", "/");
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_MODE", "XHTTP Mode", List.of("auto", "packet-up", "stream-up", "stream-one"));
                        break;
                    case "kcp":
                        adapter.addInputAfter("NETWORK", "NETWORK_KCP_MTU", "KCP MTU", "1350");
                        adapter.addInputAfter("NETWORK", "NETWORK_KCP_TTI", "KCP TTI", "50", "Transmission time interval (ms)");
                        adapter.addInputAfter("NETWORK", "NETWORK_KCP_DOWNLINK", "KCP Downlink", "100", "MB/s");
                        adapter.addInputAfter("NETWORK", "NETWORK_KCP_UPLINK", "KCP Uplink", "5", "MB/s");
                        adapter.addInputAfter("NETWORK", "NETWORK_KCP_CONGESTION", "KCP Congestion Control", List.of("True", "False"));
                        break;
                    case "grpc":
                        adapter.addInputAfter("NETWORK", "NETWORK_GRPC_SERVICE_NAME", "gRPC Service Name", "");
                        adapter.addInputAfter("NETWORK", "NETWORK_GRPC_MODE", "gRPC Mode", List.of("gun", "multi"));
                        adapter.addInputAfter("NETWORK", "NETWORK_GRPC_AUTHORITY", "gRPC Authority", "");
                        break;
                    case "hysteria":
                        adapter.addInputAfter("NETWORK", "NETWORK_HYSTERIA_AUTH", "Hysteria Auth", "");
                        adapter.addInputAfter("NETWORK", "NETWORK_HYSTERIA_DOWN", "Hysteria Downlink Bandwidth", "10 mb");
                        adapter.addInputAfter("NETWORK", "NETWORK_HYSTERIA_UP", "Hysteria Uplink Bandwidth", "5 mb");
                        adapter.addInputAfter("NETWORK", "NETWORK_HYSTERIA_HOP", "Hysteria UDP Hop", List.of("False", "True"));
                        break;
                }
                break;

            case "SECURITY":
                adapter.removeInputByPrefix("SECURITY_");
                if (value.equals("tls")) {
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_SNI", "TLS Server Name");
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_ALPN", "TLS ALPN", "h2,http/1.1");
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_FINGERPRINT", "TLS Fingerprint", List.of("None", "chrome", "firefox", "random", "randomized"));
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_VERIFY_PEER_CERT_BY_NAME", "TLS Verify Peer Cert By Name", "", "Comma separated list");
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_PINNED_PEER_CERT_SHA256", "TLS Pinned Peer Cert SHA256", "", "Comma separated list of SHA256 sums in hex");
                } else if (value.equals("reality")) {
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_MLDSA65VERIFY", "REALITY MLDSA65 Public Key");
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_SNI", "REALITY Server Name");
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_FINGERPRINT", "REALITY Fingerprint", List.of("chrome", "firefox", "random", "randomized"));
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_SHORTID", "REALITY Short ID");
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_PUBLIC_KEY", "REALITY Public Key");
                }
                break;

            case "NETWORK_XHTTP_EXTRA":
                String jsonStr = adapter.getValue("NETWORK_XHTTP_EXTRA_JSON");
                String manualHeaders = adapter.getValue("NETWORK_XHTTP_EXTRA_HEADERS");
                String manualXPaddingBytes = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_BYTES");
                String manualXPaddingObfsMode = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_OBFS_MODE");
                String manualXPaddingHeader = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_HEADER");
                String manualXPaddingKey = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_KEY");
                String manualXPaddingMethod = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_METHOD");
                String manualXPaddingPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_X_PADDING_PLACEMENT");
                String manualUplinkHTTPMethod = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_HTTP_METHOD");
                String manualSessionPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_SESSION_PLACEMENT");
                String manualSessionKey = adapter.getValue("NETWORK_XHTTP_EXTRA_SESSION_KEY");
                String manualSeqPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_SEQ_PLACEMENT");
                String manualSeqKey = adapter.getValue("NETWORK_XHTTP_EXTRA_SEQ_KEY");
                String manualUplinkDataPlacement = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_DATA_PLACEMENT");
                String manualUplinkDataKey = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_DATA_KEY");
                String manualUplinkChunkSize = adapter.getValue("NETWORK_XHTTP_EXTRA_UPLINK_CHUNK_SIZE");
                String manualNoGRPCHeader = adapter.getValue("NETWORK_XHTTP_EXTRA_NO_GRPC_HEADER");
                String manualNoSSEHeader = adapter.getValue("NETWORK_XHTTP_EXTRA_NO_SSE_HEADER");
                String manualScMaxEachPostBytes = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_MAX_EACH_POST_BYTES");
                String manualScMinPostsIntervalMs = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_MIN_POSTS_INTERVAL_MS");
                String manualScStreamUpServerSecs = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_STREAM_UP_SERVER_SECS");
                String manualScMaxBufferedPosts = adapter.getValue("NETWORK_XHTTP_EXTRA_SC_MAX_BUFFERED_POSTS");
                String manualServerMaxHeaderBytes = adapter.getValue("NETWORK_XHTTP_EXTRA_SERVER_MAX_HEADER_BYTES");
                String manualXmux = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX");
                String manualXmuxMaxConcurrency = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_MAX_CONCURRENCY");
                String manualXmuxMaxConnections = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_MAX_CONNECTIONS");
                String manualXmuxCMaxReuseTimes = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_C_MAX_REUSE_TIMES");
                String manualXmuxHMaxRequestTimes = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REQUEST_TIMES");
                String manualXmuxHMaxReusableSecs = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REUSABLE_SECS");
                String manualXmuxHKeepAlivePeriod = adapter.getValue("NETWORK_XHTTP_EXTRA_XMUX_H_KEEPALIVE_PEROID");
                String manualSeparateDownload = adapter.getValue("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD");
                String manualDownloadAddress = adapter.getValue("NETWORK_XHTTP_EXTRA_DOWNLOAD_ADDRESS");
                String manualDownloadPort = adapter.getValue("NETWORK_XHTTP_EXTRA_DOWNLOAD_PORT");
                adapter.removeInputByPrefix("NETWORK_XHTTP_EXTRA_");

                if (value.equals("Manual Input")) {
                    // Parse JSON into individual fields
                    XHttpExtraSettings parsed = null;
                    if (jsonStr != null && !jsonStr.isBlank()) {
                        try {
                            parsed = new Gson().fromJson(jsonStr, XHttpExtraSettings.class);
                        } catch (JsonSyntaxException ignored) {
                        }
                    }

                    // Separate download
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD", "Separate Download", List.of("False", "True"));
                    adapter.addGroupTitleAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_TITLE_DOWNLOAD", "XHTTP Separate Download");

                    // Other settings

                    adapter.addTextAreaInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_HEADERS", "Headers", "Each line in the format of \"Header-Name: header-value\".");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_XMUX", "XMux Settings", List.of("Disable", "Enabled"));

                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SERVER_MAX_HEADER_BYTES", "serverMaxHeaderBytes", "8192");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SC_STREAM_UP_SERVER_SECS", "scStreamUpServerSecs", "20-80");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SC_MAX_BUFFERED_POSTS", "scMaxBufferedPosts", "30");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SC_MIN_POSTS_INTERVAL_MS", "scMinPostsIntervalMs", "30");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SC_MAX_EACH_POST_BYTES", "scMaxEachPostBytes", "1000000");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_NO_SSE_HEADER", "Disable SSE header", List.of("False", "True"));
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_NO_GRPC_HEADER", "Disable GRPC header", List.of("False", "True"));
                    adapter.addGroupTitleAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_OTHER_TITLE", "XHTTP Other Settings");

                    // Obfs
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_UPLINK_CHUNK_SIZE", "uplinkChunkSize", "");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_UPLINK_DATA_KEY", "uplinkDataKey", "");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_UPLINK_DATA_PLACEMENT", "uplinkDataPlacement", List.of("auto", "body", "cookie", "header"));
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_UPLINK_HTTP_METHOD", "uplinkHTTPMethod", List.of("POST", "PUT", "PATCH", "GET"));
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SESSION_KEY", "sessionKey", "");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SESSION_PLACEMENT", "sessionPlacement", List.of("path", "query", "cookie", "header"));
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SEQ_KEY", "seqKey", "");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_SEQ_PLACEMENT", "seqPlacement", List.of("path", "query", "cookie", "header"));

                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_X_PADDING_KEY", "xPaddingKey", "x_padding");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_X_PADDING_HEADER", "xPaddingHeader", "X-Padding");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_X_PADDING_PLACEMENT", "xPaddingPlacement", List.of("queryInHeader", "cookie", "header", "query"));
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_X_PADDING_METHOD", "xPaddingMethod", List.of("repeat-x", "tokenish"));
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_X_PADDING_OBFS_MODE", "xPaddingObfsMode", List.of("False", "True"));
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_X_PADDING_BYTES", "xPaddingBytes", "100-1000");
                    adapter.addGroupTitleAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_OBFS_TITLE", "XHTTP Obfs");

                    // Apply parsed values to individual fields
                    if (parsed != null) {
                        if (parsed.headers != null && !parsed.headers.isEmpty()) {
                            StringJoiner joiner = new StringJoiner("\n");
                            parsed.headers.forEach((k, v) -> joiner.add(k + ": " + v));
                            adapter.setValue("NETWORK_XHTTP_EXTRA_HEADERS", joiner.toString());
                        }
                        if (parsed.xPaddingBytes != null) adapter.setValue("NETWORK_XHTTP_EXTRA_X_PADDING_BYTES", parsed.xPaddingBytes);
                        if (parsed.xPaddingObfsMode) adapter.setValue("NETWORK_XHTTP_EXTRA_X_PADDING_OBFS_MODE", "True");
                        if (parsed.xPaddingHeader != null) adapter.setValue("NETWORK_XHTTP_EXTRA_X_PADDING_HEADER", parsed.xPaddingHeader);
                        if (parsed.xPaddingKey != null) adapter.setValue("NETWORK_XHTTP_EXTRA_X_PADDING_KEY", parsed.xPaddingKey);
                        if (parsed.xPaddingMethod != null) adapter.setValue("NETWORK_XHTTP_EXTRA_X_PADDING_METHOD", parsed.xPaddingMethod);
                        if (parsed.xPaddingPlacement != null) adapter.setValue("NETWORK_XHTTP_EXTRA_X_PADDING_PLACEMENT", parsed.xPaddingPlacement);
                        if (parsed.uplinkHTTPMethod != null) adapter.setValue("NETWORK_XHTTP_EXTRA_UPLINK_HTTP_METHOD", parsed.uplinkHTTPMethod);
                        if (parsed.sessionPlacement != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SESSION_PLACEMENT", parsed.sessionPlacement);
                        if (parsed.sessionKey != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SESSION_KEY", parsed.sessionKey);
                        if (parsed.seqPlacement != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SEQ_PLACEMENT", parsed.seqPlacement);
                        if (parsed.seqKey != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SEQ_KEY", parsed.seqKey);
                        if (parsed.uplinkDataPlacement != null) adapter.setValue("NETWORK_XHTTP_EXTRA_UPLINK_DATA_PLACEMENT", parsed.uplinkDataPlacement);
                        if (parsed.uplinkDataKey != null) adapter.setValue("NETWORK_XHTTP_EXTRA_UPLINK_DATA_KEY", parsed.uplinkDataKey);
                        if (parsed.uplinkChunkSize != null) adapter.setValue("NETWORK_XHTTP_EXTRA_UPLINK_CHUNK_SIZE", parsed.uplinkChunkSize);
                        if (Boolean.TRUE.equals(parsed.noGRPCHeader)) adapter.setValue("NETWORK_XHTTP_EXTRA_NO_GRPC_HEADER", "True");
                        if (Boolean.TRUE.equals(parsed.noSSEHeader)) adapter.setValue("NETWORK_XHTTP_EXTRA_NO_SSE_HEADER", "True");
                        if (parsed.scMaxEachPostBytes != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SC_MAX_EACH_POST_BYTES", parsed.scMaxEachPostBytes);
                        if (parsed.scMinPostsIntervalMs != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SC_MIN_POSTS_INTERVAL_MS", parsed.scMinPostsIntervalMs);
                        if (parsed.scStreamUpServerSecs != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SC_STREAM_UP_SERVER_SECS", parsed.scStreamUpServerSecs);
                        if (parsed.scMaxBufferedPosts != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SC_MAX_BUFFERED_POSTS", String.valueOf(parsed.scMaxBufferedPosts));
                        if (parsed.serverMaxHeaderBytes != null) adapter.setValue("NETWORK_XHTTP_EXTRA_SERVER_MAX_HEADER_BYTES", String.valueOf(parsed.serverMaxHeaderBytes));
                        if (parsed.xmux != null) {
                            adapter.setValue("NETWORK_XHTTP_EXTRA_XMUX", "Enabled");
                            if (parsed.xmux.maxConcurrency != null) adapter.setValue("NETWORK_XHTTP_EXTRA_XMUX_MAX_CONCURRENCY", parsed.xmux.maxConcurrency);
                            if (parsed.xmux.maxConnections != null) adapter.setValue("NETWORK_XHTTP_EXTRA_XMUX_MAX_CONNECTIONS", parsed.xmux.maxConnections);
                            if (parsed.xmux.cMaxReuseTimes != null) adapter.setValue("NETWORK_XHTTP_EXTRA_XMUX_C_MAX_REUSE_TIMES", parsed.xmux.cMaxReuseTimes);
                            if (parsed.xmux.hMaxRequestTimes != null) adapter.setValue("NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REQUEST_TIMES", parsed.xmux.hMaxRequestTimes);
                            if (parsed.xmux.hMaxReusableSecs != null) adapter.setValue("NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REUSABLE_SECS", parsed.xmux.hMaxReusableSecs);
                            if (parsed.xmux.hKeepAlivePeriod != null) adapter.setValue("NETWORK_XHTTP_EXTRA_XMUX_H_KEEPALIVE_PEROID", String.valueOf(parsed.xmux.hKeepAlivePeriod));
                        }
                        if (parsed.downloadSettings != null) {
                            adapter.setValue("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD", "True");
                            if (parsed.downloadSettings.address != null) adapter.setValue("NETWORK_XHTTP_EXTRA_DOWNLOAD_ADDRESS", parsed.downloadSettings.address);
                            if (parsed.downloadSettings.port != null) adapter.setValue("NETWORK_XHTTP_EXTRA_DOWNLOAD_PORT", String.valueOf(parsed.downloadSettings.port));
                            JsonObject downloadOutbound = new JsonObject();
                            downloadOutbound.addProperty("protocol", "xhttpstream");
                            downloadOutbound.add("settings", new JsonObject());
                            downloadOutbound.add("streamSettings", new Gson().toJsonTree(parsed.downloadSettings).getAsJsonObject());
                            xhttpDownload = new Gson().toJson(downloadOutbound);
                        }
                    }

                } else {
                    // Serialize individual fields into JSON
                    XHttpExtraSettings extra = new XHttpExtraSettings();
                    extra.headers = new HashMap<>();
                    if (manualHeaders != null && !manualHeaders.isBlank()) {
                        for (String line : manualHeaders.split("\\n")) {
                            if (line.isBlank()) continue;
                            String[] h = line.split(":", 2);
                            if (h.length < 2) continue;
                            extra.headers.put(h[0].strip(), h[1].strip());
                        }
                    }
                    if (extra.headers.isEmpty()) extra.headers = null;

                    extra.xPaddingBytes = manualXPaddingBytes;
                    extra.xPaddingObfsMode = "True".equals(manualXPaddingObfsMode);
                    extra.xPaddingHeader = manualXPaddingHeader;
                    extra.xPaddingKey = manualXPaddingKey;
                    extra.xPaddingMethod = manualXPaddingMethod;
                    extra.xPaddingPlacement = manualXPaddingPlacement;
                    extra.uplinkHTTPMethod = manualUplinkHTTPMethod;
                    extra.sessionPlacement = manualSessionPlacement;
                    extra.sessionKey = manualSessionKey;
                    extra.seqPlacement = manualSeqPlacement;
                    extra.seqKey = manualSeqKey;
                    extra.uplinkDataPlacement = manualUplinkDataPlacement;
                    extra.uplinkDataKey = manualUplinkDataKey;
                    extra.uplinkChunkSize = manualUplinkChunkSize;
                    extra.noGRPCHeader = "True".equals(manualNoGRPCHeader) ? true : null;
                    extra.noSSEHeader = "True".equals(manualNoSSEHeader) ? true : null;
                    extra.scMaxEachPostBytes = manualScMaxEachPostBytes;
                    extra.scMinPostsIntervalMs = manualScMinPostsIntervalMs;
                    extra.scStreamUpServerSecs = manualScStreamUpServerSecs;
                    if (manualScMaxBufferedPosts != null && !manualScMaxBufferedPosts.isBlank()) {
                        try { extra.scMaxBufferedPosts = Long.parseLong(manualScMaxBufferedPosts); } catch (NumberFormatException ignored) {}
                    }
                    if (manualServerMaxHeaderBytes != null && !manualServerMaxHeaderBytes.isBlank()) {
                        try { extra.serverMaxHeaderBytes = Integer.parseInt(manualServerMaxHeaderBytes); } catch (NumberFormatException ignored) {}
                    }
                    if ("Enabled".equals(manualXmux)) {
                        extra.xmux = new XMuxSettings();
                        extra.xmux.maxConcurrency = manualXmuxMaxConcurrency;
                        extra.xmux.maxConnections = manualXmuxMaxConnections;
                        extra.xmux.cMaxReuseTimes = manualXmuxCMaxReuseTimes;
                        extra.xmux.hMaxRequestTimes = manualXmuxHMaxRequestTimes;
                        extra.xmux.hMaxReusableSecs = manualXmuxHMaxReusableSecs;
                        if (manualXmuxHKeepAlivePeriod != null && !manualXmuxHKeepAlivePeriod.isBlank()) {
                            try { extra.xmux.hKeepAlivePeriod = Long.parseLong(manualXmuxHKeepAlivePeriod); } catch (NumberFormatException ignored) {}
                        }
                    }
                    if ("True".equals(manualSeparateDownload) && xhttpDownload != null && !xhttpDownload.isEmpty()) {
                        try {
                            Gson gson = new Gson();
                            JsonElement downloadSettings = gson.fromJson(xhttpDownload, JsonObject.class).get("streamSettings");
                            if (downloadSettings != null) {
                                extra.downloadSettings = gson.fromJson(downloadSettings, XHttpStreamSettings.class);
                                extra.downloadSettings.network = "xhttp";
                                extra.downloadSettings.address = manualDownloadAddress;
                                if (manualDownloadPort != null && !manualDownloadPort.isBlank()) {
                                    try { extra.downloadSettings.port = Integer.parseInt(manualDownloadPort); } catch (NumberFormatException ignored) {}
                                }
                                extra.downloadSettings.xHttpSettings = new XHttpSettings();
                                extra.downloadSettings.xHttpSettings.path = adapter.getValue("NETWORK_XHTTP_PATH");
                            }
                        } catch (JsonSyntaxException ignored) {}
                    }

                    adapter.addTextAreaInputAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_JSON", "XHTTP Extra", "XHTTP extra parameters in JSON format.");
                    adapter.addGroupTitleAfter("NETWORK_XHTTP_EXTRA", "NETWORK_XHTTP_EXTRA_TITLE", "XHTTP Extras");
                    adapter.setValue("NETWORK_XHTTP_EXTRA_JSON", new GsonBuilder().setPrettyPrinting().create().toJson(extra));

                }

                break;

            case "NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD":
                adapter.removeInputByPrefix("NETWORK_XHTTP_EXTRA_DOWNLOAD_");
                if (value.equals("True")) {
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD", "NETWORK_XHTTP_EXTRA_DOWNLOAD_PORT", "XHTTP Download Port");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD", "NETWORK_XHTTP_EXTRA_DOWNLOAD_ADDRESS", "XHTTP Download Address");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD", "NETWORK_XHTTP_EXTRA_DOWNLOAD_BTN", "XHTTP download stream settings", () -> {
                        Intent intent = new Intent(this, XHttpStreamActivity.class);
                        intent.putExtra("LABEL", "Download stream settings");
                        intent.putExtra("INLINE", true);
                        if (adapter.getValue("NETWORK_XHTTP_EXTRA_SEPARATE_DOWNLOAD").equals("True") && !xhttpDownload.isEmpty())
                            intent.putExtra("CONFIG", xhttpDownload);
                        startActivityForResult(intent, 2);
                    });
                }
                break;

            case "NETWORK_XHTTP_EXTRA_XMUX":
                adapter.removeInputByPrefix("NETWORK_XHTTP_EXTRA_XMUX_");
                if (value.equals("Enabled")) {
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_XMUX", "NETWORK_XHTTP_EXTRA_XMUX_H_KEEPALIVE_PEROID", "XMUX hKeepAlivePeriod", "0");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_XMUX", "NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REUSABLE_SECS", "XMUX hMaxReusableSecs", "1800-3000");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_XMUX", "NETWORK_XHTTP_EXTRA_XMUX_H_MAX_REQUEST_TIMES", "XMUX hMaxRequestTimes", "600-900");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_XMUX", "NETWORK_XHTTP_EXTRA_XMUX_C_MAX_REUSE_TIMES", "XMUX cMaxReuseTimes", "0");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_XMUX", "NETWORK_XHTTP_EXTRA_XMUX_MAX_CONNECTIONS", "XMUX maxConnections", "0");
                    adapter.addInputAfter("NETWORK_XHTTP_EXTRA_XMUX", "NETWORK_XHTTP_EXTRA_XMUX_MAX_CONCURRENCY", "XMUX maxConcurrency", "1-1");
                }
                break;

            case "MUX_ENABLED":
                adapter.removeInput("MUX_CONCURRENCY");
                adapter.removeInput("MUX_XUDP_CONCURRENCY");
                adapter.removeInput("MUX_XUDP_PROXY_UDP443");
                if (value.equals("enabled")) {
                    adapter.addInputAfter("MUX_ENABLED", "MUX_XUDP_PROXY_UDP443", "XUDP Proxy UDP 443", List.of("reject", "skip"));
                    adapter.addInputAfter("MUX_ENABLED", "MUX_XUDP_CONCURRENCY", "XUDP Concurrency", "16");
                    adapter.addInputAfter("MUX_ENABLED", "MUX_CONCURRENCY", "Mux Concurrency", "4");
                }
                break;
            case "NETWORK_TCP_HEADER":
                adapter.removeInputByPrefix("NETWORK_TCP_HEADER_");
                if (value.equals("http")) {
                    adapter.addInputAfter("NETWORK_TCP_HEADER", "NETWORK_TCP_HEADER_HTTP_HOST", "TCP Header HTTP Host");
                }
                break;
            case "NETWORK_HYSTERIA_HOP":
                adapter.removeInputByPrefix("NETWORK_HYSTERIA_HOP_");
                if (value.equals("True")) {
                    adapter.addInputAfter("NETWORK_HYSTERIA_HOP", "NETWORK_HYSTERIA_HOP_PORTS", "Hysteria UDP Hop Ports", "2000-2100");
                    adapter.addInputAfter("NETWORK_HYSTERIA_HOP", "NETWORK_HYSTERIA_HOP_INTERVAL", "Hysteria UDP Hop Interval", "30", "Seconds");
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null) return;

        // xhttp download
        if (resultCode == RESULT_OK && requestCode == 2) {
            xhttpDownload = data.getStringExtra("CONFIG");
        }
    }
}