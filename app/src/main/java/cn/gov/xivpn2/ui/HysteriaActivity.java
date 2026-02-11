package cn.gov.xivpn2.ui;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;

import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.xrayconfig.HysteriaSettings;
import cn.gov.xivpn2.xrayconfig.Outbound;

public class HysteriaActivity extends ProxyActivity<HysteriaSettings> {

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
        return new TypeToken<Outbound<HysteriaSettings>>() {
        }.getType();
    }

    @Override
    protected HysteriaSettings buildProtocolSettings(IProxyEditor adapter) {
        HysteriaSettings settings = new HysteriaSettings();
        settings.version = 2;
        settings.address = adapter.getValue("ADDRESS");
        settings.port = Integer.parseInt(adapter.getValue("PORT"));
        return settings;
    }

    @Override
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<HysteriaSettings> outbound) {
        LinkedHashMap<String, String> hashMap = super.decodeOutboundConfig(outbound);
        hashMap.put("ADDRESS", outbound.settings.address);
        hashMap.put("PORT", String.valueOf(outbound.settings.port));
        return hashMap;
    }

    @Override
    protected String getProtocolName() {
        return "hysteria";
    }

    @Override
    protected void initializeInputs(IProxyEditor adapter) {
        adapter.addInput("ADDRESS", "Address");
        adapter.addInput("PORT", "Port");
    }

}
