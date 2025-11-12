package cn.gov.xivpn2.ui;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;

import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.VlessServerSettings;
import cn.gov.xivpn2.xrayconfig.VlessSettings;
import cn.gov.xivpn2.xrayconfig.VlessUser;

public class VlessActivity extends ProxyActivity<VlessSettings> {

    @Override
    protected boolean validateField(String key, String value) {
        switch (key) {
            case "ADDRESS":
            case "UUID":
            case "VLESS_ENCRYPTION":
                return !value.isEmpty();
            case "PORT":
                return Utils.isValidPort(value);
        }
        return super.validateField(key, value);
    }

    @Override
    protected Type getType() {
        return new TypeToken<Outbound<VlessSettings>>() {
        }.getType();
    }

    @Override
    protected VlessSettings buildProtocolSettings(IProxyEditor adapter) {
        VlessSettings vlessSettings = new VlessSettings();

        VlessServerSettings vnext = new VlessServerSettings();
        vnext.address = adapter.getValue("ADDRESS");
        vnext.port = Integer.parseInt(adapter.getValue("PORT"));

        VlessUser user = new VlessUser();
        user.id = adapter.getValue("UUID");
        String flow = adapter.getValue("FLOW");
        if (!flow.equals("none")) {
            user.flow = flow;
        } else {
            user.flow = "";
        }
        user.encryption = adapter.getValue("VLESS_ENCRYPTION");
        vnext.users.add(user);

        vlessSettings.vnext.add(vnext);

        return vlessSettings;
    }

    @Override
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<VlessSettings> outbound) {
        LinkedHashMap<String, String> hashMap = super.decodeOutboundConfig(outbound);
        VlessServerSettings vnext = outbound.settings.vnext.get(0);
        hashMap.put("ADDRESS", vnext.address);
        hashMap.put("PORT", String.valueOf(vnext.port));
        String flow = vnext.users.get(0).flow;
        if (flow == null || flow.isEmpty()) {
            hashMap.put("FLOW", "none");
        } else {
            hashMap.put("FLOW", flow);
        }
        hashMap.put("VLESS_ENCRYPTION", vnext.users.get(0).encryption);
        hashMap.put("UUID", vnext.users.get(0).id);
        return hashMap;
    }

    @Override
    protected String getProtocolName() {
        return "vless";
    }

    @Override
    protected void initializeInputs(IProxyEditor adapter) {
        adapter.addInput("ADDRESS", "Address");
        adapter.addInput("PORT", "Port");
        adapter.addInput("FLOW", "Flow", List.of("none", "xtls-rprx-vision", "xtls-rprx-vision-udp443"));
        adapter.addInput("UUID", "UUID");
        adapter.addInput("VLESS_ENCRYPTION", "VLESS Encryption");
    }
}
