package com.fcnapps.uqo_wifimanager;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Francois Charette Nguyen
 */
public class MainActivity extends Activity
{
    private static final String TAG = "UQO-WifiManager-GUI";
    private static final String USER_INFO = "UserInfo";
	private static boolean haveWebAcces = false;

	// GUI
	private EditText txtUsername;
	private EditText txtPassword;
	private TextView lblConnectionStatus;
	private Button btnLogin;
	private ProgressBar progressBar;
	// Background stuff
	private Boolean mIsBound;
	private BackgroundService mBackgroundService;
	private ServiceConnection mConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			mBackgroundService = ((BackgroundService.LocalBinder) service).getService();
		}

		public void onServiceDisconnected(ComponentName className)
		{
			mBackgroundService = null;
		}
	};

	// Se lie au service en arrière plan
	private void doBindService()
	{
		bindService(new Intent(getApplicationContext(), BackgroundService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	// Se délie du service en arrière plan
	private void doUnbindService()
	{
		if (mIsBound)
		{
			unbindService(mConnection);
			mIsBound = false;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		Log.d(TAG, "Application démarrée (onCreate)");
        super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		// Retrouver les éléments du GUI de l'activité
		txtUsername = (EditText) findViewById(R.id.txtUsername);
		txtPassword = (EditText) findViewById(R.id.txtPassword);
		lblConnectionStatus = (TextView) findViewById(R.id.lblConnectionStatus);
		btnLogin = (Button) findViewById(R.id.btnLogin);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		progressBar.setVisibility(View.INVISIBLE);

		// Charger les informations sauvegardées
		loadUserInfo();

		// OnClick du bouton authentifier
		btnLogin.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
            // Empécher de spammer l'action à intervalle < 5 secondes
            mBackgroundService.attemptAuthentification();
            lockLoginButton(3000);
            checkWebAccess();
			}
		});

		// OnChange du champs username
		txtUsername.addTextChangedListener(new TextWatcher()
		{
			public void afterTextChanged(Editable s)
			{
				saveUserInfo();
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after)
			{
			}

			public void onTextChanged(CharSequence s, int start, int before, int count)
			{
			}
		});

		// OnChange du champs password
		txtPassword.addTextChangedListener(new TextWatcher()
		{
			public void afterTextChanged(Editable s)
			{
				saveUserInfo();
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after)
			{
			}

			public void onTextChanged(CharSequence s, int start, int before, int count)
			{
			}
		});
	}

	private void lockLoginButton(final int duration)
	{
		// Afficher la barre d'attente pour la durée spécifiée
		btnLogin.setEnabled(false);
		progressBar.setVisibility(View.VISIBLE);

		new CountDownTimer(duration + 51, duration / 100)
		{
			int percent = 0;

			public void onTick(long millisUntilFinished)
			{
				percent += 1;
				progressBar.setProgress(percent);
			}

			public void onFinish()
			{
				progressBar.setVisibility(View.INVISIBLE);
			}
		}.start();

		// Désactiver le bouton pour la durée spécifiée
		new Thread(new Runnable()
		{

			@Override
			public void run()
			{
				try
				{
					Thread.sleep(duration);
				} catch (InterruptedException e)
				{
					e.printStackTrace();
				}

				MainActivity.this.runOnUiThread(new Runnable()
				{

					@Override
					public void run()
					{
						btnLogin.setEnabled(true);
					}
				});
			}
		}).start();
	}

	@Override
	public void onResume()
	{
        Log.d(TAG, "Application en focus (onResume)");

        super.onResume();

        stopService(new Intent(this, BackgroundService.class));
        startService(new Intent(this, BackgroundService.class));
        doBindService();

        checkWebAccess();
	}

	private void checkWebAccess()
	{
		PingWebpageTask task = new PingWebpageTask();
		task.execute("http://www.google.ca");
	}

	@Override
	public void onPause()
	{
        Log.d(TAG, "Application perdu focus (onPause)");
        super.onPause();
		saveUserInfo();
	}

	@Override
	public void onDestroy()
	{
        Log.d(TAG, "Application détruite (onDestroy)");
        super.onDestroy();
		doUnbindService();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	/**
	 * Charge le username + password de l'utilisateur
	 */
	private void loadUserInfo()
	{
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		String username = settings.getString("username", "");
		String password = settings.getString("password", "");
		txtUsername.setText(username);
		txtPassword.setText(password);
	}

	/**
	 * Sauvegarde le username + password de l'utilisateur
	 */
	private void saveUserInfo()
	{
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		SharedPreferences.Editor editor = settings.edit();

		editor.putString("username", txtUsername.getText().toString());
		editor.putString("password", txtPassword.getText().toString());
		editor.commit();
	}


	/**
	 * Tâche asynchrone qui essaie une connexion vers un URL
	 */
	private class PingWebpageTask extends AsyncTask<String, Void, Boolean>
	{
		@Override
		protected Boolean doInBackground(String... urls)
		{
			try
			{
				URL url = new URL(urls[0]);
				HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
				urlc.setRequestProperty("User-Agent", "Android Application");
				urlc.setRequestProperty("Connection", "close");
				urlc.setConnectTimeout(1000 * 5); // Abandonner après 5 secondes
				urlc.connect();
				if (urlc.getResponseCode() == 200)
				{
					return true;
				}
			} catch (MalformedURLException ignored)
			{

			} catch (IOException ignored)
			{

			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
			haveWebAcces = result;
			updateLblConnectionStatus();
		}
	}

	private void updateLblConnectionStatus()
	{
		if (haveWebAcces)
		{
			if (mBackgroundService.connectedToUQO())
			{
				lblConnectionStatus.setText("Vous avez accès à l'internet via le réseau UQO");
				lblConnectionStatus.setTextColor(Color.parseColor("#55AA55"));
			}
			else
			{
				lblConnectionStatus.setText("Vous avez accès à l'internet via le réseau " + mBackgroundService.getWifiSSID());
				lblConnectionStatus.setTextColor(Color.parseColor("#000000"));
			}
		}
		else
		{
			lblConnectionStatus.setText("Vous n'avez pas accès à l'internet");
			lblConnectionStatus.setTextColor(Color.parseColor("#FF5555"));
		}
	}
}
