package com.bitgriff.helpers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.net.Uri;
import android.os.Environment;

abstract public class CameraHelper {
	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
	public static File photoFile;
	
	/** Create a file Uri for saving an image or video */
	public static Uri getOutputMediaFileUri(int type){
	      return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	public static File getOutputMediaFile(int type){
	    File mediaStorageDir = new File(Environment.getExternalStorageDirectory() + File.separator + "FSSMobile");
	    if (!mediaStorageDir.exists()) {
	    	if (!mediaStorageDir.mkdir())
	    		mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
	    }
	    
	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "IMG_"+ timeStamp + ".jpg");
	    } 
	    else if(type == MEDIA_TYPE_VIDEO) {
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "VID_"+ timeStamp + ".mp4");
	    }
	    else {
	        return null;
	    }

	    return mediaFile;
	}
	
	/**
	 * Checks if media storage (SD card) is available.
	 * @return <code>true</code> if available
	 */
	public static boolean isMediaStorageAvailable() {
		return (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
	}
}
