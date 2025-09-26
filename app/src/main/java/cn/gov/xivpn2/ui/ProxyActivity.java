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

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.ProxyDao;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.HttpUpgradeSettings;
import cn.gov.xivpn2.xrayconfig.MuxSettings;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.QuicSettings;
import cn.gov.xivpn2.xrayconfig.RawHeader;
import cn.gov.xivpn2.xrayconfig.RawHttpHeaderRequest;
import cn.gov.xivpn2.xrayconfig.RawSettings;
import cn.gov.xivpn2.xrayconfig.RealitySettings;
import cn.gov.xivpn2.xrayconfig.StreamSettings;
import cn.gov.xivpn2.xrayconfig.TLSSettings;
import cn.gov.xivpn2.xrayconfig.WsSettings;
import cn.gov.xivpn2.xrayconfig.XHttpSettings;

public abstract class ProxyActivity<T> extends AppCompatActivity {

    private final static String TAG = "ProxyActivity";

    private ProxyEditTextAdapter adapter;

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
            adapter.addInput("NETWORK", "Network", Arrays.asList("tcp", "ws", "quic", "httpupgrade", "xhttp"));
            adapter.addInputAfter("NETWORK", "NETWORK_TCP_HEADER", "TCP Header", Arrays.asList("none", "http"));
            adapter.addGroupTitle("GROUP_SECURITY", "Security");
            adapter.addInput("SECURITY", "Security", Arrays.asList("none", "tls", "reality"));
        }
        adapter.addGroupTitle("GROUP_MUX", "Multiplex");
        adapter.addInput("MUX_ENABLED", "Multiplex", Arrays.asList("disabled", "enabled"));

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
        if (key.equals("NETWORK_XHTTP_DOWNLOAD_BTN") && adapter.getValue("NETWORK_XHTTP_SEPARATE_DOWNLOAD").equals("True")) {
            return !xhttpDownload.isEmpty();
        }
        switch (key) {
            case "SECURITY_REALITY_PUBLIC_KEY":
            case "NETWORK_XHTTP_DOWNLOAD_ADDRESS":
                return !value.isEmpty();
            case "NETWORK_XHTTP_DOWNLOAD_PORT":
                return Utils.isValidPort(value);
            case "MUX_XUDP_CONCURRENCY":
            case "MUX_CONCURRENCY":
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return false;
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

        if (!hasStreamSettings()) return outbound;

        outbound.streamSettings = new StreamSettings();
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
            case "quic":
                outbound.streamSettings.quicSettings = new QuicSettings();
                outbound.streamSettings.quicSettings.header.type = adapter.getValue("NETWORK_QUIC_HEADER");
                outbound.streamSettings.quicSettings.security = adapter.getValue("NETWORK_QUIC_SECURITY");
                if (!outbound.streamSettings.quicSettings.security.equals("none")) {
                    outbound.streamSettings.quicSettings.key = adapter.getValue("NETWORK_QUIC_KEY");
                }
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
                if (xhttpDownload != null && !xhttpDownload.isEmpty() && adapter.getValue("NETWORK_XHTTP_SEPARATE_DOWNLOAD").equals("True")) {
                    Type type = new TypeToken<Map<String, Object>>() {
                    }.getType();
                    Gson gson = new GsonBuilder().create();
                    Map<String, Object> downloadConfig = gson.fromJson(xhttpDownload, type);
                    if (downloadConfig.get("streamSettings") instanceof Map) {
                        outbound.streamSettings.xHttpSettings.downloadSettings = ((Map<String, Object>) downloadConfig.get("streamSettings"));
                        outbound.streamSettings.xHttpSettings.downloadSettings.put("network", "xhttp");
                        outbound.streamSettings.xHttpSettings.downloadSettings.put("address", adapter.getValue("NETWORK_XHTTP_DOWNLOAD_ADDRESS"));
                        outbound.streamSettings.xHttpSettings.downloadSettings.put("port", Integer.parseInt(adapter.getValue("NETWORK_XHTTP_DOWNLOAD_PORT")));
                        Map<String, String> downloadXhttpSettings = new HashMap<>();
                        downloadXhttpSettings.put("path", adapter.getValue("NETWORK_XHTTP_PATH"));
                        outbound.streamSettings.xHttpSettings.downloadSettings.put("xhttpSettings", downloadXhttpSettings);
                    }
                }
        }

        String security = this.adapter.getValue("SECURITY");
        if (security.equals("tls")) {
            outbound.streamSettings.security = "tls";
            outbound.streamSettings.tlsSettings = new TLSSettings();
            outbound.streamSettings.tlsSettings.allowInsecure = adapter.getValue("SECURITY_TLS_INSECURE").equals("True");
            if (adapter.getValue("SECURITY_TLS_FINGERPRINT").equals("None")) {
                outbound.streamSettings.tlsSettings.fingerprint = "";
            } else {
                outbound.streamSettings.tlsSettings.fingerprint = adapter.getValue("SECURITY_TLS_FINGERPRINT");
            }
            outbound.streamSettings.tlsSettings.serverName = adapter.getValue("SECURITY_TLS_SNI");
            outbound.streamSettings.tlsSettings.alpn = adapter.getValue("SECURITY_TLS_ALPN").split(",");
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

        if (this.adapter.getValue("MUX_ENABLED").equals("enabled")) {
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
            case "quic":
                initials.put("NETWORK_QUIC_HEADER", outbound.streamSettings.quicSettings.header.type);
                initials.put("NETWORK_QUIC_SECURITY", outbound.streamSettings.quicSettings.security);
                if (!outbound.streamSettings.quicSettings.security.equals("none")) {
                    initials.put("NETWORK_QUIC_KEY", outbound.streamSettings.quicSettings.key);
                }
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
                if (outbound.streamSettings.xHttpSettings.downloadSettings != null) {
                    initials.put("NETWORK_XHTTP_SEPARATE_DOWNLOAD", "True");
                    initials.put("NETWORK_XHTTP_DOWNLOAD_ADDRESS", ((String) outbound.streamSettings.xHttpSettings.downloadSettings.get("address")));
                    initials.put("NETWORK_XHTTP_DOWNLOAD_PORT", (String.valueOf(((Double) outbound.streamSettings.xHttpSettings.downloadSettings.get("port")).intValue())));

                    JsonObject downloadOutbound = new JsonObject();
                    downloadOutbound.addProperty("protocol", "xhttpstream");
                    downloadOutbound.add("settings", new JsonObject());
                    JsonObject downloadStream = new Gson().toJsonTree(outbound.streamSettings.xHttpSettings.downloadSettings).getAsJsonObject();
                    downloadOutbound.add("streamSettings", downloadStream);
                    xhttpDownload = new Gson().toJson(downloadOutbound);

                    Log.i(TAG, "decode xhttp: " + xhttpDownload);
                }
                break;
        }

        if (outbound.streamSettings.security == null || outbound.streamSettings.security.isEmpty()) {

        } else if (outbound.streamSettings.security.equals("tls")) {
            initials.put("SECURITY", "tls");
            initials.put("SECURITY_TLS_SNI", outbound.streamSettings.tlsSettings.serverName);
            initials.put("SECURITY_TLS_ALPN", String.join(",", outbound.streamSettings.tlsSettings.alpn));
            initials.put("SECURITY_TLS_INSECURE", outbound.streamSettings.tlsSettings.allowInsecure ? "True" : "False");
            if (outbound.streamSettings.tlsSettings.fingerprint.isEmpty()) {
                initials.put("SECURITY_TLS_FINGERPRINT", "None");
            } else {
                initials.put("SECURITY_TLS_FINGERPRINT", outbound.streamSettings.tlsSettings.fingerprint);
            }
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
                    case "quic":
                        adapter.addInputAfter("NETWORK", "NETWORK_QUIC_HEADER", "QUIC Header", Arrays.asList("none", "srtp", "utp", "wechat-video", "dtls", "wireguard"));
                        adapter.addInputAfter("NETWORK", "NETWORK_QUIC_SECURITY", "QUIC Security", Arrays.asList("none", "aes-128-gcm", "chacha20-poly1305"));
                        break;
                    case "httpupgrade":
                        adapter.addInputAfter("NETWORK", "NETWORK_HTTPUPGRADE_PATH", "HttpUpgrade Path", "/");
                        adapter.addInputAfter("NETWORK", "NETWORK_HTTPUPGRADE_HOST", "HttpUpgrade Host");
                        break;
                    case "xhttp":
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_HOST", "XHTTP Host", "");
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_PATH", "XHTTP Path", "/");
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_MODE", "XHTTP Mode", List.of("packet-up", "stream-up", "auto", "stream-one"));
                        break;
                }
                break;
            case "NETWORK_QUIC_SECURITY":
                if (value.equals("none")) {
                    adapter.removeInput("NETWORK_QUIC_KEY");
                } else if (!adapter.exists("NETWORK_QUIC_KEY")) {
                    adapter.addInputAfter("NETWORK", "NETWORK_QUIC_KEY", "QUIC Encryption Key");
                }
                break;
            case "SECURITY":
                adapter.removeInputByPrefix("SECURITY_");
                if (value.equals("tls")) {
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_SNI", "TLS Server Name");
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_ALPN", "TLS ALPN", "h2,http/1.1");
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_INSECURE", "TLS Allow Insecure", List.of("False", "True"));
                    adapter.addInputAfter("SECURITY", "SECURITY_TLS_FINGERPRINT", "TLS Fingerprint", List.of("None", "chrome", "firefox", "random", "randomized"));
                } else if (value.equals("reality")) {
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_MLDSA65VERIFY", "REALITY MLDSA65 Public Key");
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_SNI", "REALITY Server Name");
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_FINGERPRINT", "REALITY Fingerprint", List.of("chrome", "firefox", "random", "randomized"));
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_SHORTID", "REALITY Short ID");
                    adapter.addInputAfter("SECURITY", "SECURITY_REALITY_PUBLIC_KEY", "REALITY Public Key");
                }
                break;

            case "NETWORK_XHTTP_MODE":
                if (!value.equals("stream-one")) {
                    if (!adapter.exists("NETWORK_XHTTP_SEPARATE_DOWNLOAD")) {
                        adapter.addInputAfter("NETWORK", "NETWORK_XHTTP_SEPARATE_DOWNLOAD", "XHTTP Separate Download", List.of("False", "True"));
                    }
                } else {
                    adapter.removeInput("NETWORK_XHTTP_SEPARATE_DOWNLOAD");
                    adapter.removeInputByPrefix("NETWORK_XHTTP_DOWNLOAD_");
                }
                break;

            case "NETWORK_XHTTP_SEPARATE_DOWNLOAD":
                adapter.removeInputByPrefix("NETWORK_XHTTP_DOWNLOAD_BTN");
                adapter.removeInputByPrefix("NETWORK_XHTTP_DOWNLOAD_ADDRESS");
                adapter.removeInputByPrefix("NETWORK_XHTTP_DOWNLOAD_PORT");
                if (value.equals("True")) {
                    adapter.addInputAfter("NETWORK_XHTTP_SEPARATE_DOWNLOAD", "NETWORK_XHTTP_DOWNLOAD_PORT", "XHTTP Download Port");
                    adapter.addInputAfter("NETWORK_XHTTP_SEPARATE_DOWNLOAD", "NETWORK_XHTTP_DOWNLOAD_ADDRESS", "XHTTP Download Address");
                    adapter.addInputAfter("NETWORK_XHTTP_SEPARATE_DOWNLOAD", "NETWORK_XHTTP_DOWNLOAD_BTN", "XHTTP download stream settings", () -> {
                        Intent intent = new Intent(this, XHttpStreamActivity.class);
                        intent.putExtra("LABEL", "Download stream settings");
                        intent.putExtra("INLINE", true);
                        if (adapter.getValue("NETWORK_XHTTP_SEPARATE_DOWNLOAD").equals("True") && !xhttpDownload.isEmpty())
                            intent.putExtra("CONFIG", xhttpDownload);
                        startActivityForResult(intent, 2);
                    });
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