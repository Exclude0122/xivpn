package cn.gov.xivpn2.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.RoomRawQuery;

import java.util.List;

@Dao
public abstract class ProxyDao {
    @Query("SELECT * FROM proxy ORDER BY id ASC")
    public abstract List<Proxy> findAll();

    @Query("SELECT * FROM proxy WHERE subscription = :subscription")
    public abstract List<Proxy> findBySubscription(String subscription);

    @Query("SELECT * FROM proxy WHERE subscription = :subscription AND label = :label LIMIT 1")
    public abstract Proxy find(String label, String subscription);

    @Insert()
    public abstract void add(Proxy proxy);

    @Query("INSERT OR IGNORE INTO proxy (label, protocol, subscription, config) VALUES ('No Proxy (Bypass Mode)', 'freedom', 'none', '{\"protocol\": \"freedom\"}')")
    public abstract void addFreedom();

    @Query("INSERT OR IGNORE INTO proxy (label, protocol, subscription, config) VALUES ('Block', 'blackhole', 'none', '{\"protocol\": \"blackhole\"}')")
    public abstract void addBlackhole();

    @Query("INSERT OR IGNORE INTO proxy (label, protocol, subscription, config) VALUES ('Built-in DNS Server', 'dns', 'none', '{\"protocol\": \"dns\", \"settings\": {\"nonIPQuery\": \"drop\"}}')")
    public abstract void addDNSOutbound();

    @Query("SELECT count(*) FROM proxy WHERE label = :label AND subscription = :subscription LIMIT 1")
    public abstract int exists(String label, String subscription);

    @Query("UPDATE proxy SET config = :config WHERE label = :label AND subscription = :subscription")
    public abstract void updateConfig(String label, String subscription, String config);

    @Query("DELETE FROM proxy WHERE label = :label AND subscription = :subscription")
    public abstract void delete(String label, String subscription);

    @Query("DELETE FROM proxy WHERE subscription = :subscription")
    public abstract void deleteBySubscription(String subscription);

    @Query("SELECT * FROM proxy WHERE id = :id")
    public abstract Proxy findById(long id);

    @Query("SELECT * FROM proxy WHERE protocol = :protocol")
    public abstract List<Proxy> findByProtocol(String protocol);

    @Query("DELETE FROM proxy")
    public abstract void deleteAll();
}
