package cn.gov.xivpn2.service.sharelink;

import static cn.gov.xivpn2.service.sharelink.BaseVMessVLessParser.nullable;
import static cn.gov.xivpn2.service.sharelink.BaseVMessVLessParser.quote;
import static cn.gov.xivpn2.service.sharelink.BaseVMessVLessParser.quoteHost;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.ShadowsocksServerSettings;
import cn.gov.xivpn2.xrayconfig.ShadowsocksSettings;
import cn.gov.xivpn2.xrayconfig.StreamSettings;

public class ShadowsocksShareLinkParser implements ShareLinkParser {
    @Override
    public Proxy parse(String line) throws BadShareLinkException {

        try {
            Proxy proxy = new Proxy();
            proxy.protocol = "shadowsocks";

            URI uri = URI.create(line);

            String userInfo = nullable(uri.getRawUserInfo(), "");
            String hostname = uri.getHost();
            String port = String.valueOf(uri.getPort());
            String label = nullable(uri.getRawFragment(), "SS");

            Outbound<ShadowsocksSettings> outbound = new Outbound<>();
            outbound.settings = new ShadowsocksSettings();
            outbound.protocol = "shadowsocks";

            // network
            outbound.streamSettings = new StreamSettings();
            outbound.streamSettings.network = "tcp";
            outbound.streamSettings.security = "none";

            // shadowssocks server
            ShadowsocksServerSettings server = new ShadowsocksServerSettings();
            outbound.settings.servers.add(server);

            if (!userInfo.contains(":")) {
                userInfo = new String(Base64.decode(userInfo, Base64.URL_SAFE), StandardCharsets.UTF_8);
            } else {
                // userinfo is percent encoded if it is not base64 encoded
                userInfo = URLDecoder.decode(userInfo, "UTF-8");
            }

            String[] parts = userInfo.split(":", 2);
            if (parts.length != 2) {
                throw new BadShareLinkException("bad userinfo");
            }

            server.method = parts[0];
            server.password = parts[1];

            server.address = hostname;
            server.port = Integer.parseInt(port);
            server.out = false;

            proxy.label = URLDecoder.decode(label, "UTF-8");
            proxy.config = new Gson().toJson(outbound);

            return proxy;
        } catch (Exception e) {
            throw new BadShareLinkException(e);
        }
    }

    @Override
    public String marshal(Proxy proxy) throws MarshalProxyException {
        Outbound<ShadowsocksSettings> outbound = new Gson().fromJson(proxy.config, new TypeToken<>() {});

        ShadowsocksServerSettings server = outbound.settings.servers.get(0);

        return "ss://"
                + server.method + ":" + quote(server.password)
                + "@" + quoteHost(server.address) + ":" + server.port
                + "#" + quote(proxy.label);
    }
}
