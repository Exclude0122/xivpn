package cn.gov.xivpn2.xrayconfig;

public class HysteriaTransportSettings {
    public String auth;

    // ignored by xray, moved to finalmask. only read to migrate old configs
    public String up;
    public String down;
    public HysteriaHop udphop;
}
