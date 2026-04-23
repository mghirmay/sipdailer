package com.example.android.sip;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SipSettings extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("SIP Settings");
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SipPreferenceFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SipPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            Preference reconnectPref = findPreference("reconnectPref");
            if (reconnectPref != null) {
                reconnectPref.setOnPreferenceClickListener(preference -> {
                    MainActivity activity = (MainActivity) getActivity();
                    if (activity != null) {
                        activity.forceRegister();
                    }
                    return true;
                });
            }

            Preference loginPref = findPreference("loginPref");
            if (loginPref != null) {
                loginPref.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(getActivity(), LoginActivity.class));
                    return true;
                });
            }

            Preference logoutPref = findPreference("logoutPref");
            if (logoutPref != null) {
                logoutPref.setOnPreferenceClickListener(preference -> {
                    showLogoutConfirmation();
                    return true;
                });
            }
        }

        private void showLogoutConfirmation() {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout? This will clear your credentials.")
                    .setPositiveButton("Logout", (dialog, which) -> {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                        prefs.edit()
                                .remove("namePref")
                                .remove("passPref")
                                .putBoolean("enabledPref", false)
                                .apply();

                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }
}
