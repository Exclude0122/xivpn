package cn.gov.xivpn2.service.sharelink;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.xrayconfig.GRPCSettings;
import cn.gov.xivpn2.xrayconfig.KcpSettings;
import cn.gov.xivpn2.xrayconfig.MuxSettings;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.StreamSettings;
import cn.gov.xivpn2.xrayconfig.TLSSettings;
import cn.gov.xivpn2.xrayconfig.VmessServerSettings;
import cn.gov.xivpn2.xrayconfig.VmessSettings;
import cn.gov.xivpn2.xrayconfig.VmessShare;
import cn.gov.xivpn2.xrayconfig.VmessUser;
import cn.gov.xivpn2.xrayconfig.WsSettings;

public class LegacyVmessShareLinkParser implements ShareLinkParser{


    @Override
    public Proxy parse(String uri) throws BadShareLinkException {
        String json = new String(Base64.getUrlDecoder().decode(uri.replaceFirst("vmess://", "")), StandardCharsets.UTF_8);

        Gson gson = new Gson();
        VmessShare vmessShare = gson.fromJson(json, VmessShare.class);

        if (!"2".equals(vmessShare.v)) {
            throw new BadShareLinkException("unsupported legacy vmess version: " + vmessShare.v);
        }

        if (vmessShare.add == null || vmessShare.port == null || vmessShare.id == null || vmessShare.network == null) {
            throw new BadShareLinkException("add, port, id and net must not be null");
        }

        Proxy proxy = new Proxy();
        proxy.label = Objects.requireNonNullElse(vmessShare.ps, "VMESS");
        proxy.protocol = "vmess";

        Outbound<VmessSettings> outbound = new Outbound<>();
        outbound.protocol = "vmess";

        // vmess settings
        outbound.settings = new VmessSettings();
        outbound.settings.vnext = new ArrayList<>(1);

        VmessServerSettings vmessServer = new VmessServerSettings();
        vmessServer.address = vmessShare.add;
        vmessServer.port = Integer.parseInt(vmessShare.port);
        vmessServer.users = new ArrayList<>(1);

        VmessUser vmessUser = new VmessUser();
        vmessUser.id = vmessShare.id;
        vmessUser.security = "auto";
        vmessUser.level = 0;
        vmessServer.users.add(vmessUser);

        outbound.settings.vnext.add(vmessServer);

        // stream settings

        outbound.streamSettings = new StreamSettings();

        if ("tcp".equals(vmessShare.network)) {
            outbound.streamSettings.network = "tcp";
        } else if ("kcp".equals(vmessShare.network)) {
            outbound.streamSettings.network = "kcp";
            outbound.streamSettings.kcpSettings = new KcpSettings();
            outbound.streamSettings.kcpSettings.mtu = 1350;
            outbound.streamSettings.kcpSettings.tti = 50;
            outbound.streamSettings.kcpSettings.uplinkCapacity = 5;
            outbound.streamSettings.kcpSettings.downlinkCapacity = 20;
            outbound.streamSettings.kcpSettings.congestion = true;
        } else if ("ws".equals(vmessShare.network)) {
            outbound.streamSettings.network = "ws";
            outbound.streamSettings.wsSettings = new WsSettings();
            outbound.streamSettings.wsSettings.host = Objects.requireNonNullElse(vmessShare.host, vmessShare.add);
            outbound.streamSettings.wsSettings.path = Objects.requireNonNullElse(vmessShare.path, "/");
        } else if ("grpc".equals(vmessShare.network)) {
            outbound.streamSettings.network = "grpc";
            outbound.streamSettings.grpcSettings = new GRPCSettings();
            outbound.streamSettings.grpcSettings.multiMode = "multi".equals(vmessShare.type);
            outbound.streamSettings.grpcSettings.serviceName = Objects.requireNonNullElse(vmessShare.path, "");
            outbound.streamSettings.grpcSettings.authority = Objects.requireNonNullElse(vmessShare.host, vmessShare.add);
        } else {
            throw new BadShareLinkException("unsupported network type: " + vmessShare.network);
        }

        if ("tls".equals(vmessShare.tls)) {
            outbound.streamSettings.security = "tls";
            outbound.streamSettings.tlsSettings = new TLSSettings();
            outbound.streamSettings.tlsSettings.alpn = new String[]{"h2", "http/1.1"};
            if (vmessShare.alpn != null && !vmessShare.alpn.isBlank()) {
                outbound.streamSettings.tlsSettings.alpn = vmessShare.alpn.split(",");
            }
            if (vmessShare.sni != null && !vmessShare.sni.isBlank()) {
                outbound.streamSettings.tlsSettings.serverName = vmessShare.sni;
            } else {
                outbound.streamSettings.tlsSettings.serverName = vmessShare.add;
            }
            if (vmessShare.fingerprint != null && !vmessShare.fingerprint.isBlank()) {
                outbound.streamSettings.tlsSettings.fingerprint = vmessShare.fingerprint;
            } else {
                outbound.streamSettings.tlsSettings.fingerprint = "chrome";
            }
        } else {
            outbound.streamSettings.security = "none";
        }

        proxy.config = gson.toJson(outbound);

        return proxy;
    }

    @Override
    public String marshal(Proxy proxy) {
        return "";
    }
}
