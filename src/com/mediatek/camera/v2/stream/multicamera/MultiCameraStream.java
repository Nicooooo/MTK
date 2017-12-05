/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2015. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */
package com.mediatek.camera.v2.stream.multicamera;

import android.app.Activity;
import android.hardware.Camera.CameraInfo;
import android.media.CamcorderProfile;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.ViewGroup;

import com.mediatek.camera.debug.LogHelper;
import com.mediatek.camera.debug.LogHelper.Tag;
import com.mediatek.camera.v2.platform.device.IMultiCameraDeviceAdapter;
import com.mediatek.camera.v2.stream.CaptureStream;
import com.mediatek.camera.v2.stream.ICaptureStream;
import com.mediatek.camera.v2.stream.IPreviewStream;
import com.mediatek.camera.v2.stream.IRecordStream;
import com.mediatek.camera.v2.stream.PreviewStream;
import com.mediatek.camera.v2.stream.RecordStream;
import com.mediatek.camera.v2.stream.multicamera.renderer.AnimationRect;
import com.mediatek.camera.v2.stream.multicamera.renderer.RendererConfig;
import com.mediatek.camera.v2.stream.multicamera.renderer.RendererConfig.RendererType;
import com.mediatek.camera.v2.stream.multicamera.renderer.RendererManager;
import com.mediatek.camera.v2.stream.multicamera.renderer.RendererManager.RendererCallback;

import junit.framework.Assert;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * this class used for Multi Camera stream.
 */
public class MultiCameraStream implements IPreviewStream, ICaptureStream,
        IRecordStream, ISinkStreamCtrl {

    private static final Tag TAG = new Tag(
            MultiCameraStream.class.getSimpleName());
    private static final int ORIENTATION_HYSTERESIS = 5;
    private String mCameraIdPref = "camera_id_";

    private int mGSensorOrientation = 0;
    private int mOrienationHistory = -1;
    private int mRemoteOrientation = 0;
    private boolean mPaused = false;
    private Size mPreviewSize;
    private Size mPictureSize;

    private List<String> mPreviewCameraId = new ArrayList<>();
    private List<String> mOpenedCameraId = new ArrayList<>();


    private CaptureStream mNextCaptureStream;
    private ISinkGestureCtrl mISinkGestureCtrl;
    private PreviewStream mNextPreviewStream;
    private RecordStream mNextRecordStream;
    private RendererManager mRenderManager;

    private SinkRenderCallbackImpl mSinkRenderCallbackImpl = new SinkRenderCallbackImpl();

    private Surface mOutPreviewSurface;
    private Surface mOutCaptureSurface;

    private Object mSyncObject = new Object();


    private static final List<Integer> LIST_SUPPORTORIENTATION = Arrays.asList(0, 90, 180, 270);
    private static final int[][] MATRIX_ORIENTATIONCOMPENSATION = new int[][] {
       //0, 90, 180, 270 (Local Orientation)
        {0, 90, 180, 270}, //0 (Remote Orientation)
        {270, 0, 90, 180}, //90 (Remote Orientation)
        {180, 270, 0, 90}, //180 (Remote Orientation)
        {90, 180, 270, 0}  //270 (Remote Orientation)
    };

    /**
     * Create a multi camera stream controller.
     * @param previewStreamController preview stream.
     * @param captureStreamController capture stream.
     * @param recordStreamController recording stream.
     */
    public MultiCameraStream(PreviewStream previewStreamController,
            CaptureStream captureStreamController, RecordStream recordStreamController) {
        LogHelper.i(TAG, "[MultiCameraStream] constract...........");
        Assert.assertNotNull(previewStreamController);
        Assert.assertNotNull(captureStreamController);
        Assert.assertNotNull(recordStreamController);

        mNextPreviewStream = previewStreamController;
        mNextCaptureStream = captureStreamController;
        mNextRecordStream  = recordStreamController;
    }

    @Override
    public void open(Activity activity, ViewGroup parentView) {
        LogHelper.i(TAG, "[open]");
        mRenderManager = new RendererManager(activity, mSinkRenderCallbackImpl);
        mRenderManager.init();

        mISinkGestureCtrl = new MultiCameraGestureImpl(activity);
    }

    @Override
    public void resume() {
        LogHelper.i(TAG, "[resume]");
        mPaused = false;
        resetStreamStatus();
        mRenderManager.init();
    }

    @Override
    public void pause() {
        LogHelper.i(TAG, "[pause]");
        synchronized (mSyncObject) {
            mPaused = true;
            mRenderManager.unInit();
        }
    }

    @Override
    public void close() {
        if (mISinkGestureCtrl != null) {
            mISinkGestureCtrl = null;
        }
    }

    /*
     * **********************************************************
     * ***********************PreviewStreamController ***********
     * **********************************************************
     */
    @Override
    public boolean updatePreviewSize(Size size) {
        LogHelper.d(TAG,
                        "[updatePreviewSize] width = " + size.getWidth() + ",height = "
                                        + size.getHeight() + ",mPreviewCameraId = "
                                        + mPreviewCameraId + ",mPaused = " + mPaused + " +++++");

        mPreviewSize = size;
        // First :notify next Stream update the preview size
        mNextPreviewStream.updatePreviewSize(mPreviewSize);

        // Second: notify the render update the preview size
        // TODO: please check ,current need update the picture size ? maybe not
        // need,because current not change the picture size
        if (!mPaused) {
            synchronized (mSyncObject) {
                for (String cameraId : mPreviewCameraId) {
                    if (isRenderCreated(cameraId)) {
                        configureRenderPreviewSize(mPreviewSize, cameraId);
                    } else {
                        configureRender(mPreviewSize, mPictureSize, cameraId);
                    }
                }
            }

            if (mISinkGestureCtrl != null) {
                mISinkGestureCtrl.setPreviewSize(mPreviewSize);
                List<String> previewCameraCopy = new ArrayList<String>();
                previewCameraCopy.addAll(mPreviewCameraId);
                mISinkGestureCtrl.updatePreviewCamera(previewCameraCopy);
                setRenderRect(mGSensorOrientation);
            }
        }
        return true;
    }

    @Override
    public Map<String, Surface> getPreviewInputSurfaces() {

        // tell renderManager,the surface will be drawing for preview.
        mOutPreviewSurface = mNextPreviewStream.getPreviewInputSurfaces().get(PREVIEW_SURFACE_KEY);
        if (mOutPreviewSurface != null) {
            mRenderManager.setPreviewSurface(mOutPreviewSurface);
        }
        return getRenderPreviewSurface();
    }

    @Override
    public void setPreviewStreamCallback(PreviewStreamCallback callback) {
        mNextPreviewStream.setPreviewStreamCallback(callback);
    }

    @Override
    public void setOneShotPreviewSurfaceCallback(PreviewSurfaceCallback surfaceCallback) {
        mNextPreviewStream.setOneShotPreviewSurfaceCallback(surfaceCallback);
    }

    @Override
    public void onFirstFrameAvailable() {
        // Do nothing.
        // Sink display surface receive from Sink renderer result.
    }

    @Override
    public void releasePreviewStream() {
        mNextPreviewStream.releasePreviewStream();
    }

    /*
     * *******************************************************
     * ******************CaptureStreamController *************
     */
    @Override
    public void setCaptureStreamCallback(CaptureStreamCallback callback) {
        mNextCaptureStream.setCaptureStreamCallback(callback);
    }

    @Override
    public boolean updateCaptureSize(Size pictureSize, int pictureFormat) {
        // update the picture size
        mPictureSize = pictureSize;
        synchronized (mSyncObject) {
            if (!mPaused) {
                for (String id : mPreviewCameraId) {
                    if (isRenderCreated(id)) {
                        configureRenderPictureSize(mPictureSize, id);
                    } else {
                        configureRender(mPreviewSize, mPictureSize, id);
                    }
                }
            }
        }
        return mNextCaptureStream.updateCaptureSize(pictureSize, pictureFormat);
    }

    // This surface will configured to Camera,where the capture data put
    @Override
    public Map<String, Surface> getCaptureInputSurface() {
        mOutCaptureSurface = mNextCaptureStream.getCaptureInputSurface().get(
                CAPUTRE_SURFACE_KEY);
        mRenderManager.setCaptureSurface(mOutCaptureSurface);

        return getRenderCaptureSurface();
    }

    @Override
    public void releaseCaptureStream() {
        mNextCaptureStream.releaseCaptureStream();
    }

    @Override
    public void onOrientationChanged(int gsensorOrientation) {
        int newOrientation = calculateOrientation(gsensorOrientation, mOrienationHistory);
        if (mOrienationHistory == newOrientation || mPaused) {
            return;
        }
        mOrienationHistory = newOrientation;
        mGSensorOrientation = gsensorOrientation;
        int orientation = getRemotePreviewOrientationValue(mOrienationHistory, mRemoteOrientation);
        LogHelper.v(TAG, "newOrientation = " + newOrientation + ",orientation = " + orientation);
        if (mRenderManager != null) {
            mRenderManager.updateGSensorOrientation(orientation);
        }
    }

    @Override
    public void updatePreviewCamera(List<String> previewCameraId, List<String> hasOpenedCamera) {
        LogHelper.i(TAG, "[updatePreviewCamera] preview id: " + mPreviewCameraId + "--> "
                + previewCameraId + ",Opened id: " + mOpenedCameraId + "--> " + hasOpenedCamera);

        if (mPaused) {
            LogHelper.i(TAG, "[updatePreviewCamera] mpaused so return");
            return;
        }

        // the preview camera have changed,but the opened Camera State not changed.
        // so can't delete the not preview but have opened camera

        synchronized (mSyncObject) {
            // Step1: delete the render which the camera is closed
            for (String cameraId : mOpenedCameraId) {
                if (!hasOpenedCamera.contains(cameraId)) {
                    unConfigureRender(cameraId);
                }
            }

            // Step2: notify the sinkGestureCtrl to update the rectangle area.
            mPreviewCameraId = previewCameraId;
            mOpenedCameraId = hasOpenedCamera;

            List<String> previewCameraCopy = new ArrayList<String>();
            previewCameraCopy.addAll(mPreviewCameraId);
            mISinkGestureCtrl.updatePreviewCamera(previewCameraCopy);

            // Step3: configure render or update the preview size.
            for (String id : mPreviewCameraId) {
                if (!isRenderCreated(id)) {
                    configureRender(mPreviewSize, mPictureSize, id);
                } else {
                    configureRenderPreviewSize(mPreviewSize, id);
                }
            }
        }

        // Step4: set the correction Render area to render.
        setRenderRect(mGSensorOrientation);
        // Step5: force update rotate orientaiton.
        mOrienationHistory = -1;
        onOrientationChanged(mGSensorOrientation);
     }

    @Override
    public void setSnapShotSurface(Surface snapShotSurface) {
        LogHelper.d(TAG, "[setSnapShotSurface]");
        mRenderManager.setSnapshotSurface(snapShotSurface);
    }

    @Override
    public void doSnapShot() {
        LogHelper.d(TAG, "[doSnapShot]");
        mRenderManager.doSnapshot();
    }

    /*
     * ***********************************************************
     * **********************Recording Stream Controller *********
     * ************************************************************
     */
    @Override
    public void registerRecordingObserver(RecordStreamStatus status) {
        mNextRecordStream.registerRecordingObserver(status);
    }

    @Override
    public void unregisterCaptureObserver(RecordStreamStatus status) {
        mNextRecordStream.unregisterCaptureObserver(status);
    }

   @Override
    public void setRecordingProfile(CamcorderProfile profile) {
       mNextRecordStream.setRecordingProfile(profile);
    }

    @Override
    public void setMaxDuration(int maxDuration) {
        mNextRecordStream.setMaxDuration(maxDuration);
    }

    @Override
    public void setMaxFileSize(long maxFileSize) {
        mNextRecordStream.setMaxFileSize(maxFileSize);
    }

    @Override
    public void setOutputFile(String path) {
        mNextRecordStream.setOutputFile(path);
    }

    @Override
    public void setOutputFile(FileDescriptor fd) {
        mNextRecordStream.setOutputFile(fd);
    }

    @Override
    public void setCaptureRate(double fps) {
        mNextRecordStream.setCaptureRate(fps);
    }

    @Override
    public void setLocation(float latitude, float longitude) {
        mNextRecordStream.setLocation(latitude, longitude);
    }

    @Override
    public void setOrientationHint(int degrees) {
        mNextRecordStream.setOrientationHint(degrees);
    }

    @Override
    public void enalbeAudioRecording(boolean enableAudio) {
        mNextRecordStream.enalbeAudioRecording(enableAudio);
    }

    @Override
    public void setAudioSource(int audioSource) {
        mNextRecordStream.setAudioSource(audioSource);
    }

    @Override
    public void setHDRecordMode(String mode) {
        mNextRecordStream.setHDRecordMode(mode);
    }

    @Override
    public void setVideoSource(int videoSource) {
        mNextRecordStream.setVideoSource(videoSource);
    }

    @Override
    public void setMediaRecorderParameters(List<String> paramters) {
        mNextRecordStream.setMediaRecorderParameters(paramters);

    }

    @Override
    public void prepareRecord() {
        mNextRecordStream.prepareRecord();
    }

    @Override
    public void startRecord() {
        mNextRecordStream.startRecord();
    }

    @Override
    public void pauseRecord() {
        mNextRecordStream.pauseRecord();
    }

    @Override
    public void resumeRecord() {
        mNextRecordStream.resumeRecord();
    }

    @Override
    public void stopRecord(boolean needSaveVideo) {
        mNextRecordStream.stopRecord(needSaveVideo);
    }

    @Override
    public boolean deleteVideoFile() {
        return mNextRecordStream.deleteVideoFile();
    }

    @Override
    public Surface getRecordInputSurface() {
        return mNextRecordStream.getRecordInputSurface();
    }

    @Override
    public void releaseRecordStream() {
        mNextRecordStream.releaseRecordStream();
    } // end of Recording Stream Controller

    @Override
    public void onServiceEventUpdate(int event, int value, String cameraId) {
        //if mOrientationHistory is not initialized, do not update orientation
        //to render manager.
        if (mOrienationHistory == -1) {
            mRemoteOrientation = value;
            return;
        }
        if (event == IMultiCameraDeviceAdapter.REMOTE_CAMERA_ORIENTATION_CHANGE) {
            mRemoteOrientation = value;
            int orientation = getRemotePreviewOrientationValue(mOrienationHistory,
                            mRemoteOrientation);
            LogHelper.i(TAG, "[onServiceEventUpdate] value " + value + ",orientation = "
                            + orientation);
            if (mRenderManager != null) {
                mRenderManager.updateGSensorOrientation(orientation);
            }
        }
    }

    private int getRemotePreviewOrientationValue(int localOrientaion, int remoteOrientation) {
        LogHelper.i(TAG, "[getRemotePreviewOrientationValue] localOrientaion " + localOrientaion
                + " remoteOrientation " + remoteOrientation);
        int orientation = 0;
        int localIndex = LIST_SUPPORTORIENTATION.indexOf(localOrientaion);
        int remoteIndex = LIST_SUPPORTORIENTATION.indexOf(remoteOrientation);

        orientation = MATRIX_ORIENTATIONCOMPENSATION[remoteIndex][localIndex];
        return orientation;
    }

    /**
     * When first frame available,will be notified.
     */
    private class SinkRenderCallbackImpl implements RendererCallback {

        @Override
        public void onFristFrameAvailable() {
            mNextPreviewStream.onFirstFrameAvailable();
        }
    }

    private void resetStreamStatus() {
        mOutCaptureSurface = null;
        mOutPreviewSurface = null;
        mPictureSize = null;
        mPreviewSize = null;
    }

    private void setRenderRect(int orientation) {
        if (mRenderManager != null && mISinkGestureCtrl != null) {
            //if camera not preview,set the render rect to [0,0,0,0].
            for (String diedPreviewCamera : mOpenedCameraId) {
                if (!mPreviewCameraId.contains(diedPreviewCamera)) {
                    if (!isRenderCreated(diedPreviewCamera)) {
                        configureRender(mPreviewSize, mPictureSize, diedPreviewCamera);
                    }
                    // set the render rect to empty
                    LogHelper.d(TAG, "setRenderRect,diedPreviewCamera = " + diedPreviewCamera);
                    mRenderManager.setRendererRect(mISinkGestureCtrl.getEmptyGraphicRect(),
                                    mCameraIdPref + diedPreviewCamera);
                }
            }

            // Set the correct preview camera rect and orientaion.
            for (String cameraId : mPreviewCameraId) {
                if (!isRenderCreated(cameraId)) {
                    configureRender(mPreviewSize, mPictureSize, cameraId);
                }

                // Set Render correct area
                AnimationRect rect = null;
                if (isLocalCamera(cameraId)) {
                    rect = mISinkGestureCtrl.getBottomGraphicRect(orientation);
                } else {
                    rect = mISinkGestureCtrl.getTopGraphicRect(orientation);
                }
                mRenderManager.setRendererRect(rect, mCameraIdPref + cameraId);
                mRenderManager.updateGSensorOrientation(orientation);
            }
        }
    }

    private boolean isLocalCamera(String cameraId) {
        boolean localCamera = false;
        if (String.valueOf(CameraInfo.CAMERA_FACING_BACK).equals(cameraId)
                || String.valueOf(CameraInfo.CAMERA_FACING_FRONT).equals(cameraId)) {
            localCamera = true;
        }
        return localCamera;
    }

    private Map<String, Surface> getRenderPreviewSurface() {
        Map<String, Surface> previewInputSurface = new HashMap<String, Surface>();
        Surface privewSurface = null;
        synchronized (mSyncObject) {
            for (String cameraId : mOpenedCameraId) {
                String cameraIdKey = mCameraIdPref + cameraId;
                privewSurface = new Surface(mRenderManager.getPreviewSurfaceTexture(cameraIdKey));
                previewInputSurface.put(cameraId, privewSurface);
            }
        }

        LogHelper.i(TAG, "[getRenderPreviewSurface] previewInputSurface = " + previewInputSurface);
        return previewInputSurface;
    }

    private Map<String, Surface> getRenderCaptureSurface() {
        Map<String, Surface> captureInputSurfaces = new HashMap<String, Surface>();
        synchronized (mSyncObject) {
            for (String cameraId : mOpenedCameraId) {
                if (isLocalCamera(cameraId)) {
                    captureInputSurfaces.put(cameraId, mNextCaptureStream
                            .getCaptureInputSurface().get(CaptureStream.CAPUTRE_SURFACE_KEY));
                }
            }
        }
        return captureInputSurfaces;
    }

    private boolean isRenderCreated(String cameraId) {
        String cameraIdKey = mCameraIdPref + cameraId;
        boolean isCreated = mRenderManager.isContainsRender(cameraIdKey);
        LogHelper.i(TAG, "[isRenderCreated], cameraIdKey = " + cameraIdKey
                + ",isCreated = " + isCreated);
        return isCreated;
    }

    private void configureRender(Size previewSize, Size pictureSize, String cameraId) {
        LogHelper.i(TAG, "[configureRender] previewSize = " + previewSize + ",pictureSize = "
                + pictureSize + ",cameraId = " + cameraId);

        // create RenderManager
        String renderKey = mCameraIdPref + cameraId;

        RendererConfig config = new RendererConfig(renderKey, RendererType.RENDERER_PREVIEW);
        if (isLocalCamera(cameraId)) {
            config.setIsNeedRotate(false);
        } else {
            config.setIsNeedRotate(true);
        }
        mRenderManager.addRenderer(config);

        configureRenderPreviewSize(previewSize, cameraId);
        configureRenderPictureSize(pictureSize, cameraId);
    }

    private void configureRenderPreviewSize(Size previewSize, String cameraId) {
        String renderKey = mCameraIdPref + cameraId;
        LogHelper.d(TAG, "[configureRenderPreviewSize] size = " + previewSize
                + ",Key = " + renderKey);
        if (previewSize != null) {
            mRenderManager.setPreviewSize(previewSize, renderKey);
        }
    }

    private void configureRenderPictureSize(Size pictureSize, String cameraId) {
        String renderKey = mCameraIdPref + cameraId;
        LogHelper.d(TAG, "[configureRenderPictureSize] size = " + pictureSize
                + ",Key = " + renderKey);
        if (pictureSize != null) {
            // set capture size
            mRenderManager.setCaptureSize(pictureSize, renderKey);
        }
    }

    private void unConfigureRender(String cameraId) {
        String renderKey = mCameraIdPref + cameraId;
        if (mRenderManager.isContainsRender(renderKey)) {
            mRenderManager.deleteRender(renderKey);
            LogHelper.i(TAG, "[unConfigureRender] renderKey = " + renderKey);
        }
    }

    private int calculateOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation = false;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
            int dist = Math.abs(orientation - orientationHistory);
            dist = Math.min(dist, 360 - dist);
            changeOrientation = (dist >= 45 + ORIENTATION_HYSTERESIS);
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }
}
