package cn.gov.xivpn2.xrayconfig;

import java.util.ArrayList;
import java.util.List;

public class WireguardSettings {
    public String secretKey;
    public List<String> address = new ArrayList<>();
    public List<WireguardPeer> peers = new ArrayList<>();
    public boolean kernelMode = false;
    public int[] reserved = new int[]{0, 0, 0};
    public int workers = 2;
}
