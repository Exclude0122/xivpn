package cn.gov.xivpn2.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.Expose;

@Entity(
        indices = {
                @Index(value = {"label", "subscription"}, unique = true),
        }
)
public class Proxy {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @Expose
    public String subscription;
    @Expose
    public String protocol;
    @Expose
    public String label;
    @Expose
    public String config;

    @Ignore
    public int ping = 0;
}
