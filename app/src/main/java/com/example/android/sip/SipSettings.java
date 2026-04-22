package com.example.android.sip;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

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
        }
    }
}
