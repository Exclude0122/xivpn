package cn.gov.xivpn2.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ProxyDao {
    @Query("SELECT * FROM proxy ORDER BY id ASC")
    List<Proxy> findAll();

    @Query("SELECT * FROM proxy WHERE subscription = :subscription")
    List<Proxy> findBySubscription(String subscription);

    @Query("SELECT * FROM proxy WHERE subscription = :subscription AND label = :label LIMIT 1")
    Proxy find(String label, String subscription);

    @Insert()
    void add(Proxy proxy);

    @Query("INSERT OR IGNORE INTO proxy (label, protocol, subscription, config) VALUES ('No Proxy (Bypass Mode)', 'freedom', 'none', '{\"protocol\": \"freedom\"}')")
    void addFreedom();

    @Query("INSERT OR IGNORE INTO proxy (label, protocol, subscription, config) VALUES ('Block', 'blackhole', 'none', '{\"protocol\": \"blackhole\"}')")
    void addBlackhole();

    @Query("INSERT OR IGNORE INTO proxy (label, protocol, subscription, config) VALUES ('Built-in DNS Server', 'dns', 'none', '{\"protocol\": \"dns\", \"settings\": {\"nonIPQuery\": \"drop\"}}')")
    void addDNSOutbound();

    @Query("SELECT count(*) FROM proxy WHERE label = :label AND subscription = :subscription LIMIT 1")
    int exists(String label, String subscription);

    @Query("UPDATE proxy SET config = :config WHERE label = :label AND subscription = :subscription")
    void updateConfig(String label, String subscription, String config);

    @Query("DELETE FROM proxy WHERE label = :label AND subscription = :subscription")
    void delete(String label, String subscription);

    @Query("DELETE FROM proxy WHERE subscription = :subscription")
    void deleteBySubscription(String subscription);

    @Query("SELECT * FROM proxy WHERE id = :id")
    Proxy findById(long id);

    @Query("SELECT * FROM proxy WHERE protocol = :protocol")
    List<Proxy> findByProtocol(String protocol);
}
