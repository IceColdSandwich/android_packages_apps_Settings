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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settings.R;

public class NamePreference extends Preference implements
    View.OnClickListener, Preference.OnPreferenceChangeListener {
    private static final String TAG = NamePreference.class.getSimpleName();

    private TextView mNameView;

    private String mName;

    /**
     * @param context
     * @param title
     * @param summary
     */
    public NamePreference(Context context, String name) {
        super(context);
        mName = name.toString();
        init();
    }

    /**
     * @param context
     */
    public NamePreference(Context context) {
        super(context);
        init();
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);

        View namePref = view.findViewById(R.id.name_pref);
        if ((namePref != null) && namePref instanceof LinearLayout) {
            namePref.setOnClickListener(this);
        }

        mNameView = (TextView) view.findViewById(R.id.title);

        updatePreferenceViews();
    }

    private void init() {
        setLayoutResource(R.layout.preference_name);
    }

    public void setName(String name) {
        mName = (name.toString());
        updatePreferenceViews();
    }

    public String getName() {
        return(mName.toString());
    }

    private void updatePreferenceViews() {
        if (mNameView != null) {
            mNameView.setText(mName.toString());
        }
    }

    @Override
    public void onClick(android.view.View v) {
        if (v != null) {
            Context context = getContext();
            AlertDialog.Builder dialog = new AlertDialog.Builder(context);
            LayoutInflater factory = LayoutInflater.from(context);
            final View dialogView = factory.inflate(R.layout.rename_profile_dialog, null);
            final EditText pname = (EditText) dialogView.findViewById(R.id.profile_name);
            pname.setText(mName.toString());
            dialog.setTitle(R.string.rename_profile_dialog_title);
            dialog.setView(dialogView);
            dialog.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String value = pname.getText().toString();
                            mName = value.toString();
                            mNameView.setText(value.toString());
                            callChangeListener(this);
                        }
                    });
            dialog.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
            dialog.create().show();
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        callChangeListener(preference);
        return false;
    }
}
