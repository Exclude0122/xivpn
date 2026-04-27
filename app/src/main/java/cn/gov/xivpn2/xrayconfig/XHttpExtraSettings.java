package cn.gov.xivpn2.xrayconfig;

import java.util.Map;

public class XHttpExtraSettings {
    public Map<String, String> headers;
    public String xPaddingBytes;
    public Boolean noGRPCHeader;
    public Integer scMaxEachPostBytes;
    public Integer scMinPostsIntervalMs;
    public XMuxSettings xmux;
    public XHttpStreamSettings downloadSettings;
}
