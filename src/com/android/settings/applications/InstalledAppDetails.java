/**
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.settings.applications;

import com.android.settings.R;
import com.android.settings.applications.ApplicationsState.AppEntry;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.IUsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceActivity;
import android.text.format.Formatter;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import android.content.ComponentName;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AppSecurityPermissions;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Activity to display application information from Settings. This activity presents
 * extended information associated with a package like code, data, total size, permissions
 * used by the application and also the set of default launchable activities.
 * For system applications, an option to clear user data is displayed only if data size is > 0.
 * System applications that do not want clear user data do not have this option.
 * For non-system applications, there is no option to clear data. Instead there is an option to
 * uninstall the application.
 */
public class InstalledAppDetails extends Fragment
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        ApplicationsState.Callbacks {
    private static final String TAG="InstalledAppDetails";
    static final boolean SUPPORT_DISABLE_APPS = true;
    private static final boolean localLOGV = false;
    
    public static final String ARG_PACKAGE_NAME = "package";

    private PackageManager mPm;
    private IUsbManager mUsbManager;
    private DevicePolicyManager mDpm;
    private ApplicationsState mState;
    private ApplicationsState.AppEntry mAppEntry;
    private PackageInfo mPackageInfo;
    private CanBeOnSdCardChecker mCanBeOnSdCardChecker;
    private View mRootView;
    private Button mUninstallButton;
    private boolean mMoveInProgress = false;
    private boolean mUpdatedSysApp = false;
    private Button mActivitiesButton;
    private View mScreenCompatSection;
    private CheckBox mAskCompatibilityCB;
    private CheckBox mEnableCompatibilityCB;
    private boolean mCanClearData = true;
    private TextView mAppVersion;
    private TextView mTotalSize;
    private TextView mAppSize;
    private TextView mDataSize;
    private TextView mExternalCodeSize;
    private TextView mExternalDataSize;
    private ClearUserDataObserver mClearDataObserver;
    // Views related to cache info
    private TextView mCacheSize;
    private Button mClearCacheButton;
    private ClearCacheObserver mClearCacheObserver;
    private Button mForceStopButton;
    private Button mClearDataButton;
    private Button mMoveAppButton;
    
    private PackageMoveObserver mPackageMoveObserver;
    
    private boolean mHaveSizes = false;
    private long mLastCodeSize = -1;
    private long mLastDataSize = -1;
    private long mLastExternalCodeSize = -1;
    private long mLastExternalDataSize = -1;
    private long mLastCacheSize = -1;
    private long mLastTotalSize = -1;
    
    //internal constants used in Handler
    private static final int OP_SUCCESSFUL = 1;
    private static final int OP_FAILED = 2;
    private static final int CLEAR_USER_DATA = 1;
    private static final int CLEAR_CACHE = 3;
    private static final int PACKAGE_MOVE = 4;
    
    // invalid size value used initially and also when size retrieval through PackageManager
    // fails for whatever reason
    private static final int SIZE_INVALID = -1;
    
    // Resource strings
    private CharSequence mInvalidSizeStr;
    private CharSequence mComputingStr;
    
    // Dialog identifiers used in showDialog
    private static final int DLG_BASE = 0;
    private static final int DLG_CLEAR_DATA = DLG_BASE + 1;
    private static final int DLG_FACTORY_RESET = DLG_BASE + 2;
    private static final int DLG_APP_NOT_FOUND = DLG_BASE + 3;
    private static final int DLG_CANNOT_CLEAR_DATA = DLG_BASE + 4;
    private static final int DLG_FORCE_STOP = DLG_BASE + 5;
    private static final int DLG_MOVE_FAILED = DLG_BASE + 6;
    private static final int DLG_DISABLE = DLG_BASE + 7;
    
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            // If the fragment is gone, don't process any more messages.
            if (getView() == null) {
                return;
            }
            switch (msg.what) {
                case CLEAR_USER_DATA:
                    processClearMsg(msg);
                    break;
                case CLEAR_CACHE:
                    // Refresh size info
                    mState.requestSize(mAppEntry.info.packageName);
                    break;
                case PACKAGE_MOVE:
                    processMoveMsg(msg);
                    break;
                default:
                    break;
            }
        }
    };
    
    class ClearUserDataObserver extends IPackageDataObserver.Stub {
       public void onRemoveCompleted(final String packageName, final boolean succeeded) {
           final Message msg = mHandler.obtainMessage(CLEAR_USER_DATA);
           msg.arg1 = succeeded?OP_SUCCESSFUL:OP_FAILED;
           mHandler.sendMessage(msg);
        }
    }
    
    class ClearCacheObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
            final Message msg = mHandler.obtainMessage(CLEAR_CACHE);
            msg.arg1 = succeeded ? OP_SUCCESSFUL:OP_FAILED;
            mHandler.sendMessage(msg);
         }
     }

    class PackageMoveObserver extends IPackageMoveObserver.Stub {
        public void packageMoved(String packageName, int returnCode) throws RemoteException {
            final Message msg = mHandler.obtainMessage(PACKAGE_MOVE);
            msg.arg1 = returnCode;
            mHandler.sendMessage(msg);
        }
    }
    
    private String getSizeStr(long size) {
        if (size == SIZE_INVALID) {
            return mInvalidSizeStr.toString();
        }
        return Formatter.formatFileSize(getActivity(), size);
    }
    
    private void initDataButtons() {
        if ((mAppEntry.info.flags&(ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA))
                == ApplicationInfo.FLAG_SYSTEM
                || mDpm.packageHasActiveAdmins(mPackageInfo.packageName)) {
            mClearDataButton.setText(R.string.clear_user_data_text);
            mClearDataButton.setEnabled(false);
            mCanClearData = false;
        } else {
            if (mAppEntry.info.manageSpaceActivityName != null) {
                mClearDataButton.setText(R.string.manage_space_text);
            } else {
                mClearDataButton.setText(R.string.clear_user_data_text);
            }
            mClearDataButton.setOnClickListener(this);
        }
    }

    private CharSequence getMoveErrMsg(int errCode) {
        switch (errCode) {
            case PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE:
                return getActivity().getString(R.string.insufficient_storage);
            case PackageManager.MOVE_FAILED_DOESNT_EXIST:
                return getActivity().getString(R.string.does_not_exist);
            case PackageManager.MOVE_FAILED_FORWARD_LOCKED:
                return getActivity().getString(R.string.app_forward_locked);
            case PackageManager.MOVE_FAILED_INVALID_LOCATION:
                return getActivity().getString(R.string.invalid_location);
            case PackageManager.MOVE_FAILED_SYSTEM_PACKAGE:
                return getActivity().getString(R.string.system_package);
            case PackageManager.MOVE_FAILED_INTERNAL_ERROR:
                return "";
        }
        return "";
    }

    private void initMoveButton() {
        if (Environment.isExternalStorageEmulated()) {
            mMoveAppButton.setVisibility(View.INVISIBLE);
            return;
        }
        boolean dataOnly = false;
        dataOnly = (mPackageInfo == null) && (mAppEntry != null);
        boolean moveDisable = true;
        if (dataOnly) {
            mMoveAppButton.setText(R.string.move_app);
        } else if ((mAppEntry.info.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
            mMoveAppButton.setText(R.string.move_app_to_internal);
            // Always let apps move to internal storage from sdcard.
            moveDisable = false;
        } else {
            mMoveAppButton.setText(R.string.move_app_to_sdcard);
            mCanBeOnSdCardChecker.init();
            moveDisable = !mCanBeOnSdCardChecker.check(mAppEntry.info);
        }
        if (moveDisable) {
            mMoveAppButton.setEnabled(false);
        } else {
            mMoveAppButton.setOnClickListener(this);
            mMoveAppButton.setEnabled(true);
        }
    }

    private void initUninstallButtons() {
        mUpdatedSysApp = (mAppEntry.info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        boolean enabled = true;
        if (mUpdatedSysApp) {
            mUninstallButton.setText(R.string.app_factory_reset);
        } else {
            if ((mAppEntry.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                enabled = false;
                if (SUPPORT_DISABLE_APPS) {
                    try {
                        // Try to prevent the user from bricking their phone
                        // by not allowing disabling of apps signed with the
                        // system cert and any launcher app in the system.
                        PackageInfo sys = mPm.getPackageInfo("android",
                                PackageManager.GET_SIGNATURES);
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setPackage(mAppEntry.info.packageName);
                        List<ResolveInfo> homes = mPm.queryIntentActivities(intent, 0);
                        if ((homes != null && homes.size() > 0) ||
                                (mPackageInfo != null && mPackageInfo.signatures != null &&
                                        sys.signatures[0].equals(mPackageInfo.signatures[0]))) {
                            // Disable button for core system applications.
                            mUninstallButton.setText(R.string.disable_text);
                        } else if (mAppEntry.info.enabled) {
                            mUninstallButton.setText(R.string.disable_text);
                            enabled = true;
                        } else {
                            mUninstallButton.setText(R.string.enable_text);
                            enabled = true;
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "Unable to get package info", e);
                    }
                }
            } else {
                mUninstallButton.setText(R.string.uninstall_text);
            }
        }
        // If this is a device admin, it can't be uninstall or disabled.
        // We do this here so the text of the button is still set correctly.
        if (mDpm.packageHasActiveAdmins(mPackageInfo.packageName)) {
            enabled = false;
        }
        mUninstallButton.setEnabled(enabled);
        if (enabled) {
            // Register listener
            mUninstallButton.setOnClickListener(this);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        mState = ApplicationsState.getInstance(getActivity().getApplication());
        mPm = getActivity().getPackageManager();
        IBinder b = ServiceManager.getService(Context.USB_SERVICE);
        mUsbManager = IUsbManager.Stub.asInterface(b);
        mDpm = (DevicePolicyManager)getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);

        mCanBeOnSdCardChecker = new CanBeOnSdCardChecker();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = mRootView = inflater.inflate(R.layout.installed_app_details, null);
        
        mComputingStr = getActivity().getText(R.string.computing_size);
        
        // Set default values on sizes
        mTotalSize = (TextView)view.findViewById(R.id.total_size_text);
        mAppSize = (TextView)view.findViewById(R.id.application_size_text);
        mDataSize = (TextView)view.findViewById(R.id.data_size_text);
        mExternalCodeSize = (TextView)view.findViewById(R.id.external_code_size_text);
        mExternalDataSize = (TextView)view.findViewById(R.id.external_data_size_text);
        
        // Get Control button panel
        View btnPanel = view.findViewById(R.id.control_buttons_panel);
        mForceStopButton = (Button) btnPanel.findViewById(R.id.left_button);
        mForceStopButton.setText(R.string.force_stop);
        mUninstallButton = (Button)btnPanel.findViewById(R.id.right_button);
        mForceStopButton.setEnabled(false);
        
        // Initialize clear data and move install location buttons
        View data_buttons_panel = view.findViewById(R.id.data_buttons_panel);
        mClearDataButton = (Button) data_buttons_panel.findViewById(R.id.left_button);
        mMoveAppButton = (Button) data_buttons_panel.findViewById(R.id.right_button);
        
        // Cache section
        mCacheSize = (TextView) view.findViewById(R.id.cache_size_text);
        mClearCacheButton = (Button) view.findViewById(R.id.clear_cache_button);

        mActivitiesButton = (Button)view.findViewById(R.id.clear_activities_button);
        
        // Screen compatibility control
        mScreenCompatSection = view.findViewById(R.id.screen_compatibility_section);
        mAskCompatibilityCB = (CheckBox)view.findViewById(R.id.ask_compatibility_cb);
        mEnableCompatibilityCB = (CheckBox)view.findViewById(R.id.enable_compatibility_cb);

        return view;
    }

    // Utility method to set applicaiton label and icon.
    private void setAppLabelAndIcon(PackageInfo pkgInfo) {
        View appSnippet = mRootView.findViewById(R.id.app_snippet);
        ImageView icon = (ImageView) appSnippet.findViewById(R.id.app_icon);
        mState.ensureIcon(mAppEntry);
        icon.setImageDrawable(mAppEntry.icon);
        // Set application name.
        TextView label = (TextView) appSnippet.findViewById(R.id.app_name);
        label.setText(mAppEntry.label);
        // Version number of application
        mAppVersion = (TextView) appSnippet.findViewById(R.id.app_size);

        if (pkgInfo != null && pkgInfo.versionName != null) {
            mAppVersion.setVisibility(View.VISIBLE);
            mAppVersion.setText(getActivity().getString(R.string.version_text,
                    String.valueOf(pkgInfo.versionName)));
        } else {
            mAppVersion.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        mState.resume(this);
        if (!refreshUi()) {
            setIntentAndFinish(true, true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mState.pause();
    }

    @Override
    public void onAllSizesComputed() {
    }

    @Override
    public void onPackageIconChanged() {
    }

    @Override
    public void onPackageListChanged() {
        refreshUi();
    }

    @Override
    public void onRebuildComplete(ArrayList<AppEntry> apps) {
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
        if (packageName.equals(mAppEntry.info.packageName)) {
            refreshSizeInfo();
        }
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    private boolean refreshUi() {
        if (mMoveInProgress) {
            return true;
        }
        final Bundle args = getArguments();
        String packageName = (args != null) ? args.getString(ARG_PACKAGE_NAME) : null;
        if (packageName == null) {
            Intent intent = (args == null) ?
                    getActivity().getIntent() : (Intent) args.getParcelable("intent");
            if (intent != null) {
                packageName = intent.getData().getSchemeSpecificPart();
            }
        }
        mAppEntry = mState.getEntry(packageName);
        
        if (mAppEntry == null) {
            return false; // onCreate must have failed, make sure to exit
        }
        
        // Get application info again to refresh changed properties of application
        try {
            mPackageInfo = mPm.getPackageInfo(mAppEntry.info.packageName,
                    PackageManager.GET_DISABLED_COMPONENTS |
                    PackageManager.GET_UNINSTALLED_PACKAGES |
                    PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Exception when retrieving package:" + mAppEntry.info.packageName, e);
            return false; // onCreate must have failed, make sure to exit
        }
        
        // Get list of preferred activities
        List<ComponentName> prefActList = new ArrayList<ComponentName>();
        
        // Intent list cannot be null. so pass empty list
        List<IntentFilter> intentList = new ArrayList<IntentFilter>();
        mPm.getPreferredActivities(intentList, prefActList, packageName);
        if(localLOGV) Log.i(TAG, "Have "+prefActList.size()+" number of activities in prefered list");
        boolean hasUsbDefaults = false;
        try {
            hasUsbDefaults = mUsbManager.hasDefaults(packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "mUsbManager.hasDefaults", e);
        }
        TextView autoLaunchView = (TextView)mRootView.findViewById(R.id.auto_launch);
        if (prefActList.size() <= 0 && !hasUsbDefaults) {
            // Disable clear activities button
            autoLaunchView.setText(R.string.auto_launch_disable_text);
            mActivitiesButton.setEnabled(false);
        } else {
            autoLaunchView.setText(R.string.auto_launch_enable_text);
            mActivitiesButton.setEnabled(true);
            mActivitiesButton.setOnClickListener(this);
        }

        // Screen compatibility section.
        ActivityManager am = (ActivityManager)
                getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        int compatMode = am.getPackageScreenCompatMode(packageName);
        // For now these are always off; this is the old UI model which we
        // are no longer using.
        if (false && (compatMode == ActivityManager.COMPAT_MODE_DISABLED
                || compatMode == ActivityManager.COMPAT_MODE_ENABLED)) {
            mScreenCompatSection.setVisibility(View.VISIBLE);
            mAskCompatibilityCB.setChecked(am.getPackageAskScreenCompat(packageName));
            mAskCompatibilityCB.setOnCheckedChangeListener(this);
            mEnableCompatibilityCB.setChecked(compatMode == ActivityManager.COMPAT_MODE_ENABLED);
            mEnableCompatibilityCB.setOnCheckedChangeListener(this);
        } else {
            mScreenCompatSection.setVisibility(View.GONE);
        }

        // Security permissions section
        LinearLayout permsView = (LinearLayout) mRootView.findViewById(R.id.permissions_section);
        AppSecurityPermissions asp = new AppSecurityPermissions(getActivity(), packageName);
        if (asp.getPermissionCount() > 0) {
            permsView.setVisibility(View.VISIBLE);
            // Make the security sections header visible
            LinearLayout securityList = (LinearLayout) permsView.findViewById(
                    R.id.security_settings_list);
            securityList.removeAllViews();
            securityList.addView(asp.getPermissionsView());
        } else {
            permsView.setVisibility(View.GONE);
        }
        
        checkForceStop();
        setAppLabelAndIcon(mPackageInfo);
        refreshButtons();
        refreshSizeInfo();
        return true;
    }
    
    private void setIntentAndFinish(boolean finish, boolean appChanged) {
        if(localLOGV) Log.i(TAG, "appChanged="+appChanged);
        Intent intent = new Intent();
        intent.putExtra(ManageApplications.APP_CHG, appChanged);
        PreferenceActivity pa = (PreferenceActivity)getActivity();
        pa.finishPreferencePanel(this, Activity.RESULT_OK, intent);
    }
    
    private void refreshSizeInfo() {
        if (mAppEntry.size == ApplicationsState.SIZE_INVALID
                || mAppEntry.size == ApplicationsState.SIZE_UNKNOWN) {
            mLastCodeSize = mLastDataSize = mLastCacheSize = mLastTotalSize = -1;
            if (!mHaveSizes) {
                mAppSize.setText(mComputingStr);
                mDataSize.setText(mComputingStr);
                mCacheSize.setText(mComputingStr);
                mTotalSize.setText(mComputingStr);
            }
            mClearDataButton.setEnabled(false);
            mClearCacheButton.setEnabled(false);
            
        } else {
            mHaveSizes = true;
            if (mLastCodeSize != mAppEntry.codeSize) {
                mLastCodeSize = mAppEntry.codeSize;
                mAppSize.setText(getSizeStr(mAppEntry.codeSize));
            }
            if (mLastDataSize != mAppEntry.dataSize) {
                mLastDataSize = mAppEntry.dataSize;
                mDataSize.setText(getSizeStr(mAppEntry.dataSize));
            }
            if (mLastExternalCodeSize != mAppEntry.externalCodeSize) {
                mLastExternalCodeSize = mAppEntry.externalCodeSize;
                mExternalCodeSize.setText(getSizeStr(mAppEntry.externalCodeSize));
            }
            if (mLastExternalDataSize != mAppEntry.externalDataSize) {
                mLastExternalDataSize = mAppEntry.externalDataSize;
                mExternalDataSize.setText(getSizeStr(mAppEntry.externalDataSize));
            }
            if (mLastCacheSize != mAppEntry.cacheSize) {
                mLastCacheSize = mAppEntry.cacheSize;
                mCacheSize.setText(getSizeStr(mAppEntry.cacheSize));
            }
            if (mLastTotalSize != mAppEntry.size) {
                mLastTotalSize = mAppEntry.size;
                mTotalSize.setText(getSizeStr(mAppEntry.size));
            }
            
            if (mAppEntry.dataSize <= 0 || !mCanClearData) {
                mClearDataButton.setEnabled(false);
            } else {
                mClearDataButton.setEnabled(true);
                mClearDataButton.setOnClickListener(this);
            }
            if (mAppEntry.cacheSize <= 0) {
                mClearCacheButton.setEnabled(false);
            } else {
                mClearCacheButton.setEnabled(true);
                mClearCacheButton.setOnClickListener(this);
            }
        }
    }
    
    /*
     * Private method to handle clear message notification from observer when
     * the async operation from PackageManager is complete
     */
    private void processClearMsg(Message msg) {
        int result = msg.arg1;
        String packageName = mAppEntry.info.packageName;
        mClearDataButton.setText(R.string.clear_user_data_text);
        if(result == OP_SUCCESSFUL) {
            Log.i(TAG, "Cleared user data for package : "+packageName);
            mState.requestSize(mAppEntry.info.packageName);
        } else {
            mClearDataButton.setEnabled(true);
        }
        checkForceStop();
    }

    private void refreshButtons() {
        if (!mMoveInProgress) {
            initUninstallButtons();
            initDataButtons();
            initMoveButton();
        } else {
            mMoveAppButton.setText(R.string.moving);
            mMoveAppButton.setEnabled(false);
            mUninstallButton.setEnabled(false);
        }
    }

    private void processMoveMsg(Message msg) {
        int result = msg.arg1;
        String packageName = mAppEntry.info.packageName;
        // Refresh the button attributes.
        mMoveInProgress = false;
        if (result == PackageManager.MOVE_SUCCEEDED) {
            Log.i(TAG, "Moved resources for " + packageName);
            // Refresh size information again.
            mState.requestSize(mAppEntry.info.packageName);
        } else {
            showDialogInner(DLG_MOVE_FAILED, result);
        }
        refreshUi();
    }

    /*
     * Private method to initiate clearing user data when the user clicks the clear data 
     * button for a system package
     */
    private  void initiateClearUserData() {
        mClearDataButton.setEnabled(false);
        // Invoke uninstall or clear user data based on sysPackage
        String packageName = mAppEntry.info.packageName;
        Log.i(TAG, "Clearing user data for package : " + packageName);
        if (mClearDataObserver == null) {
            mClearDataObserver = new ClearUserDataObserver();
        }
        ActivityManager am = (ActivityManager)
                getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        boolean res = am.clearApplicationUserData(packageName, mClearDataObserver);
        if (!res) {
            // Clearing data failed for some obscure reason. Just log error for now
            Log.i(TAG, "Couldnt clear application user data for package:"+packageName);
            showDialogInner(DLG_CANNOT_CLEAR_DATA, 0);
        } else {
            mClearDataButton.setText(R.string.recompute_size);
        }
    }
    
    private void showDialogInner(int id, int moveErrorCode) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(id, moveErrorCode);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }
    
    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(int id, int moveErrorCode) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            args.putInt("moveError", moveErrorCode);
            frag.setArguments(args);
            return frag;
        }

        InstalledAppDetails getOwner() {
            return (InstalledAppDetails)getTargetFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            int moveErrorCode = getArguments().getInt("moveError");
            switch (id) {
                case DLG_CLEAR_DATA:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getText(R.string.clear_data_dlg_title))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getActivity().getText(R.string.clear_data_dlg_text))
                    .setPositiveButton(R.string.dlg_ok,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Clear user data here
                            getOwner().initiateClearUserData();
                        }
                    })
                    .setNegativeButton(R.string.dlg_cancel, null)
                    .create();
                case DLG_FACTORY_RESET:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getText(R.string.app_factory_reset_dlg_title))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getActivity().getText(R.string.app_factory_reset_dlg_text))
                    .setPositiveButton(R.string.dlg_ok,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Clear user data here
                            getOwner().uninstallPkg(getOwner().mAppEntry.info.packageName);
                        }
                    })
                    .setNegativeButton(R.string.dlg_cancel, null)
                    .create();
                case DLG_APP_NOT_FOUND:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getText(R.string.app_not_found_dlg_title))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getActivity().getText(R.string.app_not_found_dlg_title))
                    .setNeutralButton(getActivity().getText(R.string.dlg_ok),
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //force to recompute changed value
                            getOwner().setIntentAndFinish(true, true);
                        }
                    })
                    .create();
                case DLG_CANNOT_CLEAR_DATA:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getText(R.string.clear_failed_dlg_title))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getActivity().getText(R.string.clear_failed_dlg_text))
                    .setNeutralButton(R.string.dlg_ok,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            getOwner().mClearDataButton.setEnabled(false);
                            //force to recompute changed value
                            getOwner().setIntentAndFinish(false, false);
                        }
                    })
                    .create();
                case DLG_FORCE_STOP:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getText(R.string.force_stop_dlg_title))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getActivity().getText(R.string.force_stop_dlg_text))
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Force stop
                            getOwner().forceStopPackage(getOwner().mAppEntry.info.packageName);
                        }
                    })
                    .setNegativeButton(R.string.dlg_cancel, null)
                    .create();
                case DLG_MOVE_FAILED:
                    CharSequence msg = getActivity().getString(R.string.move_app_failed_dlg_text,
                            getOwner().getMoveErrMsg(moveErrorCode));
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getText(R.string.move_app_failed_dlg_title))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(msg)
                    .setNeutralButton(R.string.dlg_ok, null)
                    .create();
                case DLG_DISABLE:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(getActivity().getText(R.string.app_disable_dlg_title))
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getActivity().getText(R.string.app_disable_dlg_text))
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Disable the app
                            new DisableChanger(getOwner(), getOwner().mAppEntry.info,
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
                            .execute((Object)null);
                        }
                    })
                    .setNegativeButton(R.string.dlg_cancel, null)
                    .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }
    }

    private void uninstallPkg(String packageName) {
         // Create new intent to launch Uninstaller activity
        Uri packageURI = Uri.parse("package:"+packageName);
        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
        startActivity(uninstallIntent);
        setIntentAndFinish(true, true);
    }

    private void forceStopPackage(String pkgName) {
        ActivityManager am = (ActivityManager)getActivity().getSystemService(
                Context.ACTIVITY_SERVICE);
        am.forceStopPackage(pkgName);
        mState.invalidatePackage(pkgName);
        ApplicationsState.AppEntry newEnt = mState.getEntry(pkgName);
        if (newEnt != null) {
            mAppEntry = newEnt;
        }
        checkForceStop();
    }

    private final BroadcastReceiver mCheckKillProcessesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateForceStopButton(getResultCode() != Activity.RESULT_CANCELED);
        }
    };

    private void updateForceStopButton(boolean enabled) {
        mForceStopButton.setEnabled(enabled);
        mForceStopButton.setOnClickListener(InstalledAppDetails.this);
    }
    
    private void checkForceStop() {
        if (mDpm.packageHasActiveAdmins(mPackageInfo.packageName)) {
            // User can't force stop device admin.
            updateForceStopButton(false);
        } else if ((mAppEntry.info.flags&ApplicationInfo.FLAG_STOPPED) == 0) {
            // If the app isn't explicitly stopped, then always show the
            // force stop button.
            updateForceStopButton(true);
        } else {
            Intent intent = new Intent(Intent.ACTION_QUERY_PACKAGE_RESTART,
                    Uri.fromParts("package", mAppEntry.info.packageName, null));
            intent.putExtra(Intent.EXTRA_PACKAGES, new String[] { mAppEntry.info.packageName });
            intent.putExtra(Intent.EXTRA_UID, mAppEntry.info.uid);
            getActivity().sendOrderedBroadcast(intent, null, mCheckKillProcessesReceiver, null,
                    Activity.RESULT_CANCELED, null, null);
        }
    }
    
    static class DisableChanger extends AsyncTask<Object, Object, Object> {
        final PackageManager mPm;
        final WeakReference<InstalledAppDetails> mActivity;
        final ApplicationInfo mInfo;
        final int mState;

        DisableChanger(InstalledAppDetails activity, ApplicationInfo info, int state) {
            mPm = activity.mPm;
            mActivity = new WeakReference<InstalledAppDetails>(activity);
            mInfo = info;
            mState = state;
        }

        @Override
        protected Object doInBackground(Object... params) {
            mPm.setApplicationEnabledSetting(mInfo.packageName, mState, 0);
            return null;
        }
    }

    /*
     * Method implementing functionality of buttons clicked
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    public void onClick(View v) {
        String packageName = mAppEntry.info.packageName;
        if(v == mUninstallButton) {
            if (mUpdatedSysApp) {
                showDialogInner(DLG_FACTORY_RESET, 0);
            } else {
                if ((mAppEntry.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    if (mAppEntry.info.enabled) {
                        showDialogInner(DLG_DISABLE, 0);
                    } else {
                        new DisableChanger(this, mAppEntry.info,
                                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                        .execute((Object)null);
                    }
                } else {
                    uninstallPkg(packageName);
                }
            }
        } else if(v == mActivitiesButton) {
            mPm.clearPackagePreferredActivities(packageName);
            try {
                mUsbManager.clearDefaults(packageName);
            } catch (RemoteException e) {
                Log.e(TAG, "mUsbManager.clearDefaults", e);
            }
            mActivitiesButton.setEnabled(false);
        } else if(v == mClearDataButton) {
            if (mAppEntry.info.manageSpaceActivityName != null) {
                Intent intent = new Intent(Intent.ACTION_DEFAULT);
                intent.setClassName(mAppEntry.info.packageName,
                        mAppEntry.info.manageSpaceActivityName);
                startActivityForResult(intent, -1);
            } else {
                showDialogInner(DLG_CLEAR_DATA, 0);
            }
        } else if (v == mClearCacheButton) {
            // Lazy initialization of observer
            if (mClearCacheObserver == null) {
                mClearCacheObserver = new ClearCacheObserver();
            }
            mPm.deleteApplicationCacheFiles(packageName, mClearCacheObserver);
        } else if (v == mForceStopButton) {
            showDialogInner(DLG_FORCE_STOP, 0);
            //forceStopPackage(mAppInfo.packageName);
        } else if (v == mMoveAppButton) {
            if (mPackageMoveObserver == null) {
                mPackageMoveObserver = new PackageMoveObserver();
            }
            int moveFlags = (mAppEntry.info.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0 ?
                    PackageManager.MOVE_INTERNAL : PackageManager.MOVE_EXTERNAL_MEDIA;
            mMoveInProgress = true;
            refreshButtons();
            mPm.movePackage(mAppEntry.info.packageName, mPackageMoveObserver, moveFlags);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        String packageName = mAppEntry.info.packageName;
        ActivityManager am = (ActivityManager)
                getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        if (buttonView == mAskCompatibilityCB) {
            am.setPackageAskScreenCompat(packageName, isChecked);
        } else if (buttonView == mEnableCompatibilityCB) {
            am.setPackageScreenCompatMode(packageName, isChecked ?
                    ActivityManager.COMPAT_MODE_ENABLED : ActivityManager.COMPAT_MODE_DISABLED);
        }
    }
}

