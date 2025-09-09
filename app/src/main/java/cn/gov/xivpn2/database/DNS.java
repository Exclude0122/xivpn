package cn.gov.xivpn2.database;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import cn.gov.xivpn2.xrayconfig.XrayDNS;

public class DNS {

    public static XrayDNS readDNSSettings(File filesDir) throws IOException {
        Gson gson = new Gson();
        File file = new File(filesDir, "dns.json");

        byte[] bytes = FileUtils.readFileToByteArray(file);
        Type type = new TypeToken<XrayDNS>() {
        }.getType();

        return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    }

    public static void writeDNSSettings(File filesDir, XrayDNS rules) throws IOException {
        File file = new File(filesDir, "dns.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(rules);
        FileUtils.writeStringToFile(file, json, StandardCharsets.UTF_8);
    }


}
