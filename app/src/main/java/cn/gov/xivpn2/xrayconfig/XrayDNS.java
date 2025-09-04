package cn.gov.xivpn2.xrayconfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XrayDNS {
    public Map<String, String> hosts = new HashMap<>();
    public List<DNSServer> servers = new ArrayList<>();
    public boolean disableFallbackIfMatch = true;
}
