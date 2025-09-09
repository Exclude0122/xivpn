package cn.gov.xivpn2.xrayconfig;

import java.util.Objects;

public class ProxyChain {
    public String label;
    public String subscription;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProxyChain)) return false;
        ProxyChain that = (ProxyChain) o;
        return Objects.equals(label, that.label) && Objects.equals(subscription, that.subscription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, subscription);
    }
}
