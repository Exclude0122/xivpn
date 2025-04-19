// IVPNService.aidl
package cn.gov.xivpn2.aidl;

import cn.gov.xivpn2.aidl.IVPNListener;

interface IVPNService {
    String getStatus();
    void addListener(IVPNListener listener);
    void removeListener(IVPNListener listener);
}