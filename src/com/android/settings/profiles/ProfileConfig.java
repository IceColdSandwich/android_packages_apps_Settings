/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.profiles;

import java.util.UUID;

import android.app.AlertDialog;
import android.app.ConnectionSettings;
import android.app.Profile;
import android.app.ProfileGroup;
import android.app.ProfileManager;
import android.app.StreamSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class ProfileConfig extends SettingsPreferenceFragment
    implements Preference.OnPreferenceChangeListener {

    static final String TAG = "ProfileConfig";

    public static final String PROFILE_SERVICE = "profile";

    private ProfileManager mProfileManager;

    private static final int MENU_DELETE = Menu.FIRST;

    private Profile mProfile;

    private NamePreference mNamePreference;

    // constant value that can be used to check return code from sub activity.
    private static final int PROFILE_GROUP_DETAILS = 1;

    private StreamItem[] mStreams;

    private ConnectionItem[] mConnections;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mStreams = new StreamItem[] {
                new StreamItem(AudioManager.STREAM_ALARM, getString(R.string.alarm_volume_title)),
                new StreamItem(AudioManager.STREAM_MUSIC, getString(R.string.media_volume_title)),
                new StreamItem(AudioManager.STREAM_RING, getString(R.string.incoming_call_volume_title)),
                new StreamItem(AudioManager.STREAM_NOTIFICATION, getString(R.string.notification_volume_title))
        };

        mConnections = new ConnectionItem[] {
                new ConnectionItem(ConnectionSettings.PROFILE_CONNECTION_BLUETOOTH, getString(R.string.toggleBluetooth)),
                new ConnectionItem(ConnectionSettings.PROFILE_CONNECTION_GPS, getString(R.string.toggleGPS)),
                new ConnectionItem(ConnectionSettings.PROFILE_CONNECTION_WIFI, getString(R.string.toggleWifi)),
                new ConnectionItem(ConnectionSettings.PROFILE_CONNECTION_WIFIAP, getString(R.string.toggleWifiAp))
                //new ConnectionItem(ConnectivityManager.TYPE_WIMAX, getString(R.string.toggleWimax))
        };

        addPreferencesFromResource(R.xml.profile_config);

        mProfileManager = (ProfileManager) getActivity().getSystemService(PROFILE_SERVICE);

        final Bundle args = getArguments();
        mProfile = (args != null) ? (Profile) args.getParcelable("Profile") : null;

        if (mProfile == null) {
            mProfile = new Profile(getString(R.string.new_profile_name));
            mProfileManager.addProfile(mProfile);
        }

        setHasOptionsMenu(true);

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem delete = menu.add(0, MENU_DELETE, 1, R.string.profile_menu_delete)
                .setIcon(R.drawable.ic_menu_trash_holo_dark);
        delete.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DELETE:
                deleteProfile();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mProfile = mProfileManager.getProfile(mProfile.getUuid());
        fillList();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save profile here
        if (mProfile != null) {
            mProfileManager.updateProfile(mProfile);
        }
    }

    private void fillList() {
        PreferenceScreen prefSet = getPreferenceScreen();

        // Add the General section
        PreferenceGroup generalPrefs = (PreferenceGroup) prefSet.findPreference("profile_general_section");
        if (generalPrefs != null) {
            generalPrefs.removeAll();

            // Name preference
            mNamePreference = new NamePreference(getActivity(), mProfile.getName());
            mNamePreference.setOnPreferenceChangeListener(this);
            generalPrefs.addPreference(mNamePreference);
        }

        // Populate the audio streams list
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PreferenceGroup streamList = (PreferenceGroup) prefSet.findPreference("profile_volumeoverrides");
        if (streamList != null) {
            streamList.removeAll();
            for (StreamItem stream : mStreams) {
                StreamSettings settings = mProfile.getSettingsForStream(stream.mStreamId);
                if (settings == null) {
                    settings = new StreamSettings(stream.mStreamId);
                    mProfile.setStreamSettings(settings);
                }
                stream.mSettings = settings;
                StreamVolumePreference pref = new StreamVolumePreference(getActivity());
                pref.setKey("stream_" + stream.mStreamId);
                pref.setTitle(stream.mLabel);
                pref.setSummary(getString(R.string.volume_override_summary) + " " + settings.getValue() 
                        + "/" + am.getStreamMaxVolume(stream.mStreamId)); 
                pref.setPersistent(false);
                pref.setStreamItem(stream);
                stream.mCheckbox = pref;
                streamList.addPreference(pref);
            }
        }

        // Populate Connections list
        PreferenceGroup connectionList = (PreferenceGroup) prefSet.findPreference("profile_connectionoverrides");
        if (connectionList != null) {
            connectionList.removeAll();
            for (ConnectionItem connection : mConnections) {
                ConnectionSettings settings = mProfile.getSettingsForConnection(connection.mConnectionId);
                if (settings == null) {
                    settings = new ConnectionSettings(connection.mConnectionId);
                    mProfile.setConnectionSettings(settings);
                }
                connection.mSettings = settings;
                ProfileConnectionPreference pref = new ProfileConnectionPreference(getActivity());
                pref.setKey("connection_" + connection.mConnectionId);
                pref.setTitle(connection.mLabel);
                pref.setSummary(settings.getValue() == 1 ? getString(R.string.connection_state_enabled) 
                        : getString(R.string.connection_state_disabled));
                pref.setPersistent(false);
                pref.setConnectionItem(connection);
                connection.mCheckbox = pref;
                connectionList.addPreference(pref);
            }
        }

        // Populate Application groups
        PreferenceGroup groupList = (PreferenceGroup) prefSet.findPreference("profile_appgroups");
        if (groupList != null) {
            groupList.removeAll();
            for (ProfileGroup profileGroup : mProfile.getProfileGroups()) {
                PreferenceScreen pref = new PreferenceScreen(getActivity(), null);
                UUID uuid = profileGroup.getUuid();
                pref.setKey(uuid.toString());
                pref.setTitle(mProfileManager.getNotificationGroup(uuid).getName());
                //pref.setSummary(R.string.profile_summary);  // summary is repetitive, consider removing
                pref.setPersistent(false);
                pref.setSelectable(true);
                groupList.addPreference(pref);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference instanceof StreamVolumePreference) {
            for (StreamItem stream : mStreams) {
                if (preference == stream.mCheckbox) {
                    stream.mSettings.setOverride((Boolean) newValue);
                }
            }
        } else if (preference instanceof ProfileConnectionPreference) {
            for (ConnectionItem connection : mConnections) {
                if (preference == connection.mCheckbox) {
                    connection.mSettings.setOverride((Boolean) newValue);
                }
            }
        } else if (preference == mNamePreference) {
            String name = mNamePreference.getName().toString();
            if (!mProfileManager.profileExists(name)) {
                mProfile.setName(name);
            } else {
                mNamePreference.setName(mProfile.getName());
                Toast.makeText(getActivity(), R.string.duplicate_profile_name, Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        Log.d(TAG, "onPreferenceTreeClick(): entered" + preferenceScreen.getKey() + preference.getKey());
        if (preference instanceof PreferenceScreen) {
            startProfileGroupActivity(preference.getKey(), preference.getTitle().toString());
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void startProfileGroupActivity(String key, String title) {
        Bundle args = new Bundle();
        args.putString("ProfileGroup", key.toString());
        args.putParcelable("Profile", mProfile);

        String header = mProfile.getName().toString() + ": " + title.toString();
        PreferenceActivity pa = (PreferenceActivity) getActivity();
        pa.startPreferencePanel(ProfileGroupConfig.class.getName(), args,
                0, header, this, PROFILE_GROUP_DETAILS);
    }

    
    private void deleteProfile() {
        if (mProfile.getUuid().equals(mProfileManager.getActiveProfile().getUuid())) {
            Toast toast = Toast.makeText(getActivity(), getString(R.string.profile_cannot_delete),
                    Toast.LENGTH_SHORT);
            toast.show();
        } else {
            AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
            alert.setTitle(R.string.profile_menu_delete);
            alert.setIcon(android.R.drawable.ic_dialog_alert);
            alert.setMessage(R.string.profile_delete_confirm);
            alert.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    doDelete();
                }
            });
            alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
            alert.create().show();
        }
    }

    private void doDelete() {
        mProfileManager.removeProfile(mProfile);
        mProfile = null;
        finish();
    }

    static class StreamItem {
        int mStreamId;
        String mLabel;
        StreamSettings mSettings;
        StreamVolumePreference mCheckbox;

        public StreamItem(int streamId, String label) {
            mStreamId = streamId;
            mLabel = label;
        }
    }

    static class ConnectionItem {
        int mConnectionId;
        String mLabel;
        ConnectionSettings mSettings;
        ProfileConnectionPreference mCheckbox;

        public ConnectionItem(int connectionId, String label) {
            mConnectionId = connectionId;
            mLabel = label;
        }
    }
}
