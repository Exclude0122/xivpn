package cn.gov.xivpn2.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Proxy.class, Subscription.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    public static AppDatabase getInstance() {
        return instance;
    }

    public static void setInstance(AppDatabase instance) {
        AppDatabase.instance = instance;
    }

    public abstract ProxyDao proxyDao();

    public abstract SubscriptionDao subscriptionDao();
}