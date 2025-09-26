package cn.gov.xivpn2.xrayconfig;

public class StreamSettings {
    public String network;
    public String security;
    public WsSettings wsSettings;
    public HttpUpgradeSettings httpupgradeSettings;
    public QuicSettings quicSettings;
    public TLSSettings tlsSettings;
    public RealitySettings realitySettings;
    public XHttpSettings xHttpSettings;
    public RawSettings rawSettings;
    public Sockopt sockopt;

    // used by xhttp
    public String address;
    public int port;
}
