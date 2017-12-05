/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera.Log;

import com.mediatek.camera.setting.preference.IconListPreference;
import com.mediatek.camera.setting.preference.ListPreference;

//add by liyafeng for KEYWLVBS-166 20170110 (start)
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera.Parameters;
import android.os.BatteryManager;

import com.android.camera.R;
import com.mediatek.camera.setting.SettingConstants;
//add by liyafeng for KEYWLVBS-166 20170110 (end)

/**
 * A view for switching the front/back camera.
 */
public class PickerButton extends RotateImageView implements View.OnClickListener {
    private static final String TAG = "PickerButton";
    private boolean mForceEnable = false;

    //add by liyafeng for KEYWLVBS-166 20170110 (start)
    private boolean mIsLowPower = false;
    private Context mContext;
    //add by liyafeng for KEYWLVBS-166 20170110 (end)

    public interface Listener {
        boolean onPicked(PickerButton button, ListPreference preference, String newValue);
    }

    protected Listener mListener;
    protected IconListPreference mPreference;

    public PickerButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        //add by liyafeng for KEYWLVBS-166 20170110 (start)
        mContext = context;
        //add by liyafeng for KEYWLVBS-166 20170110 (end)
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void initialize(IconListPreference pref) {
        Log.d(TAG, "initialize(" + pref + ")");
        mPreference = pref;

        //add by liyafeng for KEYWLVBS-166 20170110 (start)
        IntentFilter batteryIntentFilter = new IntentFilter();
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        if (mPreference != null && SettingConstants.KEY_FLASH.equals(mPreference.getKey())) {
            mContext.registerReceiver(mBatteryIntentReceiver, batteryIntentFilter);
        }
        //add by liyafeng for KEYWLVBS-166 20170110 (end)
    }

    /**
     * Force to show this button on UI.
     */
    public void forceEnable() {
        mForceEnable = true;
    }

    /**
     * Do not force to show this button on UI.
     */
    public void cancelForcedEnable() {
        mForceEnable = false;
    }

    /**
     * Refresh the buttons status.
     */
    public void refresh() {
        Log.d(TAG, "refresh() " + mPreference);
        if (!isVisible()) {
            setVisibility(View.GONE);
        } else {
            setOnClickListener(null);
            String value = mPreference.getOverrideValue();
            if (value == null) {
                value = mPreference.getValue();
            }
            int index = mPreference.findIndexOfValue(value);
            int[] icons = mPreference.getIconIds();
            if (icons != null) {
                if (index >= 0 && index < icons.length) {
                    setImageResource(icons[index]);
                } else {
                    index = getValidIndexIfNotFind(value);
                    setImageResource(icons[index]);
                }
            }
            setOnClickListener(this);
            setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        if (mPreference == null) {
            Log.w(TAG, "onClick() why mPreference is null?", new Throwable());
            return;
        } else {
            if (!mPreference.isEnabled() && !mForceEnable) {
                Log.i(TAG, "onClick() mPreference's enable = false ,return this click event");
                return;
            }
        }

        //add by liyafeng for KEYWLVBS-166 20170110 (start)
        if (SettingConstants.KEY_FLASH.equals(mPreference.getKey())) {
            if (mIsLowPower) {
                return;
            }
        }
        //add by liyafeng for KEYWLVBS-166 20170110 (end)

        // Find the index of next camera.
        String value = mPreference.getOverrideValue();
        if (value == null) {
            value = mPreference.getValue();
        }
        int index = mPreference.findIndexOfValue(value);
        if (index < 0) { // make 0 as default
            index = getValidIndexIfNotFind(value);
        }
        CharSequence[] values = mPreference.getEntryValues();
        // do {
        index = (index + 1) % values.length;
        // } while (!mPreference.isEnabled(index));
        String next = values[index].toString();
        mPreference.setOverrideValue(null, false);
        if (mListener != null && mListener.onPicked(this, mPreference, next)) {
            // clear override value after user changed it
            //mPreference.setOverrideValue(null, false);
            mPreference.setValueIndex(index); // should be checked
            refresh();
        }
    }

    public void setValue(String value) {
        Log.v(TAG, "setValue(" + value + ") mPreference=" + mPreference);
        if (mPreference != null && value != null && !value.endsWith(mPreference.getValue())) {
            mPreference.setValue(value);
            refresh();
        }
    }

    protected int getValidIndexIfNotFind(String value) {
        return 0;
    }

    private boolean isVisible() {
        if (mForceEnable) {
            Log.i(TAG, "[isVisible], force enable is true, return true");
            return true;
        }
        if (mPreference == null) {
            Log.i(TAG, "[isVisible], mPreference is null, return false");
            return false;
        }
        if (mPreference.getEntries() == null || mPreference.getEntries().length <= 1) {
            Log.i(TAG, "[isVisible], entries is null or entries size less than one, return false");
            return false;
        }
        if (!mPreference.isEnabled()) {
            Log.i(TAG, "[isVisible], preference enable is false, return false");
            return false;
        }
        if (mPreference.isShowInSetting()) {
            Log.i(TAG, "[isVisible], this icon should be show in setting, return false");
            return false;
        }
        return true;
    }

    //add by liyafeng for KEYWLVBS-166 20170110 (start)
    private final BroadcastReceiver mBatteryIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "[onReceive]intent=" + intent);
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int level = (int)(100f
                    * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                if (level >= 0 && level <= 15) {
                    mIsLowPower = true;
                    mPreference.setValue(Parameters.FLASH_MODE_OFF);
                    //mPreference.setOverrideValue(Parameters.FLASH_MODE_OFF);
                    //refresh();
                    int[] icons = mPreference.getIconIds();
                    int index = mPreference.findIndexOfValue(Parameters.FLASH_MODE_OFF);
                    setImageResource(icons[index]);
                } else {
                    mIsLowPower = false;
                }
            }
        }
    };
    //add by liyafeng for KEYWLVBS-166 20170110 (end)
}
