package cn.gov.xivpn2.ui;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;

import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.xrayconfig.HttpSettings;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.VmessServerSettings;
import cn.gov.xivpn2.xrayconfig.VmessSettings;
import cn.gov.xivpn2.xrayconfig.VmessUser;

public class HttpActivity extends ProxyActivity<HttpSettings> {

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
        return new TypeToken<Outbound<HttpSettings>>() {
        }.getType();
    }

    @Override
    protected HttpSettings buildProtocolSettings(IProxyEditor adapter) {
        HttpSettings httpSettings = new HttpSettings();

        httpSettings.address = adapter.getValue("ADDRESS");
        httpSettings.port = Integer.parseInt(adapter.getValue("PORT"));
        if ("basic".equals(adapter.getValue("HTTP_AUTH"))) {
            httpSettings.user = adapter.getValue("USER");
            httpSettings.pass = adapter.getValue("PASS");
        } else {
            httpSettings.pass = null;
            httpSettings.user = null;
        }

        return httpSettings;
    }

    @Override
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<HttpSettings> outbound) {
        LinkedHashMap<String, String> hashMap = super.decodeOutboundConfig(outbound);
        hashMap.put("ADDRESS", outbound.settings.address);
        hashMap.put("PORT", String.valueOf(outbound.settings.port));
        if (outbound.settings.user != null && outbound.settings.pass != null) {
            hashMap.put("HTTP_AUTH", "basic");
            hashMap.put("USER", outbound.settings.user);
            hashMap.put("PASS", outbound.settings.pass);
        } else {
            hashMap.put("HTTP_AUTH", "none");
        }
        return hashMap;
    }

    @Override
    protected String getProtocolName() {
        return "http";
    }

    @Override
    protected void initializeInputs(IProxyEditor adapter) {
        adapter.addInput("ADDRESS", "Address");
        adapter.addInput("PORT", "Port");
        adapter.addInput("HTTP_AUTH", "Auth", List.of("none", "basic"));
    }

    @Override
    protected void onInputChanged(IProxyEditor adapter, String key, String value) {
        super.onInputChanged(adapter, key, value);

        if ("HTTP_AUTH".equals(key)) {
            adapter.removeInput("USER");
            adapter.removeInput("PASS");
            if ("basic".equals(adapter.getValue("HTTP_AUTH"))) {
                adapter.addInputAfter("HTTP_AUTH", "PASS", "Pass");
                adapter.addInputAfter("HTTP_AUTH", "USER", "User");
            }
        }

    }
}
