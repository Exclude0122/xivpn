package cn.gov.xivpn2.database;

import android.content.SharedPreferences;
import android.util.Log;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.gov.xivpn2.xrayconfig.RoutingRule;

public class Rules {

    public static List<RoutingRule> readRules(File filesDir) throws IOException {
        Gson gson = new Gson();
        File file = new File(filesDir, "rules.json");

        byte[] bytes = FileUtils.readFileToByteArray(file);
        Type type = new TypeToken<List<RoutingRule>>() {
        }.getType();

        return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    }

    public static void writeRules(File filesDir, List<RoutingRule> rules) throws IOException {
        File file = new File(filesDir, "rules.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(rules);
        FileUtils.writeStringToFile(file, json, StandardCharsets.UTF_8);
    }

    /**
     * Reset outbound to freedom for delete outbound proxy, and set catch all to freedom if the
     * currently selected catch all does not exist.
     */
    public static void resetDeletedProxies(SharedPreferences sp, File filesDir) throws IOException {
        List<RoutingRule> rules = readRules(filesDir);
        for (RoutingRule rule : rules) {
            Proxy proxy = AppDatabase.getInstance().proxyDao().find(rule.outboundLabel, rule.outboundSubscription);
            if (proxy == null) {
                Log.d("Rules", "deleted outbound: " + rule.outboundLabel + " " + rule.outboundSubscription);
                rule.outboundLabel = "No Proxy (Bypass Mode)";
                rule.outboundSubscription = "none";
            }
        }
        writeRules(filesDir, rules);

        String selectedLabel = sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)");
        String selectedSubscription = sp.getString("SELECTED_SUBSCRIPTION", "none");
        if (AppDatabase.getInstance().proxyDao().exists(selectedLabel, selectedSubscription) == 0) {
            setCatchAll(sp, "No Proxy (Bypass Mode)", "none");
        }

    }

    public static void setCatchAll(SharedPreferences sp, String label, String subscription) {
        SharedPreferences.Editor edit = sp.edit();
        edit.putString("SELECTED_LABEL", label);
        edit.putString("SELECTED_SUBSCRIPTION", subscription);
        edit.commit();
    }


}
