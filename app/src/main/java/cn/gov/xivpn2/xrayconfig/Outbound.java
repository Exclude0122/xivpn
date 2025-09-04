package cn.gov.xivpn2.xrayconfig;

public class Outbound<T> {
    public String tag;
    public String protocol;
    public T settings;
    public StreamSettings streamSettings;
    public MuxSettings mux;
}
