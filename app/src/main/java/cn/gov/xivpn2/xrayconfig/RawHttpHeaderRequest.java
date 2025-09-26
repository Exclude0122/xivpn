package cn.gov.xivpn2.xrayconfig;


import java.util.HashMap;
import java.util.Map;

public class RawHttpHeaderRequest {
    public Map<String, String> headers = new HashMap<>(Map.of(
            "User-Agent", "Mozilla/5.0 (Android 10; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
    ));
}
