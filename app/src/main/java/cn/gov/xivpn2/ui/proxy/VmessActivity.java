package cn.gov.xivpn2.ui.proxy;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;

import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.VmessServerSettings;
import cn.gov.xivpn2.xrayconfig.VmessSettings;
import cn.gov.xivpn2.xrayconfig.VmessUser;

public class VmessActivity extends ProxyActivity<VmessSettings> {

    @Override
    protected boolean validateField(String key, String value) {
        switch (key) {
            case "ADDRESS":
            case "UUID":
                return !value.isEmpty();
            case "PORT":
                return Utils.isValidPort(value);
        }
        return super.validateField(key, value);
    }

    @Override
    protected Type getType() {
        return new TypeToken<Outbound<VmessSettings>>() {
        }.getType();
    }

    @Override
    protected VmessSettings buildProtocolSettings(IProxyEditor adapter) {
        VmessSettings vmessSettings = new VmessSettings();

        VmessServerSettings vnext = new VmessServerSettings();
        vnext.address = adapter.getValue("ADDRESS");
        vnext.port = Integer.parseInt(adapter.getValue("PORT"));

        VmessUser user = new VmessUser();
        user.id = adapter.getValue("UUID");
        user.security = adapter.getValue("VMESS_SECURITY");
        vnext.users.add(user);

        vmessSettings.vnext.add(vnext);

        return vmessSettings;
    }

    @Override
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<VmessSettings> outbound) {
        LinkedHashMap<String, String> hashMap = super.decodeOutboundConfig(outbound);
        VmessServerSettings vnext = outbound.settings.vnext.get(0);
        hashMap.put("ADDRESS", vnext.address);
        hashMap.put("PORT", String.valueOf(vnext.port));
        hashMap.put("VMESS_SECURITY", vnext.users.get(0).security);
        hashMap.put("UUID", vnext.users.get(0).id);
        return hashMap;
    }

    @Override
    protected String getProtocolName() {
        return "vmess";
    }

    @Override
    protected void initializeInputs(IProxyEditor adapter) {
        adapter.addInput("ADDRESS", "Address");
        adapter.addInput("PORT", "Port");
        adapter.addInput("VMESS_SECURITY", "VMESS Security", List.of("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"));
        adapter.addInput("UUID", "UUID");
    }
}
