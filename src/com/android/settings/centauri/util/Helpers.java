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

package com.android.settings.centauri.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import com.android.settings.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.util.Properties;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

public class Helpers implements Constants {

    private static final String TAG = "Helpers";

    private static String mVoltagePath;

    /**
     * Return mount points
     *
     * @param path
     * @return line if present
     */
    public static String[] getMounts(final String path) {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"), 256);
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.contains(path)) {
                    return line.split(" ");
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "/proc/mounts does not exist");
        } catch (IOException e) {
            Log.d(TAG, "Error reading /proc/mounts");
        }
        return null;
    }

    /**
     * Get mounts
     *
     * @param mount
     * @return success or failure
     */
    public static boolean getMount(final String mount) {
        final CMDProcessor cmd = new CMDProcessor();
        final String mounts[] = getMounts("/system");
        if (mounts != null && mounts.length >= 3) {
            final String device = mounts[0];
            final String path = mounts[1];
            final String point = mounts[2];
            if (cmd.su.runWaitFor("mount -o " + mount + ",remount -t " + point + " " + device + " " + path).success()) {
                return true;
            }
        }
        return (cmd.su.runWaitFor("busybox mount -o remount," + mount + " /system").success());
    }

    /**
     * Read one line from file
     *
     * @param fname
     * @return line
     */
    public static String readOneLine(String fname) {
        String line = null;
        if (new File(fname).exists()) {
            BufferedReader br;
            try {
                br = new BufferedReader(new FileReader(fname), 512);
                try {
                    line = br.readLine();
                } finally {
                    br.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "IO Exception when reading sys file", e);
                // attempt to do magic!
                return readFileViaShell(fname, true);
            }
        }
        return line;
    }

    public static boolean fileExists(String fname) {
        return new File(fname).exists();
    }

    /**
     * Read file via shell
     *
     * @param filePath
     * @param useSu
     * @return file output
     */
    public static String readFileViaShell(String filePath, boolean useSu) {
        CMDProcessor.CommandResult cr = null;
        if (useSu) {
            cr = new CMDProcessor().su.runWaitFor("cat " + filePath);
        } else {
            cr = new CMDProcessor().sh.runWaitFor("cat " + filePath);
        }
        if (cr.success())
            return cr.stdout;
        return null;
    }

    /**
     * Write one line to a file
     *
     * @param fname
     * @param value
     * @return if line was written
     */
    public static boolean writeOneLine(String fname, String value) {
        if (!new File(fname).exists()) {
            return false;
        }
        try {
            FileWriter fw = new FileWriter(fname);
            try {
                fw.write(value);
            } finally {
                fw.close();
            }
        } catch (IOException e) {
            String Error = "Error writing to " + fname + ". Exception: ";
            Log.e(TAG, Error, e);
            return false;
        }
        return true;
    }

    /**
     * Gets available schedulers from file
     *
     * @return available schedulers
     */
    public static String[] getAvailableIOSchedulers() {
        String[] schedulers = null;
        String[] aux = readStringArray(IO_SCHEDULER_PATH[0]);
        if (aux != null) {
            schedulers = new String[aux.length];
            for (int i = 0; i < aux.length; i++) {
                if (aux[i].charAt(0) == '[') {
                    schedulers[i] = aux[i].substring(1, aux[i].length() - 1);
                } else {
                    schedulers[i] = aux[i];
                }
            }
        }
        return schedulers;
    }

    /**
     * Reads string array from file
     *
     * @param fname
     * @return string array
     */
    private static String[] readStringArray(String fname) {
        String line = readOneLine(fname);
        if (line != null) {
            return line.split(" ");
        }
        return null;
    }

    /**
     * Get current IO Scheduler
     *
     * @return current io scheduler
     */
    public static String getIOScheduler() {
        String scheduler = null;
        String[] schedulers = readStringArray(IO_SCHEDULER_PATH[0]);
        if (schedulers != null) {
            for (String s : schedulers) {
                if (s.charAt(0) == '[') {
                    scheduler = s.substring(1, s.length() - 1);
                    break;
                }
            }
        }
        return scheduler;
    }

    /*
         * @return available performance scheduler
         */
    public static Boolean GovernorExist(String gov) {
        return readOneLine(GOVERNORS_LIST_PATH).contains(gov);
    }

    /**
     * Get total number of cpus
     *
     * @return total number of cpus
     */
    public static int getNumOfCpus() {
        int numOfCpu = 1;
        String numOfCpus = Helpers.readOneLine(NUM_OF_CPUS_PATH);
        String[] cpuCount = numOfCpus.split("-");
        if (cpuCount.length > 1) {
            try {
                int cpuStart = Integer.parseInt(cpuCount[0]);
                int cpuEnd = Integer.parseInt(cpuCount[1]);

                numOfCpu = cpuEnd - cpuStart + 1;

                if (numOfCpu < 0)
                    numOfCpu = 1;
            } catch (NumberFormatException ex) {
                numOfCpu = 1;
            }
        }
        return numOfCpu;
    }

    /**
     * Check if any voltage control tables exist and set the voltage path if a
     * file is found.
     * <p/>
     * If false is returned, there was no tables found and none will be used.
     *
     * @return true/false if uv table exists
     */
    public static boolean voltageFileExists() {
        if (new File(UV_MV_PATH).exists()) {
            setVoltagePath(UV_MV_PATH);
            return true;
        } else if (new File(VDD_PATH).exists()) {
            setVoltagePath(VDD_PATH);
            return true;
        } else if (new File(VDD_SYSFS_PATH).exists()) {
            setVoltagePath(VDD_SYSFS_PATH);
            return true;
        } else if (new File(COMMON_VDD_PATH).exists()) {
            setVoltagePath(COMMON_VDD_PATH);
            return true;
        }
        return false;
    }

    /**
     * Sets the voltage file to be used by the rest of the app elsewhere.
     *
     * @param voltageFile
     */
    public static void setVoltagePath(String voltageFile) {
        Log.d(TAG, "UV table path detected: " + voltageFile);
        mVoltagePath = voltageFile;
    }

    /**
     * Gets the currently set voltage path
     *
     * @return voltage path
     */
    public static String getVoltagePath() {
        return mVoltagePath;
    }

    /**
     * Convert to MHz and append a tag
     *
     * @param mhzString
     * @return tagged and converted String
     */
    public static String toMHz(String mhzString) {
        return String.valueOf(Integer.parseInt(mhzString) / 1000) + " MHz";
    }

    /**
     * Restart the activity smoothly
     *
     * @param activity
     */
    public static void restartPC(final Activity activity) {
        if (activity == null)
            return;
        final int enter_anim = android.R.anim.fade_in;
        final int exit_anim = android.R.anim.fade_out;
        activity.overridePendingTransition(enter_anim, exit_anim);
        activity.finish();
        activity.overridePendingTransition(enter_anim, exit_anim);
        activity.startActivity(activity.getIntent());
    }

    /**
     * Helper to update the app widget
     *
     * @param context
     * @note OMNI: The widget has been disabled
     */
    public static void updateAppWidget(Context context) {
/*
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, PCWidget.class);
        int[] widgetIds = widgetManager.getAppWidgetIds(widgetComponent);
        Intent update = new Intent();
        update.setAction("com.brewcrewfoo.performance.ACTION_FREQS_CHANGED");
        update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
        context.sendBroadcast(update);
*/
    }

    /**
     * Helper to create a bitmap to set as imageview or bg
     *
     * @param bgcolor
     * @return bitmap
     */
    public static Bitmap getBackground(int bgcolor) {
        try {
            Bitmap.Config config = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = Bitmap.createBitmap(2, 2, config);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(bgcolor);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public static String binExist(String b) {
        CMDProcessor.CommandResult cr = null;
        cr = new CMDProcessor().sh.runWaitFor("busybox which " + b);
        if (cr.success()) {
            return cr.stdout;
        } else {
            return NOT_FOUND;
        }
    }

    public static String getCachePartition() {
        CMDProcessor.CommandResult cr = null;
        cr = new CMDProcessor().sh.runWaitFor("busybox echo `busybox mount | busybox grep cache | busybox cut -d' ' -f1`");
        if (cr.success() && !cr.stdout.equals("")) {
            return cr.stdout;
        } else {
            return NOT_FOUND;
        }
    }

    public static long getTotMem() {
        long v = 0;
        CMDProcessor.CommandResult cr = new CMDProcessor().sh.runWaitFor("busybox echo `busybox grep MemTot /proc/meminfo | busybox grep -E --only-matching '[[:digit:]]+'`");
        if (cr.success()) {
            try {
                v = (long) Integer.parseInt(cr.stdout) * 1024;
            } catch (NumberFormatException e) {
                Log.d(TAG, "MemTot conversion err: " + e);
            }
        }
        return v;
    }

    public static boolean showBattery() {
        return (new File(BLX_PATH).exists() || (fastcharge_path() != null) || new File(BAT_VOLT_PATH).exists());
    }

    public static String shExec(StringBuilder s, Context c, Boolean su) {
        get_assetsScript("run", c, s.toString(), "");
        if (isSystemApp(c)) {
            new CMDProcessor().sh.runWaitFor("busybox chmod 750 " + c.getFilesDir() + "/run");
        } else {
            new CMDProcessor().su.runWaitFor("busybox chmod 750 " + c.getFilesDir() + "/run");
        }
        CMDProcessor.CommandResult cr = null;
        if (su && !isSystemApp(c))
            cr = new CMDProcessor().su.runWaitFor(c.getFilesDir() + "/run");
        else
            cr = new CMDProcessor().sh.runWaitFor(c.getFilesDir() + "/run");
        if (cr.success()) {
            return cr.stdout;
        } else {
            Log.d(TAG, "execute: " + cr.stderr);
            return null;
        }
    }

    public static void get_assetsScript(String fn, Context c, String prefix, String postfix) {
        byte[] buffer;
        final AssetManager assetManager = c.getAssets();
        try {
            InputStream f = assetManager.open(fn);
            buffer = new byte[f.available()];
            f.read(buffer);
            f.close();
            final String s = new String(buffer);
            final StringBuilder sb = new StringBuilder(s);
            if (!postfix.equals("")) {
                sb.append("\n\n").append(postfix);
            }
            if (!prefix.equals("")) {
                sb.insert(0, prefix + "\n");
            }
            sb.insert(0, "#!" + Helpers.binExist("sh") + "\n\n");
            try {
                FileOutputStream fos;
                fos = c.openFileOutput(fn, Context.MODE_PRIVATE);
                fos.write(sb.toString().getBytes());
                fos.close();

            } catch (IOException e) {
                Log.d(TAG, "error write " + fn + " file");
                e.printStackTrace();
            }

        } catch (IOException e) {
            Log.d(TAG, "error read " + fn + " file");
            e.printStackTrace();
        }
    }

    public static void get_assetsBinary(String fn, Context c) {
        byte[] buffer;
        final AssetManager assetManager = c.getAssets();
        try {
            InputStream f = assetManager.open(fn);
            buffer = new byte[f.available()];
            f.read(buffer);
            f.close();
            try {
                FileOutputStream fos;
                fos = c.openFileOutput(fn, Context.MODE_PRIVATE);
                fos.write(buffer);
                fos.close();

            } catch (IOException e) {
                Log.d(TAG, "error write " + fn + " file");
                e.printStackTrace();
            }

        } catch (IOException e) {
            Log.d(TAG, "error read " + fn + " file");
            e.printStackTrace();
        }
    }

    public static String ReadableByteCount(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = String.valueOf("KMGTPE".charAt(exp - 1));
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public static void removeCurItem(MenuItem item, int idx, ViewPager vp) {
        for (int i = 0; i < vp.getAdapter().getCount(); i++) {
            if (item.getItemId() == idx + i + 1) {
                vp.setCurrentItem(i);
            }
        }
    }

    public static void addItems2Menu(Menu menu, int idx, String nume, ViewPager vp) {
        final SubMenu smenu = menu.addSubMenu(0, idx, 0, nume);
        for (int i = 0; i < vp.getAdapter().getCount(); i++) {
            if (i != vp.getCurrentItem())
                smenu.add(0, idx + i + 1, 0, vp.getAdapter().getPageTitle(i));
        }
    }

    public static String bln_path() {
        if (new File("/sys/class/misc/backlightnotification/enabled").exists()) {
            return "/sys/class/misc/backlightnotification/enabled";
        } else if (new File("/sys/class/leds/button-backlight/blink_buttons").exists()) {
            return "/sys/class/leds/button-backlight/blink_buttons";
        } else {
            return null;
        }
    }

    public static String fastcharge_path() {
        if (new File("/sys/kernel/fast_charge/force_fast_charge").exists()) {
            return "/sys/kernel/fast_charge/force_fast_charge";
        } else if (new File("/sys/module/msm_otg/parameters/fast_charge").exists()) {
            return "/sys/module/msm_otg/parameters/fast_charge";
        } else if (new File("/sys/devices/platform/htc_battery/fast_charge").exists()) {
            return "/sys/devices/platform/htc_battery/fast_charge";
        } else {
            return null;
        }
    }

    public static String fsync_path() {
        if (new File("/sys/class/misc/fsynccontrol/fsync_enabled").exists()) {
            return "/sys/class/misc/fsynccontrol/fsync_enabled";
        } else if (new File("/sys/module/sync/parameters/fsync_enabled").exists()) {
            return "/sys/module/sync/parameters/fsync_enabled";
        } else {
            return null;
        }
    }

    public static boolean isSystemApp(Context c) {
        boolean mIsSystemApp;
        return mIsSystemApp = c.getResources().getBoolean(R.bool.config_isSystemApp);
    }

    /**
     * Checks device for SuperUser permission
     *
     * @return If SU was granted or denied
     */
    public static boolean checkSu() {
        if (!new File("/system/bin/su").exists()
                && !new File("/system/xbin/su").exists()) {
            Log.e(TAG, "su does not exist!!!");
            return false; // tell caller to bail...
        }

        try {
            if ((new CMDProcessor().su
                    .runWaitFor("ls /data/app-private")).success()) {
                Log.i(TAG, " SU exists and we have permission");
                return true;
            } else {
                Log.i(TAG, " SU exists but we dont have permission");
                return false;
            }
        } catch (final NullPointerException e) {
            Log.e(TAG, e.getLocalizedMessage().toString());
            return false;
        }
    }

    /**
     * Checks to see if Busybox is installed in "/system/"
     *
     * @return If busybox exists
     */
    public static boolean checkBusybox() {
        if (!new File("/system/bin/busybox").exists()
                && !new File("/system/xbin/busybox").exists()) {
            Log.e(TAG, "Busybox not in xbin or bin!");
            return false;
        }

        try {
            if (!new CMDProcessor().su.runWaitFor("busybox mount").success()) {
                Log.e(TAG, " Busybox is there but it is borked! ");
                return false;
            }
        } catch (final NullPointerException e) {
            Log.e(TAG, e.getLocalizedMessage().toString());
            return false;
        }
        return true;
    }

    public static String getFile(final String filename) {
        String s = "";
        final File f = new File(filename);

        if (f.exists() && f.canRead()) {
            try {
                final BufferedReader br = new BufferedReader(new FileReader(f),
                        256);
                String buffer = null;
                while ((buffer = br.readLine()) != null) {
                    s += buffer + "\n";
                }

                br.close();
            } catch (final Exception e) {
                Log.e(TAG, "Error reading file: " + filename, e);
                s = null;
            }
        }
        return s;
    }

    public static void writeNewFile(String filePath, String fileContents) {
        File f = new File(filePath);
        if (f.exists()) {
            f.delete();
        }

        try{
            // Create file
            FileWriter fstream = new FileWriter(f);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(fileContents);
            //Close the output stream
            out.close();
        }catch (Exception e){
            Log.d( TAG, "Failed to create " + filePath + " File contents: " + fileContents);
        }
    }

    /**
     * Long toast message
     *
     * @param c Application Context
     * @param msg Message to send
     */
    public static void msgLong(final Context c, final String msg) {
        if (c != null && msg != null) {
            Toast.makeText(c, msg.trim(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Short toast message
     *
     * @param c Application Context
     * @param msg Message to send
     */
    public static void msgShort(final Context c, final String msg) {
        if (c != null && msg != null) {
            Toast.makeText(c, msg.trim(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Long toast message
     *
     * @param c Application Context
     * @param msg Message to send
     */
    public static void sendMsg(final Context c, final String msg) {
        if (c != null && msg != null) {
            msgLong(c, msg);
        }
    }

    public static boolean isPackageInstalled(final String packageName,
            final PackageManager pm)
    {
        String mVersion;
        try {
            mVersion = pm.getPackageInfo(packageName, 0).versionName;
            if (mVersion.equals(null)) {
                return false;
            }
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    public static void restartSystemUI() {
        new CMDProcessor().su.run("pkill -TERM -f com.android.systemui");
    }

    /*
     * Find value of build.prop item (/system can be ro or rw)
     *
     * @param prop /system/build.prop property name to find value of
     *
     * @returns String value of @param:prop
     */
    public static String findBuildPropValueOf(String prop) {
        String mBuildPath = "/system/build.prop";
        String DISABLE = "disable";
        String value = null;
        try {
            //create properties construct and load build.prop
            Properties mProps = new Properties();
            mProps.load(new FileInputStream(mBuildPath));
            //get the property
            value = mProps.getProperty(prop, DISABLE);
            Log.d(TAG, String.format("Helpers:findBuildPropValueOf found {%s} with the value (%s)", prop, value));
        } catch (IOException ioe) {
            Log.d(TAG, "failed to load input stream");
        } catch (NullPointerException npe) {
            //swallowed thrown by ill formatted requests
        }

        if (value != null) {
            return value;
        } else {
            return DISABLE;
        }
    }
}
