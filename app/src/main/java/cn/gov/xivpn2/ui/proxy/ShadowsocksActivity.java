package cn.gov.xivpn2.ui.proxy;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;

import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.ShadowsocksServerSettings;
import cn.gov.xivpn2.xrayconfig.ShadowsocksSettings;

public class ShadowsocksActivity extends ProxyActivity<ShadowsocksSettings> {

    @Override
    protected ShadowsocksSettings buildProtocolSettings(IProxyEditor adapter) {
        ShadowsocksSettings shadowsocksSettings = new ShadowsocksSettings();
        ShadowsocksServerSettings server = new ShadowsocksServerSettings();
        server.address = adapter.getValue("ADDRESS");
        server.port = Integer.parseInt(adapter.getValue("PORT"));
        server.password = adapter.getValue("PASSWORD");
        server.method = adapter.getValue("METHOD");
        server.out = false;
        if (!adapter.getValue("UDP_OVER_TCP").equals("Disabled")) {
            server.out = true;
            server.UoTVersion = adapter.getValue("UDP_OVER_TCP").equals("Version 1") ? 1 : 2;
        }
        shadowsocksSettings.servers.add(server);
        return shadowsocksSettings;
    }

    @Override
    protected boolean validateField(String key, String value) {
        switch (key) {
            case "ADDRESS":
            case "PASSWORD":
                return !value.isEmpty();
            case "PORT":
                return Utils.isValidPort(value);
        }
        return super.validateField(key, value);
    }

    @Override
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<ShadowsocksSettings> outbound) {
        LinkedHashMap<String, String> hashMap = super.decodeOutboundConfig(outbound);

        ShadowsocksServerSettings server = outbound.settings.servers.get(0);
        hashMap.put("PORT", String.valueOf(server.port));
        hashMap.put("ADDRESS", server.address);
        hashMap.put("METHOD", server.method);
        hashMap.put("PASSWORD", server.password);
        if (server.out) {
            hashMap.put("UDP_OVER_TCP", server.UoTVersion == 1 ? "Version 1" : "Version 2");
        } else {
            hashMap.put("UDP_OVER_TCP", "Disabled");
        }

        return hashMap;
    }

    @Override
    protected Type getType() {
        return new TypeToken<Outbound<ShadowsocksSettings>>() {
        }.getType();
    }

    @Override
    protected String getProtocolName() {
        return "shadowsocks";
    }

    @Override
    protected void initializeInputs(IProxyEditor adapter) {
        adapter.addInput("ADDRESS", "Address");
        adapter.addInput("PORT", "Port");
        adapter.addInput("PASSWORD", "Password");
        adapter.addInput("METHOD", "Method", Arrays.asList("2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305", "aes-256-gcm", "aes-128-gcm", "chacha20-poly1305", "xchacha20-poly1305", "none"));
        adapter.addInput("UDP_OVER_TCP", "UDP over TCP", Arrays.asList("Disabled", "Version 1", "Version 2"));
    }

    @Override
    protected void onInputChanged(IProxyEditor adapter, String key, String value) {
        super.onInputChanged(adapter, key, value);
    }

}
