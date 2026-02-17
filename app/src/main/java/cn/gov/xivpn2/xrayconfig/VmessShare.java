package cn.gov.xivpn2.xrayconfig;

import com.google.gson.annotations.SerializedName;

public class VmessShare {
    public String v = "2";
    public String ps = "vmess";
    public String add = "";
    public String port = "";
    public String id = "";
    @SerializedName("aid")
    public String alterId = "0";
    @SerializedName("scy")
    public String security = "auto";
    @SerializedName("net")
    public String network = "tcp";
    public String type = "none";
    public String host = "";
    public String path = "";
    public String tls = "";
    public String sni = "";
    public String alpn = "h2,http/1,1";
    @SerializedName("fp")
    public String fingerprint = "";
}
