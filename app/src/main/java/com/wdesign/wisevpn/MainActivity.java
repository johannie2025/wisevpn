package com.wdesign.wisevpn;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView    recycler;
    private LinearLayout    layoutLoading;
    private TextView        tvStatus, tvStatusDetail, tvLoadingMsg;
    private Button          btnConnect;
    private View            statusCard;

    private ServerAdapter     adapter;
    private List<ServerModel> servers = new ArrayList<>();
    private ServerModel       selectedServer;
    private ServerModel       connectedServer;
    private String            currentState = WiseVpnService.STATE_DISCONNECTED;

    private final BroadcastReceiver vpnReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String state = intent.getStringExtra(WiseVpnService.EXTRA_STATE);
            String error = intent.getStringExtra(WiseVpnService.EXTRA_ERROR_MSG);
            if (state != null) updateUiState(state, error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupRecycler();
        setupButtons();
        loadServers();
    }

    private void bindViews() {
        recycler       = findViewById(R.id.recycler_servers);
        layoutLoading  = findViewById(R.id.layout_loading);
        tvStatus       = findViewById(R.id.tv_status);
        tvStatusDetail = findViewById(R.id.tv_status_detail);
        tvLoadingMsg   = findViewById(R.id.tv_loading_msg);
        btnConnect     = findViewById(R.id.btn_connect);
        statusCard     = findViewById(R.id.status_card);
    }

    private void setupRecycler() {
        adapter = new ServerAdapter(servers, server -> {
            selectedServer = server;
            updateConnectButton();
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
    }

    private void setupButtons() {
        btnConnect.setOnClickListener(v -> {
            if (currentState.equals(WiseVpnService.STATE_CONNECTED) ||
                currentState.equals(WiseVpnService.STATE_CONNECTING)) {
                disconnectVpn();
            } else {
                if (selectedServer == null && !servers.isEmpty()) {
                    selectedServer = servers.get(0);
                    adapter.setSelected(0);
                }
                if (selectedServer == null) {
                    Toast.makeText(this, "Sélectionnez un serveur", Toast.LENGTH_SHORT).show();
                    return;
                }
                connectVpn(selectedServer);
            }
        });
        findViewById(R.id.btn_refresh).setOnClickListener(v -> loadServers());
    }

    private void loadServers() {
        layoutLoading.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        tvLoadingMsg.setText("Connexion à VPN Gate…");
        btnConnect.setEnabled(false);

        VpnGateApi.fetchServers(new VpnGateApi.Callback() {
            @Override public void onSuccess(List<ServerModel> result) {
                layoutLoading.setVisibility(View.GONE);
                recycler.setVisibility(View.VISIBLE);
                servers.clear();
                servers.addAll(result);
                adapter.notifyDataSetChanged();
                tvStatus.setText(result.size() + " serveurs disponibles");
                if (!servers.isEmpty()) {
                    selectedServer = servers.get(0);
                    adapter.setSelected(0);
                }
                updateConnectButton();
            }
            @Override public void onError(String message) {
                layoutLoading.setVisibility(View.GONE);
                tvLoadingMsg.setText("Erreur : " + message);
                tvStatus.setText("❌ Chargement échoué");
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Connexion ────────────────────────────────────────────────────────

    private void connectVpn(ServerModel server) {
        // Vérifier si ics-openvpn est installé
        if (!IcsOpenVpnHelper.isInstalled(this)) {
            showInstallDialog();
            return;
        }
        connectedServer = server;
        updateUiState(WiseVpnService.STATE_CONNECTING, null);
        IcsOpenVpnHelper.startVpn(this, server);
    }

    private void disconnectVpn() {
        IcsOpenVpnHelper.stopVpn(this);
        updateUiState(WiseVpnService.STATE_DISCONNECTED, null);
    }

    // ── Dialog installation ics-openvpn ──────────────────────────────────

    private void showInstallDialog() {
        new AlertDialog.Builder(this)
            .setTitle("OpenVPN requis")
            .setMessage(
                "WiseVPN utilise OpenVPN for Android pour établir le tunnel sécurisé.\n\n" +
                "L'installation est gratuite et prend moins d'une minute.")
            .setPositiveButton("📥 Installer (Play Store)", (d, w) ->
                IcsOpenVpnHelper.openPlayStore(this))
            .setNegativeButton("Annuler", null)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }

    // ── Résultat import profil ics-openvpn ───────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IcsOpenVpnHelper.REQUEST_IMPORT) {
            if (resultCode == RESULT_OK) {
                // Profil importé — ics-openvpn connecte automatiquement
                updateUiState(WiseVpnService.STATE_CONNECTED, null);
                Toast.makeText(this, "✅ Tunnel VPN actif", Toast.LENGTH_SHORT).show();
            } else {
                updateUiState(WiseVpnService.STATE_DISCONNECTED, null);
                if (resultCode != RESULT_CANCELED) {
                    Toast.makeText(this, "Connexion annulée", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────

    private void updateUiState(String state, String errorMsg) {
        currentState = state;
        switch (state) {
            case WiseVpnService.STATE_CONNECTING:
                tvStatus.setText("⏳ Ouverture OpenVPN…");
                tvStatusDetail.setText(connectedServer != null ?
                    connectedServer.getCountryFlag() + " " + connectedServer.countryLong : "");
                btnConnect.setText("Annuler");
                btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_orange_dark));
                statusCard.setBackgroundTintList(getColorStateList(android.R.color.holo_orange_light));
                break;
            case WiseVpnService.STATE_CONNECTED:
                tvStatus.setText("✅ VPN Connecté");
                tvStatusDetail.setText(connectedServer != null ?
                    connectedServer.getCountryFlag() + " " + connectedServer.countryLong
                    + "  •  " + connectedServer.ip : "");
                btnConnect.setText("Déconnecter");
                btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_red_dark));
                statusCard.setBackgroundTintList(getColorStateList(android.R.color.holo_green_light));
                break;
            case WiseVpnService.STATE_DISCONNECTED:
                tvStatus.setText("🔴 Non connecté");
                tvStatusDetail.setText("Sélectionnez un serveur et appuyez sur Connecter");
                btnConnect.setText("Connecter");
                btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_dark));
                statusCard.setBackgroundTintList(getColorStateList(android.R.color.darker_gray));
                connectedServer = null;
                break;
            case WiseVpnService.STATE_ERROR:
                tvStatus.setText("❌ Erreur VPN");
                tvStatusDetail.setText(errorMsg != null ? errorMsg : "Erreur inconnue");
                btnConnect.setText("Réessayer");
                btnConnect.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_dark));
                statusCard.setBackgroundTintList(getColorStateList(android.R.color.holo_red_light));
                break;
        }
        updateConnectButton();
    }

    private void updateConnectButton() {
        boolean idle   = currentState.equals(WiseVpnService.STATE_DISCONNECTED) ||
                         currentState.equals(WiseVpnService.STATE_ERROR);
        boolean active = currentState.equals(WiseVpnService.STATE_CONNECTED) ||
                         currentState.equals(WiseVpnService.STATE_CONNECTING);
        btnConnect.setEnabled((idle && selectedServer != null) || active);
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(vpnReceiver,
            new IntentFilter(WiseVpnService.BROADCAST_STATE),
            Context.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(vpnReceiver); } catch (Exception ignored) {}
    }
}
