package cn.gov.xivpn2.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.ProxyChain;

public class MainActivityAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SWITCH = 0;
    private static final int VIEW_TYPE_SWITCH_CENTER = 1;
    private static final int VIEW_TYPE_TABS = 2;
    private static final int VIEW_TYPE_CARDS = 3;


    private final Listener listener;

    private XiVPNService.VPNState vpnState = XiVPNService.VPNState.DISCONNECTED;
    private String message;
    private Map<ProxyChain, List<ProxyChain>> selectors = new HashMap<>();
    private ProxyChain activeTab = null;

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
        } else if (viewType == VIEW_TYPE_TABS) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_tabs, parent, false);
            return new TabsViewHolder(view);
        } else if (viewType == VIEW_TYPE_CARDS) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_card, parent, false);
            return new CardViewHolder(view);
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
        } else if (holder instanceof TabsViewHolder) {
            TabsViewHolder viewHolder = (TabsViewHolder) holder;

            viewHolder.tabLayout.setOnTabSelectedListener(null);

            viewHolder.tabLayout.removeAllTabs();

            for (ProxyChain key : selectors.keySet()) {
                TabLayout.Tab tab = viewHolder.tabLayout.newTab();
                tab.setText(key.label);
                viewHolder.tabLayout.addTab(tab);
                tab.setTag(key);
            }


            viewHolder.tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    int count = getItemCount();
                    activeTab = ((ProxyChain) tab.getTag());
                    notifyItemRangeRemoved(2, count - 2);
                    notifyItemRangeInserted(2, getItemCount() - 2);
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {

                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {

                }
            });

        } else if (holder instanceof CardViewHolder) {
            CardViewHolder viewHolder = (CardViewHolder) holder;

            List<ProxyChain> proxies = selectors.get(activeTab);
            if (proxies != null) {
                ProxyChain proxy = proxies.get(holder.getBindingAdapterPosition() - 2);
                viewHolder.subscription.setText(proxy.subscription);
                viewHolder.label.setText(proxy.label);
            } else {
                viewHolder.subscription.setText("");
                viewHolder.label.setText("");
            }
        }
    }

    public void updateVpnState(XiVPNService.VPNState newState) {

        XiVPNService.VPNState oldState = this.vpnState;
        this.vpnState = newState;

        // off -> on
        if (!oldState.equals(XiVPNService.VPNState.CONNECTED) && newState.equals(XiVPNService.VPNState.CONNECTED)) {
            List<ProxyChain> selected = selectors.get(activeTab);
            if (selected != null) {
                notifyItemRangeInserted(1, 1 + selected.size());
            } else {
                notifyItemRangeInserted(1, 1);
            }
        }

        // on -> off
        if (oldState.equals(XiVPNService.VPNState.CONNECTED) && !newState.equals(XiVPNService.VPNState.CONNECTED)) {

            int items = 0;

            if (!selectors.isEmpty()) {
                List<ProxyChain> selected = selectors.get(activeTab);
                if (selected == null) {
                    items += 1;
                } else {
                    items = 2 + selected.size();
                }
            }

            notifyItemRangeRemoved(1, items);
        }

        notifyItemChanged(0);
    }

    @Override
    public int getItemCount() {
        if (!vpnState.equals(XiVPNService.VPNState.CONNECTED)) return 1;

        if (selectors.isEmpty()) return 1; // hide tabs
        List<ProxyChain> selected = selectors.get(activeTab); // servers under selected tab
        if (selected == null) return 1;
        return 2 + selected.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            if (XiVPNService.VPNState.CONNECTED == vpnState) {
                if (selectors.isEmpty()) {
                    return VIEW_TYPE_SWITCH_CENTER;
                } else {
                    return VIEW_TYPE_SWITCH;
                }
            } else {
                return VIEW_TYPE_SWITCH_CENTER;
            }
        }

        if (position == 1) {
            return VIEW_TYPE_TABS;
        }

        return VIEW_TYPE_CARDS;
    }

    public interface Listener {
        void onSwitchCheckedChange(CompoundButton compoundButton, boolean b);
    }

    public void setMessage(String message) {
        this.message = message;
        notifyItemChanged(0);
    }

    public void setSelectors(Map<ProxyChain, List<ProxyChain>> selectors) {
        int servers = 0;
        if (selectors.get(activeTab) != null) {
            servers = Objects.requireNonNull(selectors.get(activeTab)).size();
        }

        this.selectors = selectors;
        Iterator<ProxyChain> iterator = selectors.keySet().iterator();
        if (iterator.hasNext()) {
            this.activeTab = iterator.next();
        } else {
            this.activeTab = null;
        }

        if (vpnState.equals(XiVPNService.VPNState.CONNECTED)) {
            // update tabs
            notifyItemChanged(1);

            // update servers
            List<ProxyChain> selected = selectors.get(this.activeTab); // servers under tab
            notifyItemRangeRemoved(2, servers); // remove all old server cards
            if (selected != null) notifyItemRangeInserted(2, selected.size()); // insert new cards
        }
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

    public static class TabsViewHolder extends RecyclerView.ViewHolder {
        public final TabLayout tabLayout;
        public TabsViewHolder(@NonNull View itemView) {
            super(itemView);
            tabLayout = itemView.findViewById(R.id.tab_layout);
        }
    }

    public static class CardViewHolder extends RecyclerView.ViewHolder {
        public final TextView label;
        public final TextView subscription;
        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.label);
            subscription = itemView.findViewById(R.id.subscription);
        }
    }
}
