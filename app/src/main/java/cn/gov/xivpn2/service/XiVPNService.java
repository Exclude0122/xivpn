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
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.io.File;
import java.io.FileDescriptor;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import cn.gov.xivpn2.NotificationID;
import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.DNS;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.ui.CrashLogActivity;
import cn.gov.xivpn2.ui.MainActivity;
import cn.gov.xivpn2.xrayconfig.Config;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.LabelSubscription;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;
import cn.gov.xivpn2.xrayconfig.ProxyGroupSettings;
import cn.gov.xivpn2.xrayconfig.Routing;
import cn.gov.xivpn2.xrayconfig.RoutingRule;
import cn.gov.xivpn2.xrayconfig.Sockopt;
import cn.gov.xivpn2.xrayconfig.StreamSettings;

public class XiVPNService extends VpnService implements SocketProtect {
    private final IBinder binder = new XiVPNBinder();
    private final String TAG = "XiVPNService";
    private final Set<VPNStateListener> listeners = new HashSet<>();
    private final CircularFifoQueue<String> stderrBuffer = new CircularFifoQueue<>(30);
    private final Object vpnStateLock = new Object();
    private Process libxivpnProcess = null;
    private Thread teeThread = null;
    private Thread ipcThread = null;
    private OutputStream ipcWriter = null;
    private ParcelFileDescriptor fileDescriptor;
    private volatile VPNState vpnState = VPNState.DISCONNECTED;
    private Command commandBuffer = Command.NONE;
    /**
     * mustLibxiStop is true if libxivpn exited unexpectedly
     */
    private boolean mustLibxiStop = false;
    private boolean isXrayConfigStale = false;
    private Toast toast;

    public static void markConfigStale(Context context) {
        Intent intent = new Intent(context, XiVPNService.class);
        intent.setAction("cn.gov.xivpn2.RELOAD");
        context.startService(intent);
    }

    /**
     * Set new state.
     * This method must be called in a synchronized vpnStateLock block
     */
    private void setStateRaw(VPNState newState) {
        Log.w(TAG, "state: " + newState.name());

        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (listeners) {
                for (VPNStateListener listener : listeners) {
                    listener.onStateChanged(newState);
                }
            }
        });

        vpnState = newState;
    }

    private void setState(VPNState newState) {
        synchronized (vpnStateLock) {
            setStateRaw(newState);
        }
    }

    private void sendMessage(String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (listeners) {
                for (VPNStateListener listener : listeners) {
                    listener.onMessage(msg);
                }
            }
        });
    }

    private void updateCommand(Command command) {
        synchronized (vpnStateLock) {
            commandBuffer = command;
            vpnStateLock.notify();
        }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "on create");

        new Thread(() -> {

            // work to be executed outside the synchronized block
            Runnable work = () -> {
            };
            while (true) {
                work.run();
                work = () -> {
                };

                synchronized (vpnStateLock) {
                    while (commandBuffer == Command.NONE && !mustLibxiStop && !isXrayConfigStale) {
                        try {
                            // wait for new command
                            vpnStateLock.wait();
                        } catch (InterruptedException e) {
                            Log.wtf(TAG, "wait for new command", e);
                        }
                    }

                    // xray config will always become up to date
                    isXrayConfigStale = false;

                    if (commandBuffer == Command.CONNECT) {
                        commandBuffer = Command.NONE;

                        if (vpnState != VPNState.DISCONNECTED) continue;

                        setStateRaw(VPNState.ESTABLISHING_VPN);
                        work = () -> {
                            if (!startVPN()) {
                                setState(VPNState.DISCONNECTED);
                                return;
                            }

                            setState(VPNState.STARTING_LIBXI);
                            if (!startLibxi()) {
                                stopVPN();
                                setState(VPNState.DISCONNECTED);
                                return;
                            }

                            setState(VPNState.CONNECTED);
                        };
                    } else if (commandBuffer == Command.DISCONNECT || mustLibxiStop) {
                        commandBuffer = Command.NONE;
                        mustLibxiStop = false;

                        if (vpnState != VPNState.CONNECTED) continue;

                        setStateRaw(VPNState.STOPPING_LIBXI);
                        work = () -> {
                            stopLibxi();

                            setState(VPNState.STOPPING_VPN);
                            stopVPN();

                            setState(VPNState.DISCONNECTED);
                        };
                    } else {
                        // xray config is stale

                        if (vpnState != VPNState.CONNECTED) continue;

                        setStateRaw(VPNState.STOPPING_LIBXI);
                        work = () -> {
                            stopLibxi();

                            setState(VPNState.STARTING_LIBXI);
                            if (!startLibxi()) {
                                stopVPN();
                                setState(VPNState.DISCONNECTED);
                                return;
                            }

                            setState(VPNState.CONNECTED);

                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (toast != null) {
                                    toast.cancel();
                                }
                                toast = Toast.makeText(this, R.string.xray_config_reloaded, Toast.LENGTH_SHORT);
                                toast.show();
                            });

                        };
                    }
                }
            }
        }).start();

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.i(TAG, "on start command");

        if (intent == null) {
            return Service.START_NOT_STICKY;
        }

        if (intent.getAction() != null && intent.getAction().equals("cn.gov.xivpn2.RELOAD")) {
            synchronized (vpnStateLock) {
                isXrayConfigStale = true;
                vpnStateLock.notify();
            }
            return Service.START_NOT_STICKY;
        }

        // https://developer.android.com/develop/connectivity/vpn#user_experience_2
        // https://developer.android.com/develop/connectivity/vpn#detect_always-on
        // We set always-on to false when the service is started by the app,
        // so we assume service started without always-on is started by the system.

        boolean shouldStart = intent.getBooleanExtra("always-on", true) || intent.getAction() != null && intent.getAction().equals("cn.gov.xivpn2.START");

        // start vpn
        if (shouldStart) {
            updateCommand(Command.CONNECT);
        }
        // stop vpn
        else if (intent.getAction() != null && intent.getAction().equals("cn.gov.xivpn2.STOP")) {
            updateCommand(Command.DISCONNECT);
        }

        return Service.START_NOT_STICKY;
    }

    /**
     * Start vpn interface
     */
    private boolean startVPN() {
        Log.i(TAG, "start foreground");

        // start foreground service
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "XiVPNService");
        builder.setContentText("XiVPN is running");
        builder.setSmallIcon(R.drawable.baseline_vpn_key_24);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setOngoing(true);
        builder.setContentIntent(PendingIntent.getActivity(this, 20, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        startForeground(NotificationID.getID(), builder.build());

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
        return fileDescriptor != null;
    }

    private boolean startLibxi() {
        // start libxivpn
        Config config = null;

        try {
            config = buildXrayConfig();
        } catch (RuntimeException e) {
            Log.e(TAG, "build xray config");
            sendMessage("Error: Could not build xray config: " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }


        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String xrayConfig = gson.toJson(config);
        Log.i(TAG, "xray config: " + xrayConfig);

        try {
            // write xray config
            FileOutputStream configStream = new FileOutputStream(new File(getFilesDir(), "config.json"));
            configStream.write(xrayConfig.getBytes(StandardCharsets.UTF_8));
            configStream.close();
        } catch (IOException e) {
            Log.e(TAG, "write xray config", e);
            sendMessage("Error: Write xray config to file: " + e.getMessage());
            return false;
        }

        String ipcPath = new File(getCacheDir(), "ipcsock").getAbsolutePath();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        ProcessBuilder builder = new ProcessBuilder().redirectErrorStream(true).directory(getFilesDir()).command(getApplicationInfo().nativeLibraryDir + "/libxivpn.so");
        Map<String, String> env = builder.environment();
        env.put("IPC_PATH", ipcPath);
        env.put("XRAY_LOCATION_ASSET", getFilesDir().getAbsolutePath());
        env.put("LOG_LEVEL", config.log.loglevel);
        env.put("XRAY_SNIFFING", Boolean.valueOf(preferences.getBoolean("sniffing", true)).toString());
        env.put("XRAY_SNIFFING_ROUTE_ONLY", Boolean.valueOf(preferences.getBoolean("sniffing_route_only", true)).toString());

        // ipc socket listen
        LocalSocket socket = new LocalSocket(LocalSocket.SOCKET_STREAM);
        try {
            socket.bind(new LocalSocketAddress(ipcPath, LocalSocketAddress.Namespace.FILESYSTEM));
        } catch (IOException e) {
            Log.e(TAG, "bind ipc sock", e);
            sendMessage("error: bind ipc socket to file: " + e.getMessage());
            return false;
        }
        Log.i(TAG, "ipc sock bound");

        LocalServerSocket serverSocket = null;
        try {
            serverSocket = new LocalServerSocket(socket.getFileDescriptor());

            // start xray
            libxivpnProcess = builder.start();

            // wait for ipc connection
            socket = serverSocket.accept();
            ipcWriter = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "listen ipc sock", e);
            sendMessage("error: listen on ipc socket: " + e.getMessage());
            return false;
        }

        // send tun fd
        FileDescriptor[] fds = {fileDescriptor.getFileDescriptor()};
        socket.setFileDescriptorsForSend(fds);
        try {
            ipcWriter.write("ping\n".getBytes(StandardCharsets.US_ASCII));
            ipcWriter.flush();
        } catch (IOException e) {
            Log.e(TAG, "write tn ipc sock", e);
            sendMessage("error: write to ipc socket: " + e.getMessage());
            return false;
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
                sendMessage("error: create libxivpn log: " + e.getMessage());
                return false;
            }
        }

        stderrBuffer.clear();

        // read stderr and stdout
        PrintStream log_ = log;
        teeThread = new Thread(() -> {
            Scanner scanner = new Scanner(libxivpnProcess.getInputStream());
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Log.d("libxivpn", line);

                if (log_ != null) {
                    log_.println(line);
                }

                stderrBuffer.add(line);
            }
            if (log_ != null) {
                log_.close();
            }
            scanner.close();
        });
        teeThread.start();

        LocalSocket finalSocket = socket;
        ipcThread = new Thread(() -> {
            ipcLoop(finalSocket);

            synchronized (vpnStateLock) {

                if (vpnState != VPNState.STOPPING_LIBXI) {
                    sendMessage("error: libxivpn exit unexpectedly");

                    mustLibxiStop = true;
                    vpnStateLock.notify();
                }
            }
        });
        ipcThread.start();

        return true;
    }

    /**
     * Handles IPC commands from libxivpn. Called in ipcTread.
     */
    private void ipcLoop(LocalSocket socket) {
        try {
            InputStream reader = socket.getInputStream();

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

                        try {
                            Os.close(fds[0]);
                        } catch (ErrnoException e) {
                            Log.e(TAG, "protect os.close", e);
                        }

                        Log.i(TAG, "ipc protect " + fd);

                        ipcWriter.write("protect_ack\n".getBytes(StandardCharsets.US_ASCII));
                        ipcWriter.flush();
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

    /**
     * Stop libxivpn process.
     */
    private void stopLibxi() {
        try {
            ipcWriter.write("stop\n".getBytes(StandardCharsets.US_ASCII));
            ipcWriter.flush();
        } catch (Exception e) {
            Log.e(TAG, "ipc write stop", e);
        }

        try {
            if (!libxivpnProcess.waitFor(5, TimeUnit.SECONDS)) {
                sendMessage("error: timeout when waiting for libxivpn exit");

                libxivpnProcess.destroyForcibly();
                libxivpnProcess.waitFor();
            }
            ipcThread.join();
            teeThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "wait for libxivpn", e);
        }

        int exitValue = libxivpnProcess.exitValue();
        if (exitValue != 0) {
            // process crashed
            // save last 30 lines of output and send a notification to user

            Log.e(TAG, "libxivpn process crashed with value " + exitValue);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.getDefault());
            String datetime = sdf.format(new Date());
            File file = new File(getCacheDir(), "crash_" + datetime + ".txt");

            try {
                FileOutputStream outputStream = new FileOutputStream(file);
                outputStream.write("Libxivpn exited unexpectedly.\n".getBytes(StandardCharsets.UTF_8));
                outputStream.write(("Exit code " + exitValue + "\n\n").getBytes(StandardCharsets.UTF_8));
                outputStream.write("Last 30 lines of log before exit:\n\n".getBytes(StandardCharsets.UTF_8));
                for (String line : stderrBuffer) {
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                    outputStream.write('\n');
                }
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "write crash log", e);
            }

            Intent intent = new Intent(this, CrashLogActivity.class);
            intent.putExtra("FILE", "crash_" + datetime + ".txt");

            Notification notification = new Notification.Builder(this, "XiVPNService").setContentTitle(getString(R.string.vpn_process_crashed)).setContentText(getString(R.string.click_to_open_crash_log)).setSmallIcon(R.drawable.baseline_error_24).setContentIntent(PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build();
            getSystemService(NotificationManager.class).notify(NotificationID.getID(), notification);
        }
    }

    private void stopVPN() {
        try {
            fileDescriptor.close();
        } catch (IOException e) {
            Log.e(TAG, "close tun fd", e);
        }
        fileDescriptor = null;

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "on revoke");

        synchronized (vpnStateLock) {
            mustLibxiStop = true;
            vpnStateLock.notify();
        }
    }

    // Returns the selected proxy.
    private Proxy resolveProxyGroup(String label, String subscription, List<Pair<String, String>> visited) {

        Log.d(TAG, "resolve proxy group " + label + " " + subscription + " " + visited.toString());

        // check for cycles
        if (visited.contains(new Pair<>(label, subscription))) {
            StringBuilder sb = new StringBuilder(getString(R.string.proxy_group_cycle));
            sb.append(visited.get(0).first).append(" (").append(visited.get(0).second).append(")");
            for (int i = 1; i < visited.size(); i++) {
                sb.append(" -> ").append(visited.get(i).first).append(" (").append(visited.get(i).second).append(")");
            }
            throw new IllegalStateException(sb.toString());
        }

        Proxy proxy = AppDatabase.getInstance().proxyDao().find(label, subscription);

        if (proxy == null) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, getString(R.string.proxy_group_not_found), visited.get(visited.size() - 1).first, label));
        }

        if (!proxy.protocol.equals("proxy-group")) {
            return proxy;
        }

        Gson gson = new Gson();
        Outbound<ProxyGroupSettings> proxyGroupSettings = gson.fromJson(proxy.config, new TypeToken<Outbound<ProxyGroupSettings>>() {
        }.getType());

        if (proxyGroupSettings.settings.proxies.isEmpty()) {
            throw new IllegalStateException(getString(R.string.proxy_group_empty) + label + " (" + subscription + ")");
        }

        LabelSubscription selected = proxyGroupSettings.settings.selected;
        LabelSubscription found = proxyGroupSettings.settings.proxies.get(0); // default to the first one

        for (LabelSubscription proxyChain : proxyGroupSettings.settings.proxies) {
            if (proxyChain.subscription == null || proxyChain.label == null || selected == null || selected.label == null || selected.subscription == null) {
                continue;
            }
            if (proxyChain.label.equals(selected.label) && proxyChain.subscription.equals(selected.subscription)) {
                found = new LabelSubscription();
                found.label = proxyChain.label;
                found.subscription = proxyChain.subscription;
                break;
            }
        }

        List<Pair<String, String>> newVisited = new ArrayList<>(visited);
        newVisited.add(new Pair<>(label, subscription));

        return resolveProxyGroup(found.label, found.subscription, newVisited);
    }

    private Config buildXrayConfig() {
        Config config = new Config();
        config.inbounds = new ArrayList<>();
        config.outbounds = new ArrayList<>();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // logs
        config.log.loglevel = preferences.getString("log_level", "warning");

        try {

            // dns
            config.dns = DNS.readDNSSettings(getFilesDir());

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

                String tag = String.format(Locale.ROOT, "#%d %s (%s)", id, proxy.label, proxy.subscription);

                if (proxy.protocol.equals("proxy-group")) {
                    proxy = resolveProxyGroup(proxy.label, proxy.subscription, new ArrayList<>());
                    Log.i(TAG, "resolved proxy group " + tag + " => " + proxy.label + " " + proxy.subscription);
                }


                if (proxy.protocol.equals("proxy-chain")) {
                    // proxy chain
                    Outbound<ProxyChainSettings> proxyChainOutbound = gson.fromJson(proxy.config, new TypeToken<Outbound<ProxyChainSettings>>() {

                    }.getType());

                    List<LabelSubscription> proxyChains = proxyChainOutbound.settings.proxies;

                    for (int i = proxyChains.size() - 1; i >= 0; i--) {
                        LabelSubscription each = proxyChains.get(i);

                        Proxy p = AppDatabase.getInstance().proxyDao().find(each.label, each.subscription);
                        if (p == null) {
                            throw new IllegalArgumentException(String.format(Locale.ROOT, getString(R.string.proxy_chain_not_found), proxy.label, each.label));
                        }

                        if (p.protocol.equals("proxy-group")) {
                            p = resolveProxyGroup(p.label, p.subscription, new ArrayList<>());
                            Log.i(TAG, "resolved proxy group " + tag + " => " + p.label + " " + p.subscription);
                        }

                        if (p.protocol.equals("proxy-chain")) {
                            throw new IllegalArgumentException(String.format(Locale.ROOT, getString(R.string.proxy_chain_nesting_error), proxy.label));
                        }

                        Outbound<?> outbound = gson.fromJson(p.config, Outbound.class);
                        if (i == proxyChains.size() - 1) {
                            outbound.tag = tag;
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
                    outbound.tag = tag;
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

    @Override
    public void onDestroy() {
        Log.i(TAG, "on destroy");
        super.onDestroy();
    }

    public enum VPNState {
        DISCONNECTED, ESTABLISHING_VPN, STARTING_LIBXI, CONNECTED, STOPPING_LIBXI, STOPPING_VPN
    }

    public enum Command {
        NONE, CONNECT, DISCONNECT,
    }

    public interface VPNStateListener {
        void onStateChanged(VPNState status);

        void onMessage(String msg);
    }

    public class XiVPNBinder extends Binder {

        public VPNState getState() {
            return XiVPNService.this.vpnState;
        }

        public void addListener(VPNStateListener listener) {
            synchronized (listeners) {
                Log.d(TAG, "add listener " + listener.toString());
                XiVPNService.this.listeners.add(listener);
            }
        }

        public void removeListener(VPNStateListener listener) {
            synchronized (listeners) {
                Log.d(TAG, "remove listener " + listener.toString());
                XiVPNService.this.listeners.remove(listener);
            }
        }

    }
}