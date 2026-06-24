package cn.gov.xivpn2.xrayconfig;

import com.google.gson.JsonObject;

import java.util.List;

public class Config {
    public List<Inbound> inbounds;
    public List<Outbound> outbounds;
    public Log log = new Log();
    public Routing routing;
    public XrayDNS dns;
    public Policy policy;
    public JsonObject stats;
}
