package com.bitgriff.androiddrupal;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.bitgriff.helpers.CameraHelper;
import com.bitgriff.helpers.GUIHelper;
import com.bitgriff.http.DrupalConnect;
import com.bitgriff.http.DrupalConnect.PhotoParams;
import com.bitgriff.http.HttpProgressListener;

/**
 * Activity to post pages to Drupal.
 * @author Moskvichev Andrey V.
 *
 */
public class PostActivity extends Activity {
	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;

	// UI controls
    private EditText editTitle;
	private EditText editBody;
	private Button buttonMakePhoto;
	private Button buttonPost;
	private Button buttonExit;
	
	/** is posting in progress */
	private boolean isPostInProgress; 

	/** post page node identifier. */
	private int nid;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);
        
        // get UI controls
        editTitle = (EditText) findViewById(R.id.editTitle);
        editBody = (EditText) findViewById(R.id.editBody);
        buttonMakePhoto = (Button) findViewById(R.id.buttonMakePhoto);
        buttonMakePhoto.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				makePhoto();
			}
		});
        
        buttonPost = (Button) findViewById(R.id.buttonPost);
        buttonPost.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				postPage();
			}
		});
        
        buttonExit = (Button) findViewById(R.id.buttonExit);
        buttonExit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				exit();
			}
		});
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
			if (resultCode != RESULT_OK) {
				CameraHelper.photoFile = null;
			}
		}
	}
	
	private void makePhoto() {
    	if (!CameraHelper.isMediaStorageAvailable()) {
    		GUIHelper.showError(this, "SD card is not available. It needed to store photos");
    		return ;
    	}
    	
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // store photo file in CamaraHelper, because this activity will 
        // be restarted when photo is made
    	CameraHelper.photoFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_IMAGE);
    	
    	intent.putExtra(MediaStore.EXTRA_OUTPUT,
                Uri.fromFile(CameraHelper.photoFile));
    	
    	startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
	}
	
	private void deletePhoto() {
		if (CameraHelper.photoFile == null)
			return ;
		
		CameraHelper.photoFile.delete();
		CameraHelper.photoFile = null;
	}
	
    private void postPage() {
    	// check if posting is in progress
    	if (isPostInProgress) {
    		return ;
    	}
    	
    	isPostInProgress = true;
    	
    	// get page title and body
    	final String title = editTitle.getText().toString();
    	final String body = editBody.getText().toString();
    	
    	// show progress dialog
    	final ProgressDialog progressDialog = ProgressDialog.show(this, "Posting", "Posting. Please, wait.", true, false);
    	    			
    	// start async task for posting to Drupal 
    	(new AsyncTask<Void, Void, Boolean>() {
    		Exception e;
    		
			@Override
			protected Boolean doInBackground(Void... params) {
				try {
					nid = DrupalConnect.getInstance().postPage(title, body);
					return true;
				}
				catch (Exception e) {
					this.e = e;
					return false;
				}
			}

			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);

				progressDialog.dismiss();
				
				if (result) {
					// if user made photo - upload it
					// if not - report that post succeeded
					if (CameraHelper.photoFile != null) {
						uploadPhoto();
					}
					else {
						GUIHelper.showMessage(PostActivity.this, "Post succeeded.", "Message");
						isPostInProgress = false;
					}
				}
				else {
					GUIHelper.showError(PostActivity.this, "Post is failed. "+e.getMessage());
					isPostInProgress = false;
				}
			}
    		
    	}).execute();
	}
    
    private void uploadPhoto() {
    	// create progress dialog
    	final ProgressDialog progressDialog = new ProgressDialog(this);
    	progressDialog.setTitle("Uploading photo");
    	progressDialog.setMessage("Uploading photo");
    	progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	progressDialog.setIndeterminate(false);
    	progressDialog.setCancelable(false);
    	progressDialog.show();
    	
    	// create async task to upload photo
    	(new AsyncTask<Void, Integer, Boolean>() {
    		Exception e;
    		
			@Override
			protected Boolean doInBackground(Void... params) {
				PhotoParams photoParams = new PhotoParams();
				photoParams.nid = nid;
				photoParams.file = CameraHelper.photoFile;
				photoParams.fileName = CameraHelper.photoFile.getName();
						
				DrupalConnect.getInstance().uploadPhoto(photoParams, new HttpProgressListener() {
					@Override
					public void sendStarted(int total) {
						publishProgress(0, total);
					}
					
					@Override
					public void sendProgress(int uploaded, int total) {
						publishProgress(uploaded, total);
					}
					
					@Override
					public void sendError(Exception ex) {
						e = ex;
						//Log.e("Post", "Upload is failed", e);
					}
					
					@Override
					public void sendDone() {
					}
				});
				return null;
			}

			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);

				progressDialog.dismiss();
							
				// check if exception is occurred during upload
				if (e == null) {
					// delete photo file
					// it must be deleted when upload is succeeded
					deletePhoto();
					
					GUIHelper.showError(PostActivity.this, "Post and upload are succeeded.");
				}
				else {
					GUIHelper.showError(PostActivity.this, "Upload is failed. "+e.getMessage());

					// delete page
					deletePage();
				}
				isPostInProgress = false;
			}

			@Override
			protected void onProgressUpdate(Integer... values) {
				super.onProgressUpdate(values);
	
				int sent = values[0];
				int total = values[1];
				
				// if this is the first call, set progress dialog max value
				if (sent == 0) {
					progressDialog.setMax(total);
				}
				progressDialog.setProgress(values[0]);
			}
    	}).execute();
    }
    
    private void deletePage() {
    	// check if there is page
    	if (nid == 0) {
    		return ; 
    	}
    	
    	(new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {
				try {
					DrupalConnect.getInstance().deletePage(nid);
					nid = 0;
				} 
				catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		}).execute();
    }
    
    private void exit() {
    	(new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {
				try {
					DrupalConnect.getInstance().logout();
				} 
				catch (Exception e) {
					e.printStackTrace();
				} 
				return null;
			}

			@Override
			protected void onPostExecute(Boolean result) {
				super.onPostExecute(result);
				
				PostActivity.this.finish();
			}
    	}).execute();
    }
}
