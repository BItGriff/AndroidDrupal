package com.bitgriff.androiddrupal;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.bitgriff.helpers.GUIHelper;
import com.bitgriff.helpers.PrefsHelper;
import com.bitgriff.http.DrupalConnect;

/**
 * Activity than asks user to enter login and password.
 * This activity shown first, when application is started. 
 * 
 * @author Moskvichev Andrey V.
 *
 */
public class LoginActivity extends Activity {
	// UI controls
	private EditText editUsername;
	private EditText editPassword;
	private CheckBox checkRemember;
	private Button buttonOk;
	private Button buttonCancel;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // get UI controls
        editUsername = (EditText) findViewById(R.id.editUsername);
        editPassword = (EditText) findViewById(R.id.editPassword);
        checkRemember = (CheckBox) findViewById(R.id.checkRemember);
        
        buttonOk = (Button) findViewById(R.id.buttonOk);
        buttonOk.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onButtonOk();
			}
		});
        
        buttonCancel = (Button) findViewById(R.id.buttonCancel);
        buttonCancel.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onButtonCancel();
			}
		});
        
        PrefsHelper.setup(this, "AndroidDrupal");
        loadAuthData();
    }

    @Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	/**
	 * Called to button "ok" click.
	 */
	public void onButtonOk() {
		final String username = editUsername.getText().toString();
		final String password = editPassword.getText().toString();
		
		// string buffer for errors
		StringBuilder errors = null;
		
		// check user name
		if (username.trim().length() == 0 || !username.matches("\\w+")) {
			if (errors == null)
				errors = new StringBuilder();
			errors.append("Invalid user name\n");
		}
		
		// check password
		if (password.trim().length() == 0 || !password.matches("\\w+")) {
			if (errors == null)
				errors = new StringBuilder();
			errors.append("Invalid password\n");
		}	
		
		// 
		if (errors != null) {
			GUIHelper.showError(this, errors.toString());
			return ;
		}
		
		// show login progress dialog
		final ProgressDialog dlg = ProgressDialog.show(this, "Logging in", "Logging in. Please wait.", true, true);
		
		// create an async task to perform login
		(new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				try {
					DrupalConnect.getInstance().login(username, password);
					return "OK";
				}
				catch (Exception ex) {
					return ex.getMessage();
				}
			}

			@Override
			protected void onPostExecute(String result) {
				dlg.dismiss();
				
				if (result.equals("OK")) {
					saveAuthData();
					
					// switch to MainActivity
					Intent intent = new Intent(LoginActivity.this, PostActivity.class);
					startActivity(intent);
					finish();
				}
				else {
					GUIHelper.showError(LoginActivity.this, "Login is failed. " + result);
				}
			}
		}).execute();
    }

	/**
	 * Called on button "cancel" click.
	 */
    public void onButtonCancel() {
    	this.finish();
    }

    /**
     * Load authorization data from preferences.
     */
    private void loadAuthData() {
    	SharedPreferences prefs = PrefsHelper.getPreferences();
    
    	String username = prefs.getString("username", null);
    	String password = prefs.getString("password", null);
    	
    	if (username != null && password != null) {
    		editUsername.setText(username);
        	editPassword.setText(password);
        	
        	checkRemember.setChecked(true);
    	}
    	else {
    		editUsername.setText("");
        	editPassword.setText("");
        	
        	checkRemember.setChecked(false);
    	}
    }
    
    /**
     * Save authorization data to preferences.
     */
	private void saveAuthData() {
		SharedPreferences prefs = PrefsHelper.getPreferences();
		
		SharedPreferences.Editor editor = prefs.edit();
		
		if (checkRemember.isChecked()) {
			editor.putString("username", editUsername.getText().toString());
			editor.putString("password", editPassword.getText().toString());
		}
		else {
			editor.remove("username");
			editor.remove("password");
		}
		editor.commit();
	}

}
