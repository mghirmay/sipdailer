package com.example.android.sip;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView emptyText;
    private final MagnusApiClient apiClient = new MagnusApiClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        recyclerView = view.findViewById(R.id.history_recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.history_swipe_refresh);
        progressBar = view.findViewById(R.id.history_progress);
        emptyText = view.findViewById(R.id.history_empty_text);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::fetchHistory);

        fetchHistory();

        return view;
    }

    private void fetchHistory() {
        if (!isAdded()) return;
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String domain = prefs.getString("domainPref", "sinitpower.de");
        String apiPath = prefs.getString("apiPathPref", "/webAPI/magnusbillingApi.php");
        apiClient.setBaseUrl("https://" + domain + apiPath);

        String username = prefs.getString("loginUsernamePref", "");
        if (username.isEmpty()) {
            username = prefs.getString("namePref", "");
        }
        
        if (username.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        apiClient.getCallHistory(username, new MagnusApiClient.ApiCallback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray result) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                
                List<JSONObject> historyItems = new ArrayList<>();
                for (int i = 0; i < result.length(); i++) {
                    historyItems.add(result.optJSONObject(i));
                }
                
                adapter.setItems(historyItems);
                emptyText.setVisibility(historyItems.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                emptyText.setVisibility(View.VISIBLE);
                emptyText.setText("Failed to load history: " + error);
            }
        });
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<JSONObject> items = new ArrayList<>();

        public void setItems(List<JSONObject> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject item = items.get(position);
            
            // Fixed field names based on MagnusBilling CDR table structure
            holder.tvDestination.setText(item.optString("calledstation"));
            holder.tvTime.setText(item.optString("starttime"));
            
            int durationSeconds = item.optInt("sessiontime", 0);
            int minutes = durationSeconds / 60;
            int seconds = durationSeconds % 60;
            holder.tvDuration.setText(String.format("%02d:%02d", minutes, seconds));
            
            String cost = item.optString("sessionbill", "0.00");
            holder.tvCost.setText("€ " + cost);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDestination, tvTime, tvDuration, tvCost;

            ViewHolder(View view) {
                super(view);
                tvDestination = view.findViewById(R.id.history_destination);
                tvTime = view.findViewById(R.id.history_time);
                tvDuration = view.findViewById(R.id.history_duration);
                tvCost = view.findViewById(R.id.history_cost);
            }
        }
    }
}
