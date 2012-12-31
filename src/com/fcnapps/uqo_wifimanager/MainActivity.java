package com.fcnapps.uqo_wifimanager;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * @author Francois Charette Nguyen
 * 
 */
public class MainActivity extends Activity {
	private static final String USER_INFO = "UserInfo";
	// GUI
	private EditText txtUsername;
	private EditText txtPassword;
	private TextView lblWifiName;
	private Button btnLogin;
	// Background stuff
	private Long lastConnectionAttempt = Long.MIN_VALUE;
	private Boolean mIsBound;
	private BackgroundService mBackgroundService;
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mBackgroundService = ((BackgroundService.MyBinder) service).getService();
		}

		public void onServiceDisconnected(ComponentName className) {
			mBackgroundService = null;
		}
	};

	private void doBindService() {
		bindService(new Intent(getApplicationContext(), BackgroundService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	private void doUnbindService() {
		if (mIsBound) {
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		// Retrouver les éléments du GUI de l'activité
		txtUsername = (EditText) findViewById(R.id.txtUsername);
		txtPassword = (EditText) findViewById(R.id.txtPassword);
		lblWifiName = (TextView) findViewById(R.id.lblWifiName);
		btnLogin = (Button) findViewById(R.id.btnLogin);

		// Charger les informations sauvegardées
		loadUserInfo();

		// Listener du bouton Login
		btnLogin.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Empecher de spammer l'action à intervalle < 5 secondes
				if (lastConnectionAttempt + 5000 < System.currentTimeMillis()) {
					mBackgroundService.attemptConnection();
					lastConnectionAttempt = System.currentTimeMillis();
				}
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		Intent service = new Intent(getBaseContext(), BackgroundService.class);
		getBaseContext().startService(service);
		doBindService();
	}

	@Override
	public void onPause() {
		super.onPause();
		saveUserInfo();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	/**
	 * Charge le username + password de l'utilisateur
	 */
	private void loadUserInfo() {
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		String username = settings.getString("username", "");
		String password = settings.getString("password", "");
		txtUsername.setText(username);
		txtPassword.setText(password);
	}

	/**
	 * Sauvegarde le username + password de l'utilisateur
	 */
	private void saveUserInfo() {
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		SharedPreferences.Editor editor = settings.edit();

		editor.putString("username", getUsername());
		editor.putString("password", getPassword());
		editor.commit();
	}

	/**
	 * Retourne le password contenu dans le EditText correspondant
	 * 
	 * @return
	 */
	private String getPassword() {
		return txtPassword.getText().toString();
	}

	/**
	 * Retourne le username contenu dans le EditText correspondant
	 * 
	 * @return
	 */
	private String getUsername() {
		return txtUsername.getText().toString();
	}
}
