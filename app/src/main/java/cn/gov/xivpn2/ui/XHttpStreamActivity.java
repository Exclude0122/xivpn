package cn.gov.xivpn2.ui;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;

import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.XHttpSettings;
import cn.gov.xivpn2.xrayconfig.XHttpStream;

public class XHttpStreamActivity extends ProxyActivity<XHttpSettings> {

    @Override
    protected XHttpSettings buildProtocolSettings(IProxyEditor adapter) {
        return new XHttpSettings();
    }

    @Override
    protected LinkedHashMap<String, String> decodeOutboundConfig(Outbound<XHttpSettings> outbound) {
        return super.decodeOutboundConfig(outbound);
    }

    @Override
    protected Type getType() {
        return new TypeToken<Outbound<XHttpStream>>() {
        }.getType();
    }

    @Override
    protected String getProtocolName() {
        return "xhttpstream";
    }

    @Override
    protected void initializeInputs(IProxyEditor adapter) {
    }

    @Override
    protected void afterInitializeInputs(IProxyEditor adapter) {
        adapter.removeInputByPrefix("NETWORK");
        adapter.removeInputByPrefix("GROUP");
        adapter.removeInputByPrefix("MUX_");
    }

    @Override
    protected void onInputChanged(IProxyEditor adapter, String key, String value) {
        super.onInputChanged(adapter, key, value);
    }

}
