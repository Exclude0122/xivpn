package cn.gov.xivpn2.xrayconfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;

public class StreamSettings {
    public String network;
    public String security;
    public WsSettings wsSettings;
    public HttpUpgradeSettings httpupgradeSettings;
    public TLSSettings tlsSettings;
    public RealitySettings realitySettings;
    public XHttpSettings xHttpSettings;
    public RawSettings rawSettings;
    public Sockopt sockopt;
    public KcpSettings kcpSettings;
    public GRPCSettings grpcSettings;
    public HysteriaTransportSettings hysteriaSettings;
    public JsonObject finalmask;
}
