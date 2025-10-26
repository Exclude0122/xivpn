package cn.gov.xivpn2.ui;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;

import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.Socks5Settings;

public class Socks5Activity extends ProxyActivity<Socks5Settings> {

    @Override
    protected boolean validateField(String key, String value) {
        switch (key) {
            case "ADDRESS":
                return !value.isEmpty();
            case "PORT":
                return Utils.isValidPort(value);
        }
        return super.validateField(key, value);
    }

    @Override
    protected Type getType() {
        return new TypeToken<Outbound<Socks5Settings>>() {
        }.getType();
    }

    @Override
    protected Socks5Settings buildProtocolSettings(IProxyEditor adapter) {
        Socks5Settings socks5Settings = new Socks5Settings();

        socks5Settings.address = adapter.getValue("ADDRESS");
        socks5Settings.port = Integer.parseInt(adapter.getValue("PORT"));
        if ("password".equals(adapter.getValue("SOCKS5_AUTH"))) {
            socks5Settings.user = adapter.getValue("USER");
            socks5Settings.pass = adapter.getValue("PASS");
        } else {
            socks5Settings.pass = null;
            socks5Settings.user = null;
        }

        return socks5Settings;
    }

    @Override
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<Socks5Settings> outbound) {
        LinkedHashMap<String, String> hashMap = super.decodeOutboundConfig(outbound);
        hashMap.put("ADDRESS", outbound.settings.address);
        hashMap.put("PORT", String.valueOf(outbound.settings.port));
        if (outbound.settings.user != null && outbound.settings.pass != null) {
            hashMap.put("SOCKS5_AUTH", "password");
            hashMap.put("USER", outbound.settings.user);
            hashMap.put("PASS", outbound.settings.pass);
        } else {
            hashMap.put("SOCKS5_AUTH", "none");
        }
        return hashMap;
    }

    @Override
    protected String getProtocolName() {
        return "socks";
    }

    @Override
    protected void initializeInputs(IProxyEditor adapter) {
        adapter.addInput("ADDRESS", "Address");
        adapter.addInput("PORT", "Port");
        adapter.addInput("SOCKS5_AUTH", "Auth", List.of("none", "password"));
    }

    @Override
    protected void onInputChanged(IProxyEditor adapter, String key, String value) {
        super.onInputChanged(adapter, key, value);

        if ("SOCKS5_AUTH".equals(key)) {
            adapter.removeInput("USER");
            adapter.removeInput("PASS");
            if ("password".equals(adapter.getValue("SOCKS5_AUTH"))) {
                adapter.addInputAfter("SOCKS5_AUTH", "PASS", "Pass");
                adapter.addInputAfter("SOCKS5_AUTH", "USER", "User");
            }
        }

    }
}
