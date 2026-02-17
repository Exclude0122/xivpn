package cn.gov.xivpn2.service.sharelink;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.StreamSettings;
import cn.gov.xivpn2.xrayconfig.VmessServerSettings;
import cn.gov.xivpn2.xrayconfig.VmessSettings;
import cn.gov.xivpn2.xrayconfig.VmessShare;
import cn.gov.xivpn2.xrayconfig.VmessUser;

/**
 * VMess share link parser.
 * <p>
 * Supports two formats for parsing:
 * <ul>
 *     <li>Standard URI format (per XTLS spec): {@code vmess://uuid@host:port?params#label}</li>
 *     <li>Legacy v2rayN format: {@code vmess://base64json}</li>
 * </ul>
 * <p>
 * Marshaling always outputs the standard URI format.
 */
public class VMessShareLinkParser extends BaseVMessVLessParser {


    @Override
    public Proxy parse(String line) throws BadShareLinkException {
        if (!line.startsWith("vmess://")) {
            throw new IllegalArgumentException("not a vmess URI: " + line);
        }

        try {
            URI uri = new URI(line);

            Proxy proxy = new Proxy();
            proxy.label = URLDecoder.decode(nullable(uri.getFragment(), "VMess"), "UTF-8");
            proxy.protocol = "vmess";

            Map<String, String> query = splitQuery(uri.getRawQuery());

            // protocol settings
            Outbound<VmessSettings> outbound = new Outbound<>();
            outbound.protocol = "vmess";
            outbound.settings = new VmessSettings();

            VmessServerSettings server = new VmessServerSettings();
            server.address = uri.getHost();
            server.port = uri.getPort();

            VmessUser vmessUser = new VmessUser();
            vmessUser.id = nullable(uri.getUserInfo(), "");
            vmessUser.security = query.getOrDefault("encryption", "auto");

            server.users.add(vmessUser);
            outbound.settings.vnext.add(server);

            // stream settings (transport + security)
            outbound.streamSettings = parseStreamSettings(query, uri.getHost());

            proxy.config = new Gson().toJson(outbound);
            return proxy;
        } catch (Exception e) {
            throw new BadShareLinkException(e);
        }
    }


    @Override
    public String marshal(Proxy proxy) throws MarshalProxyException {
        Outbound<VmessSettings> outbound = new Gson().fromJson(
                proxy.config, new TypeToken<Outbound<VmessSettings>>() {
                });

        VmessServerSettings server = outbound.settings.vnext.get(0);
        VmessUser vmessUser = server.users.get(0);
        StreamSettings streamSettings = outbound.streamSettings;

        // Build query params from stream settings
        Map<String, String> queries = marshalStreamSettingsQueries(streamSettings);

        // VMess-specific: encryption (omit if "auto" per spec recommendation)
        String encryption = nullable(vmessUser.security, "auto");
        if (!encryption.equals("auto")) {
            queries.put("encryption", encryption);
        }

        // Reorder: put encryption first, then the rest
        Map<String, String> orderedQueries = new LinkedHashMap<>();
        if (queries.containsKey("encryption")) {
            orderedQueries.put("encryption", queries.remove("encryption"));
        }
        orderedQueries.putAll(queries);

        return "vmess://"
                + vmessUser.id
                + "@" + quoteHost(server.address) + ":" + server.port
                + queryFromMap(orderedQueries)
                + "#" + quote(proxy.label);
    }
}
