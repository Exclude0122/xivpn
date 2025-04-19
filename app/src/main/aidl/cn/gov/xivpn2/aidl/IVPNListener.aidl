// IVPNListener.aidl
package cn.gov.xivpn2.aidl;


interface IVPNListener {
    void onStatusChanged(String status);
    void onMessage(String msg);
}