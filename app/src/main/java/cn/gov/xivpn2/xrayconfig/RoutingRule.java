package cn.gov.xivpn2.xrayconfig;

import java.util.List;

public class RoutingRule {
    public String type = "field";
    public List<String> domain;
    public List<String> ip;
    public String port;
    public String network;
    public List<String> protocol;
    public String outboundTag;

    public String outboundSubscription;
    public String outboundLabel;
    public String label;
}
