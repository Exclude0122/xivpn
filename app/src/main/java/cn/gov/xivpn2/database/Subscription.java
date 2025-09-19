package cn.gov.xivpn2.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.Expose;

@Entity(
        indices = {
                @Index(value = {"label"}, unique = true),
        }
)
public class Subscription {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @Expose
    public String label;
    @Expose
    public String url;
    @Expose
    public int autoUpdate;
}
