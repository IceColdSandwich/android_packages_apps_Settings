package com.android.settings;
import com.android.settings.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;


public class RomCustomSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String QUAD_TARGETS = "pref_lockscreen_quad_targets";
    CheckBoxPreference mQuadTargets;

    private static final String PREF_VOLUME_WAKE = "volume_wake";
    CheckBoxPreference mVolumeWake;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.rom_custom_settings);
        PreferenceScreen prefSet = getPreferenceScreen();

        mVolumeWake = (CheckBoxPreference) findPreference(PREF_VOLUME_WAKE);
        mVolumeWake.setChecked(Settings.System.getInt(getActivity()
                .getContentResolver(), Settings.System.VOLUME_WAKE_SCREEN,	
                0) == 1);
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean value;
	if (preference == mVolumeWake) {
            Settings.System.putInt(getActivity().getContentResolver(),
            Settings.System.VOLUME_WAKE_SCREEN,
                ((CheckBoxPreference) preference).isChecked() ? 1 : 0);	
            return true;
	}

	return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

}

