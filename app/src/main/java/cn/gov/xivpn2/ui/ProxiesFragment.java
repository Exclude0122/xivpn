package cn.gov.xivpn2.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.IOException;
import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.service.SubscriptionWork;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.service.sharelink.MarshalProxyException;

public class ProxiesFragment extends Fragment {


    private ProxiesAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_proxies, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);

        this.adapter = new ProxiesAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // on list item clicked

        adapter.setOnClickListener(new ProxiesAdapter.Listener() {
            @Override
            public void onClick(View v, Proxy proxy, int i) {
                if (proxy.protocol.equals("dns")) return; // dns can not be the default outbound

                SharedPreferences sp = requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
                Rules.setCatchAll(sp, proxy.label, proxy.subscription);
                adapter.setChecked(proxy.label, proxy.subscription);

                XiVPNService.markConfigStale(requireContext());
            }

            @Override
            public void onLongClick(View v, Proxy proxy, int i) {

            }

            @Override
            public void onDelete(View v, Proxy proxy, int i) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.warning)
                        .setMessage(R.string.delete_confirm)
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                            AppDatabase.getInstance().proxyDao().delete(proxy.label, proxy.subscription);

                            try {
                                Rules.resetDeletedProxies(requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE), requireContext().getFilesDir());
                            } catch (IOException e) {
                                Log.e("ProxiesFragment", "reset deleted proxies", e);
                            }

                            XiVPNService.markConfigStale(requireContext());

                            refresh();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();

            }

            @Override
            public void onShare(View v, Proxy proxy, int i) {

                String link;
                try {
                    link = SubscriptionWork.marshalProxy(proxy);
                } catch (MarshalProxyException e) {
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }

                Bitmap bmp = null;
                try {

                    QRCodeWriter writer = new QRCodeWriter();
                    BitMatrix bitMatrix = writer.encode(link, BarcodeFormat.QR_CODE, 512, 512);
                    int width = bitMatrix.getWidth();
                    int height = bitMatrix.getHeight();
                    bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                        }
                    }

                } catch (WriterException e) {
                    Log.e("ProxiesFragment", "could not generate qr code", e);
                    return;
                }

                ImageView imageView = new ImageView(requireContext());
                imageView.setImageBitmap(bmp);

                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.share)
                        .setView(imageView)
                        .setPositiveButton(R.string.copy_share_link, (dialog, which) -> {

                            ClipboardManager clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("", link));

                        })
                        .show();

            }

            @Override
            public void onEdit(View v, Proxy proxy, int i) {
                Class<? extends AppCompatActivity> cls = null;
                switch (proxy.protocol) {
                    case "shadowsocks":
                        cls = ShadowsocksActivity.class;
                        break;
                    case "vmess":
                        cls = VmessActivity.class;
                        break;
                    case "vless":
                        cls = VlessActivity.class;
                        break;
                    case "trojan":
                        cls = TrojanActivity.class;
                        break;
                    case "wireguard":
                        cls = WireguardActivity.class;
                        break;
                    case "proxy-chain":
                        cls = ProxyChainActivity.class;
                        break;
                    case "proxy-group":
                        cls = ProxyGroupActivity.class;
                        break;
                    case "http":
                        cls = HttpActivity.class;
                        break;
                    case "socks":
                        cls = Socks5Activity.class;
                        break;
                    case "hysteria":
                        cls = HysteriaActivity.class;
                        break;
                }

                if (cls != null) {
                    Intent intent = new Intent(requireContext(), cls);
                    intent.putExtra("LABEL", proxy.label);
                    intent.putExtra("SUBSCRIPTION", proxy.subscription);
                    intent.putExtra("CONFIG", proxy.config);
                    startActivity(intent);
                }
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.proxies);
        }
        refresh();
    }

    private void refresh() {

        adapter.replaceProxies(AppDatabase.getInstance().proxyDao().findAll());

        SharedPreferences sp = requireContext().getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
        adapter.setChecked(
                sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)"),
                sp.getString("SELECTED_SUBSCRIPTION", "none")
        );


    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.proxies_activity, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.from_clipboard) {

            // import from clipboard

            View view = LayoutInflater.from(requireContext()).inflate(R.layout.edit_text, null);
            TextInputEditText editText2 = view.findViewById(R.id.edit_text);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.import_form_clipboard)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {

                        String s = Objects.requireNonNull(editText2.getText()).toString();
                        if (s.isEmpty()) {
                            return;
                        }

                        try {
                            SubscriptionWork.parseLine(s, "none");
                            Toast.makeText(requireContext(), R.string.proxy_added, Toast.LENGTH_SHORT).show();
                            XiVPNService.markConfigStale(requireContext());
                            refresh();
                        } catch (Exception e) {
                            Log.e("ProxiesFragment", "parse line", e);

                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.invalid_link)
                                    .setMessage(e.getMessage())
                                    .setPositiveButton(R.string.ok, null)
                                    .show();
                        }

                    }).show();

            view.requestFocus();

            return true;
        } else if (id == R.id.shadowsocks || id == R.id.vmess || id == R.id.socks5 || id == R.id.vless || id == R.id.trojan || id == R.id.wireguard || id == R.id.proxy_chain || id == R.id.proxy_group || id == R.id.http || id == R.id.hysteria) {

            // add

            View view = LayoutInflater.from(requireContext()).inflate(R.layout.label_edit_text, null);
            TextInputEditText editText = view.findViewById(R.id.edit_text);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.label)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {

                        String label = String.valueOf(editText.getText());
                        if (label.isEmpty() || AppDatabase.getInstance().proxyDao().exists(label, "none") > 0) {
                            Toast.makeText(requireContext(), getResources().getText(R.string.conflict_label), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Class<? extends AppCompatActivity> cls = null;
                        if (id == R.id.shadowsocks) {
                            cls = ShadowsocksActivity.class;
                        } else if (id == R.id.vmess) {
                            cls = VmessActivity.class;
                        } else if (id == R.id.vless) {
                            cls = VlessActivity.class;
                        } else if (id == R.id.trojan) {
                            cls = TrojanActivity.class;
                        } else if (id == R.id.wireguard) {
                            cls = WireguardActivity.class;
                        } else if (id == R.id.proxy_chain) {
                            cls = ProxyChainActivity.class;
                        } else if (id == R.id.proxy_group) {
                            cls = ProxyGroupActivity.class;
                        } else if (id == R.id.http) {
                            cls = HttpActivity.class;
                        } else if (id == R.id.socks5) {
                            cls = Socks5Activity.class;
                        } else if (id == R.id.hysteria) {
                            cls = HysteriaActivity.class;
                        }

                        Intent intent = new Intent(requireContext(), cls);
                        intent.putExtra("LABEL", label);
                        intent.putExtra("SUBSCRIPTION", "none");
                        startActivity(intent);

                    }).show();

            return true;
        } else if (id == R.id.help) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.help)
                    .setMessage(R.string.proxies_help)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;

        } else if (id == R.id.qrcode) {
            startActivity(new Intent(requireContext(), QRScanActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Nullable
    private androidx.appcompat.app.ActionBar getSupportActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            return ((AppCompatActivity) getActivity()).getSupportActionBar();
        }
        return null;
    }
}
