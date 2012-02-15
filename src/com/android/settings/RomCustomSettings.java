package com.android.settings;
import com.android.settings.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.text.Spannable;
import android.widget.EditText;
import android.util.Log;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

public class RomCustomSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String PREF_VOLUME_MUSIC = "volume_music_controls";
    private static final String QUAD_TARGETS = "pref_lockscreen_quad_targets";
    private static final String PREF_CLOCK_DISPLAY_STYLE = "clock_am_pm";
    private static final String PREF_CLOCK_STYLE = "clock_style";
    CheckBoxPreference mVolumeMusic;
    CheckBoxPreference mQuadTargets;
    private ListPreference mAmPmStyle;
    private ListPreference mClockStyle;

    private static final String BATTERY_STYLE = "battery_style";
    private static final String BATTERY_BAR = "battery_bar";
    private static final String BATTERY_BAR_COLOR = "battery_bar_color";
    private ListPreference mBatteryStyle;
    private CheckBoxPreference mBattBar;
    private ColorPickerPreference mBattBarColor;

    private static final String PREF_VOLUME_WAKE = "volume_wake";
    CheckBoxPreference mVolumeWake;
    
    private static final String NOTIFICATION_BUTTON_BACKLIGHT = "notification_button_backlight";
    private CheckBoxPreference mUseBLN;

    private static final String PREF_180 = "rotate_180";
    CheckBoxPreference mAllow180Rotation;

    private static final String PREF_CARRIER_TEXT = "carrier_text";
    private Preference mCarrier;
    String mCarrierText = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.rom_custom_settings);
        PreferenceScreen prefSet = getPreferenceScreen();

        mVolumeMusic = (CheckBoxPreference) prefSet.findPreference(PREF_VOLUME_MUSIC);
        mVolumeMusic.setChecked(Settings.System.getInt(getActivity()
            .getContentResolver(), Settings.System.VOLUME_MUSIC_CONTROLS,
            0) == 1);

        mClockStyle = (ListPreference) prefSet.findPreference(PREF_CLOCK_STYLE);
        mAmPmStyle = (ListPreference) prefSet.findPreference(PREF_CLOCK_DISPLAY_STYLE);

        int styleValue = Settings.System.getInt(getContentResolver(),
                Settings.System.STATUS_BAR_AM_PM, 2);
        mAmPmStyle.setValueIndex(styleValue);
        mAmPmStyle.setOnPreferenceChangeListener(this);

        int clockVal = Settings.System.getInt(getContentResolver(),
                Settings.System.STATUS_BAR_CLOCK, 1);
        mClockStyle.setValueIndex(clockVal);
        mClockStyle.setOnPreferenceChangeListener(this);

	mBatteryStyle = (ListPreference) prefSet.findPreference(BATTERY_STYLE);
        int battVal = Settings.System.getInt(getContentResolver(),
                Settings.System.BATTERY_PERCENTAGES, 1);
        mBatteryStyle.setValueIndex(battVal);
        mBatteryStyle.setOnPreferenceChangeListener(this);

        mVolumeWake = (CheckBoxPreference) findPreference(PREF_VOLUME_WAKE);
        mVolumeWake.setChecked(Settings.System.getInt(getActivity()
                .getContentResolver(), Settings.System.VOLUME_WAKE_SCREEN,	
                0) == 1);

        mBattBar = (CheckBoxPreference) prefSet.findPreference(BATTERY_BAR);
        mBattBar.setChecked(Settings.System.getInt(getContentResolver(),
            Settings.System.STATUSBAR_BATTERY_BAR, 0) == 1);

        mBattBarColor = (ColorPickerPreference) prefSet.findPreference(BATTERY_BAR_COLOR);
        mBattBarColor.setOnPreferenceChangeListener(this);
        mBattBarColor.setEnabled(mBattBar.isChecked());

        mCarrier = (Preference) prefSet.findPreference(PREF_CARRIER_TEXT);
        updateCarrierText();

	mAllow180Rotation = (CheckBoxPreference) findPreference(PREF_180);
        mAllow180Rotation.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
            Settings.System.ACCELEROMETER_ROTATION_ANGLES, (1 | 2 | 8)) == (1 | 2 | 4 | 8));

	mUseBLN = (CheckBoxPreference) prefSet.findPreference(NOTIFICATION_BUTTON_BACKLIGHT);
	mUseBLN.setChecked(Settings.System.getInt(getContentResolver(),
            Settings.System.NOTIFICATION_USE_BUTTON_BACKLIGHT, 0) == 1);
    }

    private void updateBatteryBarToggle(boolean bool){
        if (bool)
            mBattBarColor.setEnabled(true);
        else
            mBattBarColor.setEnabled(false);
    }

    private void updateCarrierText() {
        mCarrierText = Settings.System.getString(getContentResolver(), Settings.System.CUSTOM_CARRIER_TEXT);
        if (mCarrierText == null) {
            mCarrier.setSummary("Upon changing you will need to data wipe to get back stock. Requires reboot.");
        } else {
            mCarrier.setSummary(mCarrierText);
        }
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean value;
	if (preference == mVolumeMusic) {
            Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.VOLUME_MUSIC_CONTROLS,
		((CheckBoxPreference) preference).isChecked() ? 1 : 0);
	    return true;
	} else if (preference == mVolumeWake) {
            Settings.System.putInt(getActivity().getContentResolver(),
            Settings.System.VOLUME_WAKE_SCREEN,
                ((CheckBoxPreference) preference).isChecked() ? 1 : 0);	
            return true;
        } else if (preference == mBattBar) {
            value = mBattBar.isChecked();
            Settings.System.putInt(getContentResolver(),
                Settings.System.STATUSBAR_BATTERY_BAR, value ? 1 : 0);
            return true;
	} else if (preference == mAllow180Rotation) {
            boolean checked = ((CheckBoxPreference) preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION_ANGLES, checked ? (1 | 2 | 4 | 8)
                    : (1 | 2 | 8));
            return true;
	} else if (preference == mUseBLN) {
            value = mUseBLN.isChecked();
            Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_USE_BUTTON_BACKLIGHT, value ? 1 : 0);
            return true;
	} else if (preference == mCarrier) {
            AlertDialog.Builder ad = new AlertDialog.Builder(getActivity());
            ad.setTitle("Custom Carrier Text");
            ad.setMessage("Enter new carrier text here");
            final EditText text = new EditText(getActivity());
            text.setText(mCarrierText != null ? mCarrierText : "");
            ad.setView(text);
            ad.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String value = ((Spannable) text.getText()).toString();
                    Settings.System.putString(getActivity().getContentResolver(), Settings.System.CUSTOM_CARRIER_TEXT, value);
                    updateCarrierText();
                }
            });
            ad.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            });
            ad.show();
        }

        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAmPmStyle) {
            int statusBarAmPm = Integer.valueOf((String) newValue);
            Settings.System.putInt(getContentResolver(),
                Settings.System.STATUS_BAR_AM_PM, statusBarAmPm);
            return true;
        } else if (preference == mClockStyle) {
            int val = Integer.valueOf((String) newValue);
            Settings.System.putInt(getContentResolver(),
                Settings.System.STATUS_BAR_CLOCK, val);
            return true;
	} else if (preference == mBatteryStyle) {
            int val = Integer.valueOf((String) newValue);
            Settings.System.putInt(getContentResolver(),
                Settings.System.BATTERY_PERCENTAGES, val);
            return true;
        } else if (preference == mBattBarColor) {
            String hexColor = ColorPickerPreference.convertToARGB(Integer.valueOf(String.valueOf(newValue)));
            preference.setSummary(hexColor);
            int color = ColorPickerPreference.convertToColorInt(hexColor);
            Settings.System.putInt(getContentResolver(),
                Settings.System.STATUSBAR_BATTERY_BAR_COLOR, color);
            return true;
        }

        return false;
    }

}

