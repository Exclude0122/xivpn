package cn.gov.xivpn2.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.service.XiVPNService;

public class MainActivityAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SWITCH = 0;
    private static final int VIEW_TYPE_SWITCH_CENTER = 1;
    private final Listener listener;

    private XiVPNService.VPNState vpnState = XiVPNService.VPNState.DISCONNECTED;
    private String message;

    public MainActivityAdapter(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SWITCH) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_switch, parent, false);
            return new SwitchViewHolder(view);
        } else if (viewType == VIEW_TYPE_SWITCH_CENTER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_switch_center, parent, false);
            return new SwitchViewHolder(view);
        }
        throw new IllegalArgumentException("view type " + viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SwitchViewHolder) {
            SwitchViewHolder viewHolder = (SwitchViewHolder) holder;

            viewHolder.textView.setText(Objects.requireNonNullElse(message, ""));

            viewHolder.aSwitch.setOnCheckedChangeListener(null);
            if (vpnState == XiVPNService.VPNState.CONNECTED || vpnState == XiVPNService.VPNState.DISCONNECTED) {
                viewHolder.aSwitch.setChecked(vpnState == XiVPNService.VPNState.CONNECTED);
                viewHolder.aSwitch.setEnabled(true);
            } else {
                viewHolder.aSwitch.setChecked(vpnState == XiVPNService.VPNState.ESTABLISHING_VPN || vpnState == XiVPNService.VPNState.STARTING_LIBXI);
                viewHolder.aSwitch.setEnabled(false);
            }
            viewHolder.aSwitch.setOnCheckedChangeListener(listener::onSwitchCheckedChange);
        }
    }

    public void updateVpnState(XiVPNService.VPNState newState) {
        this.vpnState = newState;
        notifyItemChanged(0);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            if (XiVPNService.VPNState.CONNECTED == vpnState) {
                return VIEW_TYPE_SWITCH;
            } else {
                return VIEW_TYPE_SWITCH_CENTER;
            }
        }

        throw new IllegalArgumentException("bad position " + position);
    }

    public interface Listener {

        void onSwitchCheckedChange(CompoundButton compoundButton, boolean b);
    }

    public void setMessage(String message) {
        this.message = message;
        notifyItemChanged(0);
    }

    public static class SwitchViewHolder extends RecyclerView.ViewHolder {
        public final MaterialSwitch aSwitch;
        public final TextView textView;
        public SwitchViewHolder(@NonNull View itemView) {
            super(itemView);
            aSwitch = itemView.findViewById(R.id.vpn_switch);
            textView = itemView.findViewById(R.id.textview);
        }
    }
}
