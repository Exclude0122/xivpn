package cn.gov.xivpn2.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import cn.gov.xivpn2.NotificationID;
import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.ui.CrashLogActivity;
import cn.gov.xivpn2.ui.MainActivity;
import cn.gov.xivpn2.xrayconfig.Config;
import cn.gov.xivpn2.xrayconfig.Inbound;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.ProxyChain;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;
import cn.gov.xivpn2.xrayconfig.Routing;
import cn.gov.xivpn2.xrayconfig.RoutingRule;
import cn.gov.xivpn2.xrayconfig.Sniffing;
import cn.gov.xivpn2.xrayconfig.Sockopt;
import cn.gov.xivpn2.xrayconfig.StreamSettings;

public class XiVPNService extends VpnService implements SocketProtect {
    public static final int SOCKS_PORT = 18964;
    private final IBinder binder = new XiVPNBinder();
    private final String TAG = "XiVPNService";
    private final Set<VPNStatusListener> listeners = new HashSet<>();

    private volatile Status status = Status.DISCONNECTED;

    private Process libxivpnProcess = null;
    private Thread ipcThread = null;
    private OutputStream ipcWriter = null;
    private ParcelFileDescriptor fileDescriptor;
    private final CircularFifoQueue<String> stderrBuffer = new CircularFifoQueue<>(30);

    private synchronized void setStatus(Status newStatus) {
        Log.w(TAG, "status " + newStatus.name());
        new Handler(Looper.getMainLooper()).post(() -> {
            for (VPNStatusListener listener : listeners) {
                listener.onStatusChanged(newStatus);
            }
        });

        status = newStatus;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "on create");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(TAG, "on start command");

        // https://developer.android.com/develop/connectivity/vpn#user_experience_2
        // https://developer.android.com/develop/connectivity/vpn#detect_always-on
        // We set always-on to false when the service is started by the app,
        // so we assume service started without always-on is started by the system.

        boolean shouldStart = intent == null || intent.getBooleanExtra("always-on", true) || (intent.getAction() != null && intent.getAction().equals("cn.gov.xivpn2.START"));

        // start vpn
        if (shouldStart) {
            if (status != Status.DISCONNECTED) {
                Log.d(TAG, "on start command already started");
                return Service.START_NOT_STICKY;
            }

            Log.i(TAG, "start foreground");

            // start foreground service
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "XiVPNService");
            builder.setContentText("XiVPN is running");
            builder.setSmallIcon(R.drawable.baseline_vpn_key_24);
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
            builder.setOngoing(true);
            builder.setContentIntent(PendingIntent.getActivity(this, 20, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            startForeground(NotificationID.getID(), builder.build());

            // start
            try {
                Config config = buildXrayConfig();
                startVPN(config);
            } catch (Exception e) {
                Log.e(TAG, "start vpn", e);
                for (VPNStatusListener listener : listeners) {
                    listener.onMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        // stop vpn
        if (intent != null && intent.getAction() != null && intent.getAction().equals("cn.gov.xivpn2.STOP")) {
            if (status != Status.CONNECTED) {
                Log.d(TAG, "on start command already stopped");
                return Service.START_NOT_STICKY;
            }

            stopForeground(true);
            stopVPN();
        }

        return Service.START_NOT_STICKY;
    }

    public synchronized void startVPN(Config config) throws IOException {
        if (status != Status.DISCONNECTED) return;
        setStatus(Status.CONNECTING);

        // establish vpn
        Builder vpnBuilder = new Builder();
        vpnBuilder.addRoute("0.0.0.0", 0);
        vpnBuilder.addRoute("[::]", 0);
        vpnBuilder.addAddress("10.89.64.1", 32);
        vpnBuilder.addDnsServer("8.8.8.8");
        vpnBuilder.addDnsServer("8.8.4.4");

        Set<String> apps = getSharedPreferences("XIVPN", MODE_PRIVATE).getStringSet("APP_LIST", new HashSet<>());
        boolean blacklist = PreferenceManager.getDefaultSharedPreferences(this).getString("split_tunnel_mode", "Blacklist").equals("Blacklist");

        Log.i(TAG, "is blacklist: " + blacklist);
        for (String app : apps) {
            try {
                Log.i(TAG, "add app: " + app);
                if (blacklist) {
                    vpnBuilder.addDisallowedApplication(app);
                } else {
                    vpnBuilder.addAllowedApplication(app);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "package not found: " + app);
            }
        }

        fileDescriptor = vpnBuilder.establish();

        // start libxivpn
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String xrayConfig = gson.toJson(config);
        Log.i(TAG, "xray config: " + xrayConfig);

        // write xray config
        FileOutputStream configStream = new FileOutputStream(new File(getFilesDir(), "config.json"));
        configStream.write(xrayConfig.getBytes(StandardCharsets.UTF_8));
        configStream.close();

        String ipcPath = new File(getCacheDir(), "ipcsock").getAbsolutePath();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        ProcessBuilder builder = new ProcessBuilder()
                .redirectErrorStream(true)
                .directory(getFilesDir())
                .command(getApplicationInfo().nativeLibraryDir + "/libxivpn.so");
        Map<String, String> env = builder.environment();
        env.put("IPC_PATH", ipcPath);
        env.put("XRAY_LOCATION_ASSET", getFilesDir().getAbsolutePath());
        env.put("LOG_LEVEL", config.log.loglevel);
        env.put("XRAY_SNIFFING", Boolean.valueOf(preferences.getBoolean("sniffing", true)).toString());
        env.put("XRAY_SNIFFING_ROUTE_ONLY", Boolean.valueOf(preferences.getBoolean("sniffing_route_only", true)).toString());

        ipcThread = new Thread(() -> {
            // ipc socket listen
            LocalSocket socket = new LocalSocket(LocalSocket.SOCKET_STREAM);
            try {
                socket.bind(new LocalSocketAddress(ipcPath, LocalSocketAddress.Namespace.FILESYSTEM));
            } catch (IOException e) {
                Log.e(TAG, "bind ipc sock", e);
                setStatus(Status.DISCONNECTED);
                return;
            }
            Log.i(TAG, "ipc sock binded");

            LocalServerSocket serverSocket = null;
            OutputStream writer = null;
            try {
                serverSocket = new LocalServerSocket(socket.getFileDescriptor());

                // start xray
                libxivpnProcess = builder.start();

                // wait for ipc connection
                socket = serverSocket.accept();
                writer = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "listen ipc sock", e);
                setStatus(Status.DISCONNECTED);
                return;
            }

            // send tun fd
            FileDescriptor[] fds = {fileDescriptor.getFileDescriptor()};
            socket.setFileDescriptorsForSend(fds);
            try {
                writer.write("ping\n".getBytes(StandardCharsets.US_ASCII));
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "write to ipc sock", e);
                setStatus(Status.DISCONNECTED);
                return;
            }

            // logging
            String logFile = "";
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("logs", false)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault());
                String datetime = sdf.format(new Date());
                logFile = getFilesDir().getAbsolutePath() + "/logs/" + datetime + ".txt";
                new File(getFilesDir().getAbsolutePath(), "logs").mkdirs();
            }
            Log.i(TAG, "log file: " + logFile);

            PrintStream log = null;
            if (!logFile.isEmpty()) {
                try {
                    log = new PrintStream(logFile);
                } catch (Exception e) {
                    Log.e(TAG, "create libxivpn log", e);
                    setStatus(Status.DISCONNECTED);
                    return;
                }
            }

            synchronized (stderrBuffer) {
                stderrBuffer.clear();
            }

            // read stderr and stdout
            PrintStream log_ = log;
            new Thread(() -> {
                Scanner scanner = new Scanner(libxivpnProcess.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    Log.d("libxivpn", line);

                    if (log_ != null) {
                        log_.println(line);
                    }

                    synchronized (stderrBuffer) {
                        stderrBuffer.add(line);
                    }
                }
                if (log_ != null) {
                    log_.close();
                }
                scanner.close();
            }).start();

            setStatus(Status.CONNECTED);

            ipcLoop(socket);

            stopVPN();
        });
        ipcThread.start();
    }

    private void ipcLoop(LocalSocket socket) {
        try {
            InputStream reader = socket.getInputStream();
            OutputStream writer = socket.getOutputStream();
            ipcWriter = writer;

            Field fdField = FileDescriptor.class.getDeclaredField("descriptor");
            fdField.setAccessible(true);

            Scanner scanner = new Scanner(reader);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] splits = line.split(" ");

                Log.i(TAG, "ipc packet: " + Arrays.toString(splits));

                switch (splits[0]) {
                    case "ping":
                        break;
                    case "pong":
                        break;
                    case "protect":
                        FileDescriptor[] fds = socket.getAncillaryFileDescriptors();
                        if (fds == null) {
                            Log.e(TAG, "null array");
                            break;
                        }
                        if (fds.length != 1) {
                            Log.e(TAG, "expect 1 fd, found " + fds.length);
                            break;
                        }

                        int fd = fdField.getInt(fds[0]);
                        protectFd(fd);

                        Log.i(TAG, "ipc protect " + fd);

                        writer.write("protect_ack\n".getBytes(StandardCharsets.US_ASCII));
                        writer.flush();
                        break;
                }
            }

            scanner.close();

            Log.i(TAG, "protect loop exit");
        } catch (Exception e) {
            Log.e(TAG, "protect loop", e);
        } finally {
             ipcWriter = null;
        }
    }

    public synchronized void stopVPN() {
        if (status != Status.CONNECTED) return;
        setStatus(Status.DISCONNECTING);

        new Thread(() -> {
            try {
                ipcWriter.write("stop\n".getBytes(StandardCharsets.US_ASCII));
                ipcWriter.flush();
            } catch (Exception e) {
                Log.e(TAG, "ipc write stop", e);
            }

            try {
                libxivpnProcess.waitFor();
                ipcThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "wait libxivpn", e);
            }

            try {
                fileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "close tun fd", e);
            }

            int exitValue = libxivpnProcess.exitValue();
            if (exitValue != 0) {
                // process crashed
                // save last 30 lines of output and send a notification to user

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault());
                String datetime = sdf.format(new Date());
                File file = new File(getCacheDir(), "crash_" + datetime + ".txt");

                try {
                    FileOutputStream outputStream = new FileOutputStream(file);
                    outputStream.write("Libxivpn exited unexpectedly.\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.write(("Exit code " + exitValue + "\n\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.write("Last 30 lines of log before exit:\n\n".getBytes(StandardCharsets.UTF_8));
                    synchronized (stderrBuffer) {
                        for (String line : stderrBuffer) {
                            outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                            outputStream.write('\n');
                        }
                    }
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "write crash log", e);
                }

                Intent intent = new Intent(this, CrashLogActivity.class);
                intent.putExtra("FILE", "crash_" + datetime + ".txt");

                Notification notification = new Notification.Builder(this, "XiVPNService")
                        .setContentTitle(getString(R.string.vpn_process_crashed))
                        .setContentText(getString(R.string.click_to_open_crash_log))
                        .setSmallIcon(R.drawable.baseline_error_24)
                        .setContentIntent(PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                        .build();
                getSystemService(NotificationManager.class).notify(NotificationID.getID(), notification);


            }

            stopForeground(true);
            stopSelf();

            setStatus(Status.DISCONNECTED);
        }).start();
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "on revoke");

        stopVPN();
        stopForeground(true);
    }

    private Config buildXrayConfig() {
        Config config = new Config();
        config.inbounds = new ArrayList<>();
        config.outbounds = new ArrayList<>();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // logs
        config.log.loglevel = preferences.getString("log_level", "warning");

        try {

            // routing
            List<RoutingRule> rules = Rules.readRules(getFilesDir());

            config.routing = new Routing();
            config.routing.rules = rules;

            // outbound
            HashSet<Long> proxyIds = new HashSet<>();

            for (RoutingRule rule : rules) {
                long id = AppDatabase.getInstance().proxyDao().find(rule.outboundLabel, rule.outboundSubscription).id;
                Log.d(TAG, "build xray config: add proxy: " + id + " | " + rule.outboundLabel + " | " + rule.outboundSubscription);
                proxyIds.add(id);

                rule.outboundTag = String.format(Locale.ROOT, "#%d %s (%s)", id, rule.outboundLabel, rule.outboundSubscription);
                if (rule.domain.isEmpty()) rule.domain = null;
                if (rule.ip.isEmpty()) rule.ip = null;
                if (rule.port.isEmpty()) rule.port = null;
                if (rule.protocol.isEmpty()) rule.protocol = null;
                rule.outboundLabel = null;
                rule.outboundSubscription = null;
                rule.label = null;
            }

            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

            // catch all
            SharedPreferences sp = getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
            String selectedLabel = sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)");
            String selectedSubscription = sp.getString("SELECTED_SUBSCRIPTION", "none");
            Proxy catchAll = AppDatabase.getInstance().proxyDao().find(selectedLabel, selectedSubscription);


            ArrayList<Long> proxyIdsList = new ArrayList<>(proxyIds);
            proxyIdsList.remove(catchAll.id);
            proxyIdsList.add(0, catchAll.id);

            // outbounds
            for (Long id : proxyIdsList) {
                Proxy proxy = AppDatabase.getInstance().proxyDao().findById(id);
                if (proxy.protocol.equals("proxy-chain")) {
                    // proxy chain
                    Outbound<ProxyChainSettings> proxyChainOutbound = gson.fromJson(proxy.config, new TypeToken<Outbound<ProxyChainSettings>>() {

                    }.getType());

                    List<ProxyChain> proxyChains = proxyChainOutbound.settings.proxies;

                    for (int i = proxyChains.size() - 1; i >= 0; i--) {
                        ProxyChain each = proxyChains.get(i);

                        Proxy p = AppDatabase.getInstance().proxyDao().find(each.label, each.subscription);
                        if (p == null) {
                            throw new IllegalArgumentException(String.format(Locale.ROOT, getString(R.string.proxy_chain_not_found), proxy.label, each.label));
                        }
                        if (p.protocol.equals("proxy-chain")) {
                            throw new IllegalArgumentException(String.format(Locale.ROOT, getString(R.string.proxy_chain_nesting_error), proxy.label));
                        }

                        Outbound<?> outbound = gson.fromJson(p.config, Outbound.class);
                        if (i == proxyChains.size() - 1) {
                            outbound.tag = String.format(Locale.ROOT, "#%d %s (%s)", id, proxy.label, proxy.subscription);
                        } else {
                            outbound.tag = String.format(Locale.ROOT, "CHAIN #%d %s (%s)", id, each.label, each.subscription);
                        }

                        if (i > 0) {
                            if (outbound.streamSettings == null) {
                                outbound.streamSettings = new StreamSettings();
                                outbound.streamSettings.network = "tcp";
                            }
                            outbound.streamSettings.sockopt = new Sockopt();
                            outbound.streamSettings.sockopt.dialerProxy = String.format(Locale.ROOT, "CHAIN #%d %s (%s)", id, proxyChains.get(i - 1).label, proxyChains.get(i - 1).subscription);
                        }

                        config.outbounds.add(outbound);
                    }


                } else {
                    Outbound<?> outbound = gson.fromJson(proxy.config, Outbound.class);
                    outbound.tag = String.format(Locale.ROOT, "#%d %s (%s)", id, proxy.label, proxy.subscription);
                    config.outbounds.add(outbound);
                }

            }
        } catch (IOException e) {
            Log.wtf(TAG, "build xray config", e);
        }

        return config;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return binder;
    }

    @Override
    public void protectFd(int fd) {
        Log.d(TAG, "protect " + fd);
        protect(fd);
    }

    public enum Status {
        CONNECTED, CONNECTING, DISCONNECTED, DISCONNECTING,
    }

    public interface VPNStatusListener {
        void onStatusChanged(Status status);

        void onMessage(String msg);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "on destroy");
        super.onDestroy();
    }

    public class XiVPNBinder extends Binder {

        public Status getStatus() {
            return XiVPNService.this.status;
        }

        public void addListener(VPNStatusListener listener) {
            Log.d(TAG, "add listener " + listener.toString());
            XiVPNService.this.listeners.add(listener);
        }

        public void removeListener(VPNStatusListener listener) {
            Log.d(TAG, "remove listener " + listener.toString());
            XiVPNService.this.listeners.remove(listener);
        }

    }
}