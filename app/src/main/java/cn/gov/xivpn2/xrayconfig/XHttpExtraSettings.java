package cn.gov.xivpn2.xrayconfig;

import java.util.Map;

public class XHttpExtraSettings {
    public Map<String, String> headers;

    public String xPaddingBytes;
    public boolean xPaddingObfsMode;
    public String xPaddingKey;
    public String xPaddingHeader;
    public String xPaddingPlacement;
    public String xPaddingMethod;
    public String uplinkHTTPMethod;
    public String sessionPlacement;
    public String sessionKey;
    public String seqPlacement;
    public String seqKey;
    public String uplinkDataPlacement;
    public String uplinkDataKey;
    public String uplinkChunkSize;
    public Boolean noGRPCHeader;
    public Boolean noSSEHeader;
    public String scMaxEachPostBytes;
    public String scMinPostsIntervalMs;
    public Long scMaxBufferedPosts;
    public String scStreamUpServerSecs;
    public Integer serverMaxHeaderBytes;
    public XMuxSettings xmux;
    public XHttpStreamSettings downloadSettings;
}
