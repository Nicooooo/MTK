package com.android.camera.manager;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import com.android.camera.manager.SettingManager.SettingListener;

import com.android.camera.CameraActivity;
import com.android.camera.FeatureSwitcher;
import com.android.camera.Log;
import com.android.camera.R;
import com.android.camera.Util;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.SettingListLayout;

import com.mediatek.camera.platform.ICameraAppUi.CommonUiType;
import com.mediatek.camera.platform.ICameraAppUi.ViewState;
import com.mediatek.camera.setting.preference.CameraPreference;
import com.mediatek.camera.ISettingCtrl;
import com.mediatek.camera.setting.preference.IconListPreference;
import com.mediatek.camera.setting.preference.ListPreference;
import com.mediatek.camera.setting.preference.PreferenceGroup;
import com.mediatek.camera.setting.SettingConstants;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import android.widget.SeekBar;
import com.xchengtech.ProjectConfig;

public class ProfessionalManager extends ViewManager implements View.OnClickListener, SeekBar.OnSeekBarChangeListener,
        SettingListLayout.Listener, CameraActivity.OnPreferenceReadyListener, OnTabChangeListener, OnItemClickListener {
    private static final String TAG = "ProfessionalManager";
    protected static final int SETTING_PAGE_LAYER = VIEW_LAYER_SETTING;

    protected static final int MSG_REMOVE_SETTING = 0;
    protected static final int DELAY_MSG_REMOVE_SETTING_MS = 3000; // delay

    private static int PROF_MF_MAX_VALUE = -1;
    private static String[] PROF_ITEM_KEY;
    private static int[] PROF_ITEM_ICON;
    private static int[] PROF_ITEM_LISTICON;

    protected ViewGroup mSettingLayout;
    private TabHost mTabHost;

    protected RotateImageView mIndicator;
    protected boolean mShowingContainer;
    private boolean mIsStereoFeatureSwitch;
    protected ISettingCtrl mSettingController;
    protected SettingListener mListener;
    private Animation mFadeIn;
    private Animation mFadeOut;
    private ListPreference mPreference;
    private boolean mCancleHideAnimation    = false;

    private int mCameraMode;
    private int mCurrentProgress;

    private TextView mItem_name;
    private SeekBar mItem_SeekBar;
    private int mItem_index;                //Item List Highlight Index

    private int mProf_default_pad;
    private int mSeekBar_width;
    private boolean isIconListItem          = false;

    protected Handler mMainHandler = new Handler() {
        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage(" + msg + ")");
            switch (msg.what) {
            case MSG_REMOVE_SETTING:
                // If we removeView and addView frequently, drawing cache may be wrong.
                // Here avoid do this action frequently to workaround that issue.
                if (mSettingLayout != null && mSettingLayout.getParent() != null) {
                    getContext().removeView(mSettingLayout, SETTING_PAGE_LAYER);
                }
                break;
            default:
                break;
            }
        };
    };

    public ProfessionalManager(CameraActivity context) {
        super(context);
        context.addOnPreferenceReadyListener(this);
    }

    @Override
    protected View getView() {
        View view = inflate(R.layout.professional_indicator);
        mIndicator = (RotateImageView) view.findViewById(R.id.professional_indicator);
        mIndicator.setOnClickListener(this);
        return view;
    }

    @Override
    public void show() {
        if(ProjectConfig.FEATURE_CAMERA_PROFESSIONAL_SUPPORT){
            super.show();
        }
    }

    @Override
    public void hide() {
        collapse(true);
        super.hide();
    }

    @Override
    public boolean collapse(boolean force) {
        boolean collapsechild = false;
        if (mShowingContainer) {
            hideSetting();
            collapsechild = true;
        }
        Log.i(TAG, "collapse(" + force + ") mShowingContainer=" + mShowingContainer + ", return " + collapsechild);
        return collapsechild;
    }

    @Override
    public void onRefresh() {
    }

    @Override
    protected void onRelease() {
        super.onRelease();
        releaseSettingResource();
    }

    @Override
    public void onOrientationChanged(int orientation) {
        super.onOrientationChanged(orientation);
        Util.setOrientation(mSettingLayout, orientation, true);
    }

    /**** OnItemClickListener Implementation ****/
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id){
        String item_key = mTabHost.getCurrentTabTag();
        ListPreference item_pref = mSettingController.getListPreference(item_key);

        int item_count = item_pref.getEntries().length;
        mCurrentProgress = (int)(position*100/(item_count - 1));

        mItem_SeekBar.setProgress(mCurrentProgress);
    }

    /**** View.OnClickListener Implementation ****/
    @Override
    public void onClick(View view) {
        if (view == mIndicator) {
            if (!mShowingContainer) {
                showProfessional();
            } else {
                collapse(true);
            }
        }
    }

    /**** SettingListLayout.Listener Implementation ****/
    @Override
    public void onSettingChanged(SettingListLayout settingList, ListPreference preference) {
        Log.i(TAG, "onSettingChanged(" + settingList + ")");
        if (mListener != null) {
            mListener.onSharedPreferenceChanged(preference);
            mPreference = preference;
        }
        refresh();
    }

    @Override
    public void onStereoCameraSettingChanged(SettingListLayout settingList,
            ListPreference preference, int index, boolean showing) {
        Log.i(TAG, "onStereo3dSettingChanged(" + settingList + ")" + ", type = " + index);
        if (mListener != null) {
            mIsStereoFeatureSwitch = true;
            mListener.onStereoCameraPreferenceChanged(preference, index);
            mPreference = preference;
        }
        if (getContext().getCurrentMode() == ModePicker.MODE_STEREO_CAMERA
                || (getContext().getCurrentMode() != ModePicker.MODE_STEREO_CAMERA && index == 2)) {
            refresh();
            return;
        }
        if (mShowingContainer) {
                if (mShowingContainer && mSettingLayout != null) {
                    mMainHandler.removeMessages(MSG_REMOVE_SETTING);
                    mSettingLayout.setVisibility(View.GONE);
                    getContext().getCameraAppUI().restoreViewState();
                    mIndicator.setImageResource(R.drawable.ic_pro_normal);
                    mMainHandler.sendEmptyMessageDelayed(MSG_REMOVE_SETTING,
                            DELAY_MSG_REMOVE_SETTING_MS);
                }
                setChildrenClickable(false);
        }

        if (getContext().isFullScreen()) {
            mMainHandler.removeMessages(MSG_REMOVE_SETTING);
            initializeSettings();
            refresh();
            highlightCurrentSetting();
            mSettingLayout.setVisibility(View.VISIBLE);
            if (mSettingLayout.getParent() == null) {
                getContext().addView(mSettingLayout, SETTING_PAGE_LAYER);
            }
            getContext().getCameraAppUI().setViewState(ViewState.VIEW_STATE_SETTING);
            mIndicator.setImageResource(R.drawable.ic_pro_focus);
            setChildrenClickable(true);
        }
    }

    @Override
    public void onRestorePreferencesClicked() {
        Log.i(TAG, "onRestorePreferencesClicked() mShowingContainer=" + mShowingContainer);
        if (mListener != null && mShowingContainer) {
            mListener.onRestorePreferencesClicked();
        }
    }

    /**** CameraActivity.OnPreferenceReadyListener Implementation ****/
    @Override
    public void onPreferenceReady() {
        releaseSettingResource();
    }

    /**** OnTabChangeListener Implementation ****/
    @Override
    public void onTabChanged(String key) {
        if (mTabHost != null) {
            highlightCurrentSetting();
        }
        Log.i(TAG, "onTabChanged(" + key + ")");
    }

    /**** SeekBar.OnSeekBarChangeListener Implementation ****/
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        String item_key = mTabHost.getCurrentTabTag();
        ListPreference item_pref = mSettingController.getListPreference(item_key);

        mItem_name.setText(item_pref.getTitle());

        int item_count = item_pref.getEntries().length;
        mItem_index = doComputeItemIndex(progress, item_count);
        mCurrentProgress = (int)(mItem_index*100/(item_count - 1));
        //Add for special item ManualFocus start
        doComputeProgressForSpecialItem(item_key, progress);
        //Add for special item ManualFocus end
        showItemListIcons(mItem_index);

        mItem_SeekBar.setProgress(progress);
        //Add for special item ManualFocus start
        if(item_key.equals(SettingConstants.KEY_CAMERA_MANUAL_FOCUS)){
            if(mItem_index > PROF_MF_MAX_VALUE){
                item_pref.setValueExtra("auto");
            }else{
                item_pref.setValueExtra(String.valueOf(mItem_index));
            }
        }else{
            item_pref.setValueIndex(mItem_index);
        }
        //Add for special item ManualFocus end

        if (mListener != null) {
            item_pref = mSettingController.getListPreference(item_key);
            mListener.onSharedPreferenceChanged(item_pref);
            mPreference = item_pref;
        }

        /*
         *  ISO value isn't auto, set ZSD value to off.
        */
        if(item_key.equals(SettingConstants.KEY_ISO)){
            ListPreference pref = mSettingController.getListPreference(SettingConstants.KEY_CAMERA_ZSD);
            if(item_pref.getValue().equals("auto")){
                pref.setValue("on");
            }else{
                pref.setValue("off");
            }
            if (mListener != null) {
                mListener.onSharedPreferenceChanged(pref);
            }
        }
        refresh();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do nothing
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Do nothing
        seekBar.setProgress(mCurrentProgress);
    }

    /**** Custom Method ****/
    public void showProfessional() {
        Log.i(TAG, "showProfessional() mShowingContainer=" + mShowingContainer
                + ", getContext().isFullScreen()=" + getContext().isFullScreen());
        if (getContext().isFullScreen()) {      //ActivityBase.java return true
            if (!mShowingContainer && getContext().getCameraAppUI().isNormalViewState()) {
                mMainHandler.removeMessages(MSG_REMOVE_SETTING);
                mShowingContainer = true;
                mListener.onSettingContainerShowing(mShowingContainer);
                initializeSettings();
                refresh();
                highlightCurrentSetting();
                mSettingLayout.setVisibility(View.VISIBLE);
                if (mSettingLayout.getParent() == null) {
                    getContext().addView(mSettingLayout, SETTING_PAGE_LAYER);
                }
                getContext().getCameraAppUI().setViewState(ViewState.VIEW_STATE_SETTING);
                startFadeInAnimation(mSettingLayout);
                mIndicator.setImageResource(R.drawable.ic_pro_focus);
            }
            setChildrenClickable(true);
        }
    }

    private void initializeSettings() {
        int currentMode = getContext().getCurrentMode();
        if ((mCameraMode != currentMode) || (mSettingLayout == null && mSettingController.getPreferenceGroup() != null)) {
            mSettingLayout = (ViewGroup) getContext().inflate(R.layout.professional_container, SETTING_PAGE_LAYER);
            mTabHost = (TabHost) mSettingLayout.findViewById(R.id.tab_title);
            mTabHost.setup();

            boolean frontCamera = getContext().getCameraId() == 1;
            boolean specialMode = currentMode != ModePicker.MODE_PHOTO;
            mCameraMode = currentMode;

            initProfessionalItemDataAndPreference();

            int size = PROF_ITEM_ICON.length;
            List<View> pageViews = new ArrayList<View>();
            for (int i = 0; i < size; i++) {
                if(!isCameraSupportCurrentItem(frontCamera, specialMode, PROF_ITEM_KEY[i])){
                    continue;
                }

                ImageView indicatorView = new ImageView(getContext());
                if (indicatorView != null) {
                    //indicatorView.setBackgroundResource(R.drawable.bg_tab_title);
                    indicatorView.setImageResource(PROF_ITEM_ICON[i]);
                    indicatorView.setScaleType(ScaleType.CENTER);
                }
                mTabHost.addTab(mTabHost.newTabSpec(PROF_ITEM_KEY[i]).setIndicator(indicatorView).setContent(android.R.id.tabcontent));
            }

            mTabHost.setOnTabChangedListener(this);
        }
        Util.setOrientation(mSettingLayout, getOrientation(), false);
    }

    private void highlightCurrentSetting() {
        String item_key = mTabHost.getCurrentTabTag();
        ListPreference item_pref = mSettingController.getListPreference(item_key);

        if(item_pref instanceof IconListPreference){
            isIconListItem = true;
        }else{
            isIconListItem = false;
        }

        mItem_name.setText(item_pref.getTitle());

        mItem_index = item_pref.findIndexOfValue(item_pref.getValue());
        int item_count = item_pref.getEntries().length;
        int progress = (int)(mItem_index*100/(item_count - 1));

        //Add for special item ManualFocus start
        if(item_key.equals(SettingConstants.KEY_CAMERA_MANUAL_FOCUS)){
            String item_value = item_pref.getValue();
            if(item_value.equals("auto")){
                progress = 0;
                mItem_index = -1;
            }else{
                mItem_index = Integer.parseInt(item_pref.getValue());
                progress = (int)(100 - mItem_index*90/PROF_MF_MAX_VALUE);
            }
        }
        //Add for special item ManualFocus end

        showItemListIcons(mItem_index);

        int seekBar_width = mSeekBar_width - (int)mSeekBar_width/item_count + mItem_SeekBar.getPaddingLeft() + mItem_SeekBar.getPaddingRight();
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(seekBar_width, LinearLayout.LayoutParams.WRAP_CONTENT);
        mItem_SeekBar.setLayoutParams(layoutParams);
        mItem_SeekBar.setProgress(progress);
        mItem_SeekBar.setOnSeekBarChangeListener(ProfessionalManager.this);
    }

    private void showItemListIcons(int item_index){
        GridView item_icons = (GridView)mSettingLayout.findViewById(R.id.prof_item_icon);
        int icon_count = -1;
        if(isIconListItem){
            icon_count = doGetListIcons().length;
        }else{
            icon_count = doGetListTexts().length;
        }
        item_icons.setNumColumns(icon_count);
        item_icons.setColumnWidth(item_icons.getWidth()/icon_count);

        try{
            item_icons.setAdapter(new ImageAdapter(getContext()));
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();  
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        item_icons.setOnItemClickListener(this);
    }

    public void hideSetting() {
        Log.i(TAG, "hideSetting() mShowingContainer=" + mShowingContainer + ", mSettingLayout=" + mSettingLayout);
        setChildrenClickable(false);
        if (mShowingContainer && mSettingLayout != null) {
            mMainHandler.removeMessages(MSG_REMOVE_SETTING);
            if (!mCancleHideAnimation) {
                startFadeOutAnimation(mSettingLayout);
            }
            mSettingLayout.setVisibility(View.GONE);
            mShowingContainer = false;
            //because into setting,ViewState will set mode picker false
            getContext().getCameraAppUI().getCameraView(CommonUiType.MODE_PICKER).setEnabled(true);
            getContext().getCameraAppUI().restoreViewState();
            mListener.onSettingContainerShowing(mShowingContainer);
            mIndicator.setImageResource(R.drawable.ic_pro_normal);
            mMainHandler.sendEmptyMessageDelayed(MSG_REMOVE_SETTING, DELAY_MSG_REMOVE_SETTING_MS);
        }
        mCancleHideAnimation = false;
    }

    public boolean isShowProContainer() {
        return mShowingContainer;
    }

    public void setListener(SettingListener listener) {
        mListener = listener;
    }

    public void setSettingController(ISettingCtrl settingController) {
        mSettingController = settingController;
    }

    public boolean handleMenuEvent() {
        boolean handle = false;
        if (isEnabled() && isShowing() && mIndicator != null) {
            mIndicator.performClick();
            handle = true;
        }
        Log.i(TAG, "handleMenuEvent() isEnabled()=" + isEnabled() + ", isShowing()=" + isShowing()
                + ", mIndicator=" + mIndicator + ", return " + handle);
        return handle;
    }

    public void resetSettings() {
        if (mSettingLayout != null && mSettingLayout.getParent() != null) {
            getContext().removeView(mSettingLayout, SETTING_PAGE_LAYER);
        }
        mSettingLayout = null;
    }

    protected void releaseSettingResource() {
        Log.i(TAG, "releaseSettingResource()");
        if (mIsStereoFeatureSwitch) {
            mIsStereoFeatureSwitch = false;
            Log.i(TAG, "releaseSettingResource is stereo feature, no need release");
            return;
        }
        collapse(true);
        if (mSettingLayout != null) {
            mSettingLayout = null;
        }
    }

    protected void startFadeInAnimation(View view) {
        if (mFadeIn == null) {
            mFadeIn = AnimationUtils.loadAnimation(getContext(), R.anim.setting_popup_grow_fade_in);
        }
        if (view != null && mFadeIn != null) {
            view.startAnimation(mFadeIn);
        }
    }

    protected void startFadeOutAnimation(View view) {
        if (mFadeOut == null) {
            mFadeOut = AnimationUtils.loadAnimation(getContext(),
                    R.anim.setting_popup_shrink_fade_out);
        }
        if (view != null && mFadeOut != null) {
            view.startAnimation(mFadeOut);
        }
    }

    public void cancleHideAnimation() {
        mCancleHideAnimation = true;
    }

    protected void setChildrenClickable(boolean clickable) {
        Log.i(TAG, "setChildrenClickable(" + clickable + ") ");
        PreferenceGroup group = mSettingController.getPreferenceGroup();
        if (group != null) {
            int len = group.size();
            for (int i = 0; i < len; i++) {
                CameraPreference pref = group.get(i);
                if (pref instanceof ListPreference) {
                    ((ListPreference) pref).setClickable(clickable);
                }
            }
        }
    }

    private int doComputeItemIndex(int progress, int count){
        int index = 0;
        int total = count - 1;
        if(total >= 100){
            index = total*progress/100;
            return index;
        }

        for(int i = 0; i < count; i++){
            int start = i*100 - 50;
            int end = i*100 + 50;
            int current = progress * total;

            if(current >= start && current < end){
                index = i;
                break;
            }
        }
        return index;
    }

    private int[] doGetListIcons(){
        String key = mTabHost.getCurrentTabTag();
        ListPreference pref = mSettingController.getListPreference(key);
        int[] itemListIcons = ((IconListPreference) pref).getIconIds();
        return itemListIcons;
    }

    private CharSequence[] doGetListTexts(){
        String key = mTabHost.getCurrentTabTag();
        ListPreference pref = mSettingController.getListPreference(key);
        CharSequence[] itemListTexts = pref.getEntries();
        return itemListTexts;
    }

    //Obtain Drawable Resource Ids
    private int[] getIds(Resources res, int iconsRes) {
        if (iconsRes == 0) return null;
        TypedArray array = res.obtainTypedArray(iconsRes);
        int n = array.length();
        int ids[] = new int[n];
        for (int i = 0; i < n; ++i) {
            ids[i] = array.getResourceId(i, 0);
        }
        array.recycle();
        return ids;
    }

    /**** ProfessionalItem Data && Preference Init ****/
    private void initProfessionalItemDataAndPreference(){
        initProfessionalItemData();
        //Add for special item ManualFocus start
        initManualFocusPreference();
        //Add for special item ManualFocus end
    }

    private void initProfessionalItemData(){
        PROF_MF_MAX_VALUE = getContext().getResources().getInteger(R.integer.config_prof_mf_max_value);

        PROF_ITEM_KEY = getContext().getResources().getStringArray(R.array.custom_prof_item_keys);
        PROF_ITEM_ICON = getIds(getContext().getResources(), R.array.custom_prof_item_icons);

        mItem_name = (TextView)mSettingLayout.findViewById(R.id.prof_item_name);
        mItem_SeekBar = (SeekBar)mSettingLayout.findViewById(R.id.prof_item_slider);

        int prof_width = (int)getContext().getResources().getDimension(R.dimen.setting_container_width);
        int prof_pad = (int)getContext().getResources().getDimension(R.dimen.setting_container_padding_left);
        mProf_default_pad = (int)getContext().getResources().getDimension(R.dimen.prof_default_padding);
        mSeekBar_width = prof_width - prof_pad*2 - mProf_default_pad*2;
    }

    private void initManualFocusPreference(){
        boolean isSupportManualFocus = Arrays.asList(PROF_ITEM_KEY).contains(SettingConstants.KEY_CAMERA_MANUAL_FOCUS);
        /*
         *  For ManualFocus set auto as init value
        */
        if(isSupportManualFocus){
            ListPreference prefMF = mSettingController.getListPreference(SettingConstants.KEY_CAMERA_MANUAL_FOCUS);
            int index = PROF_MF_MAX_VALUE*10/9;
            prefMF.setValueExtra(String.valueOf(index));
            if (mListener != null) {
                mListener.onSharedPreferenceChanged(prefMF);
            }
        }
    }

    /*
     *  Check current camera is supported special item.
    */
    private boolean isCameraSupportCurrentItem(boolean front, boolean special, String key){
        if(front && (key.equals(SettingConstants.KEY_CAMERA_MANUAL_FOCUS)
            || key.equals(SettingConstants.KEY_CAMERA_MANUAL_SHUTTER))){
            return false;
        }else if(special && key.equals(SettingConstants.KEY_CAMERA_MANUAL_SHUTTER)){
            resetManualShutterPreference(key);
            return false;
        }else{
            return true;
        }
    }

    private void resetManualShutterPreference(String key){
        ListPreference prefMS = mSettingController.getListPreference(key);
        prefMS.setValue("0");
        if (mListener != null) {
            mListener.onSharedPreferenceChanged(prefMS);
        }
    }

    /*
     *  For ManualFocus:
     *  No.0 -- entry: auto;  values: auto
     *  No.1 -- entry: 0;     values: MAX
     *  No.9 -- entry: MAX;   values: 0
    */
    private void doComputeProgressForSpecialItem(String key, int progress){
        if(key.equals(SettingConstants.KEY_CAMERA_MANUAL_FOCUS)){
            int item_count = PROF_MF_MAX_VALUE*10/9;
            mItem_index = doComputeItemIndex(100 - progress, item_count);
            if(progress < 10){
                mCurrentProgress = 0;
            }else{
                mCurrentProgress = progress;
            }
        }
    }

    private boolean isManualFocusItem(){
        String item_key = mTabHost.getCurrentTabTag();
        if(item_key.equals(SettingConstants.KEY_CAMERA_MANUAL_FOCUS)){
            return true;
        }else{
            return false;
        }
    }

    private boolean isShowItemHighlightBackground(int position){
        boolean show = false;
        if(isManualFocusItem()){
            if((position == 0) && (mItem_index > PROF_MF_MAX_VALUE || mItem_index == -1)){
                show = true;
            }else if(position == 1 && mItem_index == PROF_MF_MAX_VALUE){
                show = true;
            }
        }else if(mItem_index == position){
            show = true;
        }
        return show;
    }

    /**** Adapter for ItemListIcons  ****/
    private class ImageAdapter extends BaseAdapter{
        private Context mContext;
        private int mCount;
        private int[] imgList;
        private CharSequence[] txtList;

        public ImageAdapter(Context c) throws IllegalArgumentException, IllegalAccessException{
            mContext = c;
            if(isIconListItem){
                imgList = doGetListIcons();
                txtList = null;
                mCount = imgList.length;
            }else{
                imgList = null;
                txtList = doGetListTexts();
                mCount = txtList.length;
            }
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub\
            return mCount;
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public long getItemId(int position) {
            // TODO Auto-generated method stub
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // TODO Auto-generated method stub
            if(isIconListItem){
                ImageView mIV;
                if(convertView == null){
                    mIV = new ImageView(mContext);
                }else{
                    mIV = (ImageView)convertView;
                }

                if(mItem_index == position){
                    mIV.setBackgroundResource(R.drawable.list_pressed_holo_light);
                }

                mIV.setImageResource(imgList[position]);
                mIV.setLayoutParams(new GridView.LayoutParams(
                    parent.getWidth()/getCount(),
                    parent.getHeight()));
                return mIV;
            }else{
                TextView mTV;
                if(convertView == null){
                    mTV = new TextView(mContext);
                }else{
                    mTV = (TextView)convertView;
                }

                if(isShowItemHighlightBackground(position)){
                    mTV.setBackgroundResource(R.drawable.list_pressed_holo_light);
                }

                mTV.setText(txtList[position]);
                mTV.setGravity(Gravity.CENTER);
                mTV.setLayoutParams(new GridView.LayoutParams(
                    parent.getWidth()/getCount(),
                    parent.getHeight()));
                return mTV;
            }
        }
    }
}
