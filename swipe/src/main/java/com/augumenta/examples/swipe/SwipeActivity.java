/*
 * Copyright (c) 2012-2018 Augumenta Ltd. All rights reserved.
 *
 * This source code file is furnished under a limited license and may be used or
 * copied only in accordance with the terms of the license. Except as permitted
 * by the license, no part of this source code file may be  reproduced, stored in
 * a retrieval system, or transmitted, in any form or by  any means, electronic,
 * mechanical, recording, or otherwise, without the prior written permission of
 * Augumenta.
 *
 * This source code file contains proprietary information that is protected by
 * copyright. Certain parts of proprietary information is patent protected. The
 * content herein is furnished for informational use only, is subject to change
 * without notice, and should not be construed as a commitment by Augumenta.
 * Augumenta assumes no responsibility or liability for any errors or
 * inaccuracies that may appear in the informational content contained herein.
 * This source code file has not been thoroughly tested under all conditions.
 * Augumenta, therefore, does not guarantee or imply its reliability,
 * serviceability, or function.
 *
 */

package com.augumenta.examples.swipe;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.augumenta.agapi.AugumentaManager;
import com.augumenta.agapi.CameraFrameProvider;
import com.augumenta.agapi.HandPath;
import com.augumenta.agapi.HandPath.Path;
import com.augumenta.agapi.HandPathEvent;
import com.augumenta.agapi.HandPathListener;
import com.augumenta.agapi.HandPose;
import com.augumenta.agapi.Poses;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import static androidx.fragment.app.FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT;

/**
 * SwipeActivity demonstrates the usage of HandPath detection and detection without camera preview.
 * When specified pose (fist) is swiped across the frame then the page is changed accordingly.
 */
public class SwipeActivity extends FragmentActivity {
	private static final String TAG = SwipeActivity.class.getSimpleName();
	private static final int MAX_PAGE_NUMBERS = 10;

	// On Android 6.0 and later permissions are requested at runtime.
	// See more information about requesting permission at runtime at:
	// https://developer.android.com/training/permissions/requesting.html

	// Permission request code for CAMERA permission
	private static final int PERMISSION_REQUEST_CAMERA = 0;
	private android.hardware.Camera.CameraInfo cameraInfo;

	// AugumentaManager instance
	private AugumentaManager mAugumentaManager;
	private long prev_timestamp = 0;
	private final long HOLDOFF = 500; // 1 sec in milliseconds

	ViewPager mViewPager;
	ViewPagerAdapter mViewPagerAdapter;

	// This is for handling display rotations
	private DisplayManager.DisplayListener displayListener;

	private void updateDisplayOrientation() {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
		}
		CameraFrameProvider cameraFrameProvider = ((CameraFrameProvider) mAugumentaManager.getFrameProvider());
		if (cameraFrameProvider != null) {
			cameraFrameProvider.setDisplayOrientation(degrees);
			int result;
			android.hardware.Camera.getCameraInfo(cameraFrameProvider.getCameraId(), cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				result = (cameraInfo.orientation + degrees) % 360;
				result = (360 - result) % 360;  // compensate the mirror
			} else {  // back-facing
				result = (cameraInfo.orientation - degrees + 360) % 360;
			}
			Camera camera = cameraFrameProvider.getCamera();
			if (camera != null) {
				camera.setDisplayOrientation(result);
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_swipe);

		SurfaceView mCameraPreview = findViewById(R.id.camera_preview);
		cameraInfo = new android.hardware.Camera.CameraInfo();

		// Get AugumentaManager instance with default CameraFrameProvider
		// without camera preview.
		try {
			mAugumentaManager = AugumentaManager.getInstance(this);
		} catch (IllegalStateException e) {
			// Something went wrong while authenticating license
			Toast.makeText(this, "License error: " + e.getMessage(), Toast.LENGTH_LONG).show();
			Log.e(TAG, "License error: " + e.getMessage());

			// Close the app before AugumentaManager is used, otherwise it will cause
			// NullPointerException when trying to use it.
			finish();
			return;
		}
		CameraFrameProvider provider = (CameraFrameProvider) mAugumentaManager.getFrameProvider();
		// To make detection faster, set the CameraFrameProvider in a fast mode.
		// In fast mode the lowest supported camera preview resolution is used
		// for detection which makes the detection faster.
		//provider.setFastMode(true);
		provider.setCameraPreview(mCameraPreview);

		// Register listener for horizontal swipe motion of the fist (P032) or you can use just a
		// motion detector (M001) that checks if there is a big object passing in the frames, like a
		// hand. It works for both hands to change current page.
		// HandPath swipe_horizontal = new HandPath(new HandPose(Poses.P032), Path.SWIPE_HORIZONTAL);
		HandPath swipe_horizontal = new HandPath(new HandPose(Poses.M001), Path.SWIPE_HORIZONTAL);
		// Register listener for the horizontal swipe motion
		mAugumentaManager.registerListener(mSwipeListener, swipe_horizontal);
/*
		// Register different listener for vertical swipe motion to show a Toast notification
		mAugumentaManager.registerListener(new HandPathListener() {
				@Override
				public void onPath(final HandPathEvent handPathEvent) {
			// Run on UI thread to show the Toast notification
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					String message;
					if (handPathEvent.motionY > 0) {
						// swipe from top to bottom
						message = "Downwards swipe";
					} else {
						// swipe from bottom to top
						message = "Upwards swipe";
					}
					Toast.makeText(SwipeActivity.this, message, Toast.LENGTH_SHORT).show();
				}
			    });
			}
		}, new HandPath(new HandPose(Poses.P001), Path.SWIPE_VERTICAL));
*/
		// It is possible to register same pose to multiple listeners or
		// multiple poses to same listener. It all depends on the use case.

		// Initialize the page viewer
		mViewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
		mViewPager = findViewById(R.id.pager);
		mViewPager.setAdapter(mViewPagerAdapter);
		mViewPager.setCurrentItem(5);
	}

	@Override
	public void onResume() {
		super.onResume();
/*
		// set screen brightness to minimum
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = 0.1f;
		getWindow().setAttributes(lp);
*/
		// Check if the Camera permission is already available
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
				!= PackageManager.PERMISSION_GRANTED) {
			// Camera permission has not been granted
			requestCameraPermission();
		} else {
			// Camera permission is already available
			// Start detection when activity is resumed
			startAugumentaManager();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (displayListener != null) {
			DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
			dm.unregisterDisplayListener(displayListener);
		}
		// No need to unregister pose, because they were registered at onCreate
		// and are valid the whole life-cycle of the application.

		// Remember to stop the detection when the activity is paused.
		// Otherwise the camera is not released and other applications are unable to
		// open the camera.
		mAugumentaManager.stop();
	}

	private void startAugumentaManager() {
		if (!mAugumentaManager.start()) {
			// failed to start frame provider, probably failed to open camera
			Toast.makeText(this, "Failed open Camera!", Toast.LENGTH_LONG).show();
			// close application
			finish();
			return;
		}
		DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
		displayListener = new DisplayManager.DisplayListener() {
			@Override
			public void onDisplayAdded(int displayId) {
			}

			@Override
			public void onDisplayRemoved(int displayId) {
			}

			@Override
			public void onDisplayChanged(int displayId) {
				updateDisplayOrientation();
			}
		};
		dm.registerDisplayListener(displayListener, null);
		// On some devices you might not want the head motion enabled
		mAugumentaManager.setHeadMotionEnabled(true);
		updateDisplayOrientation();
	}

	private void requestCameraPermission() {
		// Request CAMERA permission from user
		Log.d(TAG, "Requesting CAMERA permission");
		ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == PERMISSION_REQUEST_CAMERA) {
			Log.d(TAG, "Received response for CAMERA permission");

			if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Log.d(TAG, "CAMERA permission granted, starting AugumentaManager");
			} else {
				Log.d(TAG, "CAMERA permission not granted, exiting..");
				Toast.makeText(this, "Camera permission was not granted.", Toast.LENGTH_LONG).show();
				finish();
			}
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}


	/**
	 * HandPathListener to listen for horizontal swipe motions.
	 * When swipe motion is detected, change the current page of the page viewer.
	 */
	private final HandPathListener mSwipeListener = new HandPathListener() {
		@Override
		public void onPath(final HandPathEvent handPathEvent) {
			Log.d(TAG, "onPath: " + handPathEvent);
			// If multiple poses are registered to a single listener,
			// you can check which pose triggered the event by examining
			// the handPoseEvent object

			// Run in the UI thread to interact with UI components
			runOnUiThread(() -> {
				long timestamp = System.currentTimeMillis();
				Log.d(TAG, "onPath: prev time=" + prev_timestamp);
				int current_item = mViewPager.getCurrentItem();
				Log.d(TAG, "onPath: curr time=" + timestamp);
				if (prev_timestamp + HOLDOFF < timestamp) {
					prev_timestamp = timestamp;
					if (handPathEvent.motionX < 0) {
						// Motion was from right to left, change to next page
						Log.d(TAG, "Show next page");
						mViewPager.setCurrentItem(current_item + 1, true);
					} else {
						// Motion was from left to right, change to previous page
						Log.d(TAG, "Show previous page");
						mViewPager.setCurrentItem(current_item - 1, true);
					}
				} else {
					Log.d(TAG, "onPath: Hold OFF");
				}
			});
		}
	};

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the pages.
	 */
	public static class ViewPagerAdapter extends FragmentPagerAdapter {
		public ViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
			super(fm, behavior);
		}

		@NonNull
		@Override
		public androidx.fragment.app.Fragment getItem(int position) {
			// getItem is called to instantiate the fragment for the given page.
			return PlaceholderFragment.newInstance(position + 1);
		}

		@Override
		public int getCount() {
			return MAX_PAGE_NUMBERS;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return "Page " + (position + 1);
		}
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {
		private static final String ARG_PAGE_NUMBER = "page_number";

		private static final int[] PAGE_COLOR = new int[]{
				Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.WHITE, Color.YELLOW
		};

		public static PlaceholderFragment newInstance(int pageNumber) {
			PlaceholderFragment fragment = new PlaceholderFragment();
			Bundle args = new Bundle();
			args.putInt(ARG_PAGE_NUMBER, pageNumber);
			fragment.setArguments(args);
			return fragment;
		}

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
								 Bundle savedInstanceState) {

			int page_num = getArguments() != null ? getArguments().getInt(ARG_PAGE_NUMBER): 0;
			int page_color = PAGE_COLOR[page_num % PAGE_COLOR.length];

			View rootView = inflater.inflate(R.layout.fragment_swipe, container, false);
			rootView.setBackgroundColor(page_color);
			TextView text = rootView.findViewById(R.id.page_label);
			text.setText(getString(R.string.page_number_info,
					page_num,
					MAX_PAGE_NUMBERS));
			return rootView;
		}
	}
}
