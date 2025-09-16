package cn.gov.xivpn2.ui;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cn.gov.xivpn2.R;
import cn.gov.xivpn2.service.XiVPNService;
import cn.gov.xivpn2.xrayconfig.LabelSubscription;

public class MainActivityAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SWITCH = 0;
    private static final int VIEW_TYPE_SWITCH_CENTER = 1;
    private static final int VIEW_TYPE_TABS = 2;
    private static final int VIEW_TYPE_CARDS = 3;


    private final Listener listener;

    private XiVPNService.VPNState vpnState = XiVPNService.VPNState.DISCONNECTED;
    private String message;
    /**
     * proxy group -> (servers in proxy group, selected server)
     */
    private Map<LabelSubscription, Pair<List<LabelSubscription>, LabelSubscription>> groups = new HashMap<>();
    private LabelSubscription activeTab = null; // currently selected proxy group

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

            for (LabelSubscription key : groups.keySet()) {
                TabLayout.Tab tab = viewHolder.tabLayout.newTab();
                tab.setText(key.label);
                viewHolder.tabLayout.addTab(tab);
                tab.setTag(key);
            }


            viewHolder.tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    int count = getItemCount();
                    activeTab = ((LabelSubscription) tab.getTag());
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

            viewHolder.card.setCheckable(true);

            Pair<List<LabelSubscription>, LabelSubscription> selected = groups.get(activeTab);

            if (selected != null) {

                LabelSubscription proxy = selected.first.get(holder.getBindingAdapterPosition() - 2);

                viewHolder.card.setChecked(selected.second.equals(proxy));

                viewHolder.subscription.setText(proxy.subscription);
                viewHolder.label.setText(proxy.label);

                viewHolder.card.setOnClickListener(v -> {
                    listener.onServerSelected(this.activeTab, proxy);

                    int oldPosition = selected.first.indexOf(selected.second);
                    if (oldPosition >= 0) notifyItemChanged(oldPosition + 2); // unselect the old server
                    groups.put(activeTab, Pair.create(selected.first, proxy));
                    notifyItemChanged(holder.getBindingAdapterPosition());
                });

            }else {

                viewHolder.subscription.setText("");
                viewHolder.label.setText("");
                viewHolder.card.setOnClickListener(null);
                viewHolder.card.setChecked(false);
            }


        }
    }

    public void updateVpnState(XiVPNService.VPNState newState) {

        XiVPNService.VPNState oldState = this.vpnState;
        this.vpnState = newState;

        // off -> on
        if (oldState.equals(XiVPNService.VPNState.DISCONNECTED) && !newState.equals(XiVPNService.VPNState.DISCONNECTED)) {
            Pair<List<LabelSubscription>, LabelSubscription> selected = groups.get(activeTab);
            if (selected != null) {
                notifyItemRangeInserted(1, 1 + selected.first.size());
            } else {
                notifyItemRangeInserted(1, 1);
            }
        }

        // on -> off
        if (!oldState.equals(XiVPNService.VPNState.DISCONNECTED) && newState.equals(XiVPNService.VPNState.DISCONNECTED)) {

            int items = 0;

            if (!groups.isEmpty()) {
                Pair<List<LabelSubscription>, LabelSubscription> selected = groups.get(activeTab);
                if (selected == null) {
                    items += 1;
                } else {
                    items = 2 + selected.first.size();
                }
            }

            notifyItemRangeRemoved(1, items);
        }

        notifyItemChanged(0);
    }

    @Override
    public int getItemCount() {
        if (vpnState.equals(XiVPNService.VPNState.DISCONNECTED)) return 1;

        if (groups.isEmpty()) return 1; // hide tabs
        Pair<List<LabelSubscription>, LabelSubscription> selected = groups.get(activeTab); // servers under selected tab
        if (selected == null) return 1;
        return 2 + selected.first.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            if (XiVPNService.VPNState.DISCONNECTED != vpnState) {
                if (groups.isEmpty()) {
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

    public void setMessage(String message) {
        this.message = message;
        notifyItemChanged(0);
    }

    public void setGroups(Map<LabelSubscription, Pair<List<LabelSubscription>, LabelSubscription>> groups) {
        int servers = 0;
        if (groups.get(activeTab) != null) {
            servers = Objects.requireNonNull(groups.get(activeTab)).first.size();
        }

        this.groups = groups;
        Iterator<LabelSubscription> iterator = groups.keySet().iterator();
        if (iterator.hasNext()) {
            this.activeTab = iterator.next();
        } else {
            this.activeTab = null;
        }

        if (!vpnState.equals(XiVPNService.VPNState.DISCONNECTED)) {
            // update tabs
            notifyItemChanged(1);

            // update servers
            Pair<List<LabelSubscription>, LabelSubscription> selected = groups.get(this.activeTab); // servers under tab
            notifyItemRangeRemoved(2, servers); // remove all old server cards
            if (selected != null) notifyItemRangeInserted(2, selected.first.size()); // insert new cards
        }
    }

    public interface Listener {
        void onSwitchCheckedChange(CompoundButton button, boolean isChecked);
        void onServerSelected(LabelSubscription group, LabelSubscription selected);
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
        public final MaterialCardView card;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.label);
            subscription = itemView.findViewById(R.id.subscription);
            card = itemView.findViewById(R.id.card);
        }
    }
}
