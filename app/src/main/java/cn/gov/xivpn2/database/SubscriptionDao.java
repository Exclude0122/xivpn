package cn.gov.xivpn2.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SubscriptionDao {
    @Query("SELECT * FROM subscription")
    List<Subscription> findAll();

    @Query("SELECT * FROM subscription WHERE label=:label")
    Subscription findByLabel(String label);

    @Insert
    void insert(Subscription subscription);

    @Query("DELETE FROM subscription WHERE label=:label")
    void delete(String label);

    @Query("UPDATE subscription SET url = :url WHERE label = :label")
    void updateUrl(String label, String url);

    @Query("DELETE FROM subscription")
    void deleteAll();
}
