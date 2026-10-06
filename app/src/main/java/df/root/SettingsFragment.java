package df.root;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import com.nzs.mroot.R;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context ctx = requireContext();
        SharedPreferences dePrefs = ctx.createDeviceProtectedStorageContext()
                .getSharedPreferences(ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE);
        getPreferenceManager().setPreferenceDataStore(new DePreferenceDataStore(dePrefs));
        setPreferencesFromResource(R.xml.preferences, rootKey);

        ComponentName bootReceiver = new ComponentName(ctx, BootReceiver.class);
        SwitchPreferenceCompat bootPref = findPreference("boot_start");
        int state = ctx.getPackageManager().getComponentEnabledSetting(bootReceiver);
        bootPref.setChecked(state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        bootPref.setOnPreferenceChangeListener((pref, value) -> {
            ctx.getPackageManager().setComponentEnabledSetting(bootReceiver,
                    (Boolean) value ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            return true;
        });
    }

    private static class DePreferenceDataStore extends PreferenceDataStore {
        private final SharedPreferences mPrefs;

        DePreferenceDataStore(SharedPreferences prefs) {
            mPrefs = prefs;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            mPrefs.edit().putBoolean(key, value).apply();
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return mPrefs.getBoolean(key, defValue);
        }
    }
}
