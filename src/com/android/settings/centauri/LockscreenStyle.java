/*
 * Copyright (C) 2014 CentauriROM
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

package com.android.settings.centauri;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SeekBarPreference;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.internal.util.centauri.DeviceUtils;
import com.android.internal.policy.IKeyguardService;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.SettingsPreferenceFragment;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class LockscreenStyle extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {

    private static final String TAG = "LockscreenStyle";

    private static final String KEY_LOCKSCREEN_COLORIZE_ICON =
            "lockscreen_colorize_icon";
    private static final String KEY_LOCKSCREEN_LOCK_ICON =
            "lockscreen_lock_icon";
    private static final String KEY_LOCKSCREEN_FRAME_COLOR =
            "lockscreen_frame_color";
    private static final String KEY_LOCKSCREEN_LOCK_COLOR =
            "lockscreen_lock_color";
    private static final String KEY_LOCKSCREEN_DOTS_COLOR =
            "lockscreen_dots_color";
    private static final String KEY_LOCKSCREEN_TARGETS_COLOR =
            "lockscreen_targets_color";
    private static final String KEY_LOCKSCREEN_MISC_COLOR =
            "lockscreen_misc_color";
    private static final String KEY_LOCKSCREEN_WALLPAPER =
            "lockscreen_wallpaper";
    private static final String KEY_SELECT_LOCKSCREEN_WALLPAPER =
            "select_lockscreen_wallpaper";


    private static final String KEY_SEE_THROUGH = "see_through";
    private static final String KEY_BLUR_RADIUS = "blur_radius";

    private String mDefault;

    private CheckBoxPreference mLockscreenWallpaper;
    private Preference mSelectLockscreenWallpaper;
    private CheckBoxPreference mSeeThrough;
    private SeekBarPreference mBlurRadius;

    private CheckBoxPreference mColorizeCustom;
    private ColorPickerPreference mFrameColor;
    private ColorPickerPreference mLockColor;
    private ColorPickerPreference mDotsColor;
    private ColorPickerPreference mTargetsColor;
    private ColorPickerPreference mMiscColor;

    private ListPreference mLockIcon;
    private boolean mCheckPreferences;

    private File mLockImage;
    private File mWallpaperTemporary;
    private IKeyguardService mKeyguardService;

    private static final int MENU_RESET = Menu.FIRST;

    private static final int DLG_RESET = 0;
    private static final int REQUEST_PICK_LOCK_ICON = 100;
    private static final int REQUEST_CODE_BG_WALLPAPER = 1024;

    private final ServiceConnection mKeyguardConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mKeyguardService = IKeyguardService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mKeyguardService = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createCustomView();
    }

    private PreferenceScreen createCustomView() {
        mCheckPreferences = false;
        PreferenceScreen prefSet = getPreferenceScreen();
        if (prefSet != null) {
            prefSet.removeAll();
        }

        addPreferencesFromResource(R.xml.lockscreen_style);
        prefSet = getPreferenceScreen();

        // Set to string so we don't have to create multiple objects of it
        mDefault = getResources().getString(R.string.default_string);

        mLockImage = new File(getActivity().getFilesDir() + "/lock_icon.tmp");

        mLockIcon = (ListPreference)
                findPreference(KEY_LOCKSCREEN_LOCK_ICON);
        mLockIcon.setOnPreferenceChangeListener(this);

        mColorizeCustom = (CheckBoxPreference)
                findPreference(KEY_LOCKSCREEN_COLORIZE_ICON);
        mColorizeCustom.setChecked(Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.LOCKSCREEN_COLORIZE_LOCK, 0) == 1);
        mColorizeCustom.setOnPreferenceChangeListener(this);

        mFrameColor = (ColorPickerPreference)
                findPreference(KEY_LOCKSCREEN_FRAME_COLOR);
        mFrameColor.setOnPreferenceChangeListener(this);
        int frameColor = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_FRAME_COLOR, -2);
        setPreferenceSummary(mFrameColor,
                getResources().getString(
                R.string.lockscreen_frame_color_summary), frameColor);
        mFrameColor.setNewPreviewColor(frameColor);

        mLockColor = (ColorPickerPreference)
                findPreference(KEY_LOCKSCREEN_LOCK_COLOR);
        mLockColor.setOnPreferenceChangeListener(this);
        int lockColor = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_LOCK_COLOR, -2);
        setPreferenceSummary(mLockColor,
                getResources().getString(
                R.string.lockscreen_lock_color_summary), lockColor);
        mLockColor.setNewPreviewColor(lockColor);

        mDotsColor = (ColorPickerPreference)
                findPreference(KEY_LOCKSCREEN_DOTS_COLOR);
        mDotsColor.setOnPreferenceChangeListener(this);
        int dotsColor = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_DOTS_COLOR, -2);
        setPreferenceSummary(mDotsColor,
                getResources().getString(
                R.string.lockscreen_dots_color_summary), dotsColor);
        mDotsColor.setNewPreviewColor(dotsColor);

        mTargetsColor = (ColorPickerPreference)
                findPreference(KEY_LOCKSCREEN_TARGETS_COLOR);
        mTargetsColor.setOnPreferenceChangeListener(this);
        int targetColor = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_TARGETS_COLOR, -2);
        setPreferenceSummary(mTargetsColor,
                getResources().getString(
                R.string.lockscreen_targets_color_summary), targetColor);
        mTargetsColor.setNewPreviewColor(targetColor);

        mMiscColor = (ColorPickerPreference)
                findPreference(KEY_LOCKSCREEN_MISC_COLOR);
        mMiscColor.setOnPreferenceChangeListener(this);
        int miscColor = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_MISC_COLOR, -2);
        setPreferenceSummary(mMiscColor,
                getResources().getString(
                R.string.lockscreen_misc_color_summary), miscColor);
        mMiscColor.setNewPreviewColor(miscColor);

        Intent intent = new Intent();
        intent.setClassName("com.android.keyguard", "com.android.keyguard.KeyguardService");
        if (!mContext.bindServiceAsUser(intent, mKeyguardConnection,
                Context.BIND_AUTO_CREATE, UserHandle.OWNER)) {
            Log.e(TAG, "*** Keyguard: can't bind to keyguard");
        }

        mLockscreenWallpaper = (CheckBoxPreference) findPreference(KEY_LOCKSCREEN_WALLPAPER);
        mLockscreenWallpaper.setChecked(Settings.System.getInt(getContentResolver(), Settings.System.LOCKSCREEN_WALLPAPER, 0) == 1);

        mSelectLockscreenWallpaper = findPreference(KEY_SELECT_LOCKSCREEN_WALLPAPER);
        mSelectLockscreenWallpaper.setEnabled(mLockscreenWallpaper.isChecked());
        mWallpaperTemporary = new File(getActivity().getCacheDir() + "/lockwallpaper.tmp");

        // lock screen see through
        mSeeThrough = (CheckBoxPreference) findPreference(KEY_SEE_THROUGH);
        if (mSeeThrough != null) {
            mSeeThrough.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_SEE_THROUGH, 0) == 1);
        }

        // lock screen blur radius
        mBlurRadius = (SeekBarPreference) findPreference(KEY_BLUR_RADIUS);
        mBlurRadius.setProgress(Settings.System.getInt(getContentResolver(),
                Settings.System.LOCKSCREEN_BLUR_RADIUS, 12));
        mBlurRadius.setOnPreferenceChangeListener(this);
        mBlurRadius.setEnabled(mSeeThrough.isChecked() && mSeeThrough.isEnabled());

        // No lock-slider is available
        boolean dotsDisabled = new LockPatternUtils(getActivity()).isSecure()
            && Settings.Secure.getInt(getContentResolver(),
            Settings.Secure.LOCK_BEFORE_UNLOCK, 0) == 0
            && Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.LOCK_SHAKE_TEMP_SECURE, 0) == 0;
        boolean imageExists = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.LOCKSCREEN_LOCK_ICON) != null;
        mDotsColor.setEnabled(!dotsDisabled);
        mLockIcon.setEnabled(!dotsDisabled);
        mColorizeCustom.setEnabled(!dotsDisabled && imageExists);
        // Tablets don't have the extended-widget lock icon
        if (DeviceUtils.isTablet(getActivity())) {
            mLockColor.setEnabled(!dotsDisabled);
        }

        updateLockSummary();

        setHasOptionsMenu(true);
        mCheckPreferences = true;
        return prefSet;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_CODE_BG_WALLPAPER) {
            if (resultCode == Activity.RESULT_OK) {
                if (mWallpaperTemporary.length() == 0 || !mWallpaperTemporary.exists()) {
                    Toast.makeText(getActivity(),
                            getResources().getString(R.string.shortcut_image_not_valid),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Bitmap bmp = BitmapFactory.decodeFile(mWallpaperTemporary.getAbsolutePath());
                try {
                    mKeyguardService.setWallpaper(bmp);
                    Settings.System.putInt(getContentResolver(), Settings.System.LOCKSCREEN_SEE_THROUGH, 0);
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to set wallpaper: " + ex);
                }
            }
        }
        if (mWallpaperTemporary.exists()) mWallpaperTemporary.delete();

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_PICK_LOCK_ICON) {

                if (mLockImage.length() == 0 || !mLockImage.exists()) {
                    Toast.makeText(getActivity(),
                            getResources().getString(R.string.shortcut_image_not_valid),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                File image = new File(getActivity().getFilesDir() + File.separator
                        + "lock_icon" + System.currentTimeMillis() + ".png");
                String path = image.getAbsolutePath();
                mLockImage.renameTo(image);
                image.setReadable(true, false);

                deleteLockIcon(); // Delete current icon if it exists before saving new.
                Settings.Secure.putString(getContentResolver(),
                        Settings.Secure.LOCKSCREEN_LOCK_ICON, path);

                mColorizeCustom.setEnabled(path != null);
            }
        } else {
            if (mLockImage.exists()) {
                mLockImage.delete();
            }
        }
        updateLockSummary();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
                .setIcon(R.drawable.ic_settings_backup) // use the backup icon
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                showDialogInner(DLG_RESET);
                return true;
             default:
                return super.onContextItemSelected(item);
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mLockIcon) {
            int indexOf = mLockIcon.findIndexOfValue(newValue.toString());
            if (indexOf == 0) {
                requestLockImage();
            } else if (indexOf == 2) {
                deleteLockIcon();
            } else {
                resizeCentauriLock();
            }
            return true;
        } else if (preference == mColorizeCustom) {
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_COLORIZE_LOCK,
                    (Boolean) newValue ? 1 : 0);
            return true;
        } else if (preference == mFrameColor) {
            int val = Integer.valueOf(String.valueOf(newValue));
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_FRAME_COLOR, val);
            setPreferenceSummary(preference,
                    getResources().getString(R.string.lockscreen_frame_color_summary), val);
            return true;
        } else if (preference == mLockColor) {
            int val = Integer.valueOf(String.valueOf(newValue));
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_LOCK_COLOR, val);
            setPreferenceSummary(preference,
                    getResources().getString(R.string.lockscreen_lock_color_summary), val);
            return true;
        } else if (preference == mDotsColor) {
            int val = Integer.valueOf(String.valueOf(newValue));
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_DOTS_COLOR, val);
            setPreferenceSummary(preference,
                    getResources().getString(R.string.lockscreen_dots_color_summary), val);
            return true;
        } else if (preference == mBlurRadius) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_BLUR_RADIUS,
                    (Integer) newValue);
            return true;
        } else if (preference == mTargetsColor) {
            int val = Integer.valueOf(String.valueOf(newValue));
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_TARGETS_COLOR, val);
            setPreferenceSummary(preference,
                    getResources().getString(R.string.lockscreen_targets_color_summary), val);
            return true;
        } else if (preference == mMiscColor) {
            int val = Integer.valueOf(String.valueOf(newValue));
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_MISC_COLOR, val);
            setPreferenceSummary(preference,
                    getResources().getString(R.string.lockscreen_misc_color_summary), val);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mSeeThrough) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCKSCREEN_SEE_THROUGH,
                    mSeeThrough.isChecked() ? 1 : 0);
            mBlurRadius.setEnabled(mSeeThrough.isChecked() && mSeeThrough.isEnabled());
        } else if (preference == mLockscreenWallpaper) {
            if (!mLockscreenWallpaper.isChecked()) setWallpaper(null);
            else Settings.System.putInt(getContentResolver(), Settings.System.LOCKSCREEN_WALLPAPER, 1);
            mSelectLockscreenWallpaper.setEnabled(mLockscreenWallpaper.isChecked());
        } else if (preference == mSelectLockscreenWallpaper) {
            final Intent intent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("scale", true);
            intent.putExtra("scaleUpIfNeeded", false);
            intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());

            final Display display = getActivity().getWindowManager().getDefaultDisplay();

            boolean isPortrait = getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_PORTRAIT;

            Point size = new Point();
            display.getSize(size);

            intent.putExtra("aspectX", isPortrait ? size.x : size.y);
            intent.putExtra("aspectY", isPortrait ? size.y : size.x);

            try {
                mWallpaperTemporary.createNewFile();
                mWallpaperTemporary.setWritable(true, false);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mWallpaperTemporary));
                getActivity().startActivityFromFragment(this, intent, REQUEST_CODE_BG_WALLPAPER);
            } catch (IOException e) {
                // Do nothing here
            } catch (ActivityNotFoundException e) {
                // Do nothing here
            }
        } else {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
        return true;
    }

    private void setPreferenceSummary(
            Preference preference, String defaultSummary, int value) {
        if (value == -2) {
            preference.setSummary(defaultSummary + " (" + mDefault + ")");
        } else {
            String hexColor = String.format("#%08x", (0xffffffff & value));
            preference.setSummary(defaultSummary + " (" + hexColor + ")");
        }
    }

    private void updateLockSummary() {
        int resId;
        String value = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.LOCKSCREEN_LOCK_ICON);
        if (value == null) {
            resId = R.string.lockscreen_lock_icon_default;
            mLockIcon.setValueIndex(2);
        } else if (value.contains("centauri_lock")) {
            resId = R.string.lockscreen_lock_icon_centauri;
            mLockIcon.setValueIndex(1);
        } else {
            resId = R.string.lockscreen_lock_icon_custom;
            mLockIcon.setValueIndex(0);
        }
        mLockIcon.setSummary(getResources().getString(resId));
    }

    private void requestLockImage() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        int px = requestImageSize();

        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", px);
        intent.putExtra("aspectY", px);
        intent.putExtra("outputX", px);
        intent.putExtra("outputY", px);
        intent.putExtra("scale", true);
        intent.putExtra("scaleUpIfNeeded", false);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());

        try {
            mLockImage.createNewFile();
            mLockImage.setWritable(true, false);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mLockImage));
            startActivityForResult(intent, REQUEST_PICK_LOCK_ICON);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private void deleteLockIcon() {
        String path = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.LOCKSCREEN_LOCK_ICON);

        if (path != null) {
            File f = new File(path);
            if (f != null && f.exists()) {
                f.delete();
            }
        }

        Settings.Secure.putString(getContentResolver(),
                Settings.Secure.LOCKSCREEN_LOCK_ICON, null);

        mColorizeCustom.setEnabled(false);
        updateLockSummary();
    }

    private void resizeCentauriLock() {
        Bitmap CentauriLock = BitmapFactory.decodeResource(getResources(), R.drawable.centauri_lock);
        if (CentauriLock != null) {
            String path = null;
            int px = requestImageSize();
            CentauriLock = Bitmap.createScaledBitmap(CentauriLock, px, px, true);
            try {
                mLockImage.createNewFile();
                mLockImage.setWritable(true, false);
                File image = new File(getActivity().getFilesDir() + File.separator
                            + "centauri_lock" + System.currentTimeMillis() + ".png");
                path = image.getAbsolutePath();
                mLockImage.renameTo(image);
                FileOutputStream outPut = new FileOutputStream(image);
                CentauriLock.compress(Bitmap.CompressFormat.PNG, 100, outPut);
                image.setReadable(true, false);
                outPut.flush();
                outPut.close();
            } catch (Exception e) {
                // Uh-oh Nothing we can do here.
                Log.e(TAG, e.getMessage(), e);
                return;
            }

            deleteLockIcon(); // Delete current icon if it exists before saving new.
            Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.LOCKSCREEN_LOCK_ICON, path);
            mColorizeCustom.setEnabled(path != null);
            updateLockSummary();
        }
    }

    private int requestImageSize() {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 68, getResources().getDisplayMetrics());
    }

    private void setWallpaper(Bitmap bmp) {
        try {
            mKeyguardService.setWallpaper(bmp);
        } catch (RemoteException ex) {
            Log.e(TAG, "Unable to set wallpaper!");
        }
    }

    private void showDialogInner(int id) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(id);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }

    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(int id) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            frag.setArguments(args);
            return frag;
        }

        LockscreenStyle getOwner() {
            return (LockscreenStyle) getTargetFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            switch (id) {
                case DLG_RESET:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reset)
                    .setMessage(R.string.lockscreen_style_reset_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Settings.Secure.putInt(getActivity().getContentResolver(),
                                    Settings.Secure.LOCKSCREEN_FRAME_COLOR, -2);
                            Settings.Secure.putInt(getActivity().getContentResolver(),
                                    Settings.Secure.LOCKSCREEN_LOCK_COLOR, -2);
                            Settings.Secure.putInt(getActivity().getContentResolver(),
                                    Settings.Secure.LOCKSCREEN_DOTS_COLOR, -2);
                            Settings.Secure.putInt(getActivity().getContentResolver(),
                                    Settings.Secure.LOCKSCREEN_TARGETS_COLOR, -2);
                            Settings.Secure.putInt(getActivity().getContentResolver(),
                                    Settings.Secure.LOCKSCREEN_MISC_COLOR, -2);
                            getOwner().createCustomView();
                        }
                    })
                    .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }

        @Override
        public void onCancel(DialogInterface dialog) {

        }
    }
}
