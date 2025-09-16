package cn.gov.xivpn2.xrayconfig;

import java.util.Objects;

public class LabelSubscription {
    public String label;
    public String subscription;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LabelSubscription)) return false;
        LabelSubscription that = (LabelSubscription) o;
        return Objects.equals(label, that.label) && Objects.equals(subscription, that.subscription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, subscription);
    }
}
