package com.fcnapps.uqo_wifimanager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

public class BackgroundService extends Service {
	private static final String USER_INFO = "UserInfo";
	// This is the object that receives interactions from clients. See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new LocalBinder();
	public Boolean isConnected = false;
	private BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			NetworkInfo currentNetworkInfo = (NetworkInfo) intent
					.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

			// Si l'�v�nement re�u est une connexion �tablie et que c'est le r�seau sans-fil UQO, s'authentifier
			if (currentNetworkInfo.isConnected() && (getWifiSSID().equals("\"UQO\"") || getWifiSSID().equals("UQO"))) {
				attemptAuthentification();
			}
		}
	};

	// Lorsque le service d�marre, commencer � monitorer l'�tat de la connection
	@Override
	public void onCreate() {
		this.registerReceiver(this.mConnReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}

	// Garder le service actif sauf si explicitement demand� de le fermer
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		BackgroundService getService() {
			return BackgroundService.this;
		}
	}

	/**
	 * T�che asynchrone qui essaie une connexion vers un URL
	 */
	private class PingWebpageTask extends AsyncTask<String, Void, Boolean> {
		@Override
		protected Boolean doInBackground(String... urls) {
			try {
				URL url = new URL(urls[0]);
				HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
				urlc.setRequestProperty("User-Agent", "Android Application");
				urlc.setRequestProperty("Connection", "close");
				urlc.setConnectTimeout(1000 * 5); // Abandonner apr�s 5 secondes
				urlc.connect();
				if (urlc.getResponseCode() == 200) {
					return true;
				}
			} catch (MalformedURLException e) {

			} catch (IOException e) {

			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (!result) {
				isConnected = false;
				Toast.makeText(getApplicationContext(),
						"L'authentification au r�seau sans-fil UQO semble avoir �chou�", Toast.LENGTH_SHORT).show();
			} else {
				isConnected = true;
				Toast.makeText(getApplicationContext(), "L'authentification au r�seau sans-fil UQO est r�ussie",
						Toast.LENGTH_SHORT).show();
			}
		}
	}

	/**
	 * T�che asynchrone qui tentera de soumettre les informations de login de
	 * l'utilisateur � la page d'authentification
	 */
	private class connectUQOWifiTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... _void) {
			// Pr�parer le POST
			HttpClient httpclient = new DefaultHttpClient();
			String urlPost = "https://sansfil.uqo.ca/auth/index.html/u";
			HttpPost httppost = new HttpPost(urlPost);

			try {
				// Donn�es � envoyer
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
				nameValuePairs.add(new BasicNameValuePair("user", getUsername()));
				nameValuePairs.add(new BasicNameValuePair("password", getPassword()));
				httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

				// Faire le POST
				httpclient.execute(httppost);
			} catch (ClientProtocolException e) {

			} catch (IOException e) {

			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			checkConnectionStatus();
		}
	}

	/**
	 * V�rifie de fa�on asynchrone si on a acc�s � internet en testant si
	 * google.ca est accessible
	 * 
	 * @return
	 */
	private Void checkConnectionStatus() {
		PingWebpageTask task = new PingWebpageTask();
		task.execute(new String[] {
			"http://www.google.ca"
		});
		return null;
	}

	/**
	 * Tentera une authentification au r�seau sans-fil "UQO"
	 */
	public void attemptAuthentification() {
		if (getWifiSSID().equals("\"UQO\"") || getWifiSSID().equals("UQO")) {
			if (getUsername().isEmpty() || getPassword().isEmpty()) {
				Toast.makeText(
						getApplicationContext(),
						"Gestionnaire de connexion sans-fil UQO: vous devez sp�cifier votre nom d'usager et mot de passe",
						Toast.LENGTH_LONG).show();
			} else {
				if (!isConnected) {
					connectUQOWifiTask task = new connectUQOWifiTask();
					task.execute(new Void[] {});
				}
			}
		} else {
			Toast.makeText(getApplicationContext(), "Vous n'�tes pas connect� au r�seau sans-fil de l'UQO",
					Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Retourne une string avec le nom du r�seau sans fil sur lequel l'appareil
	 * est connect� (SSID)
	 * 
	 * @return SSID
	 */
	private String getWifiSSID() {
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		return wifiInfo.getSSID();
	}

	/**
	 * Retourne le username contenu dans le EditText correspondant
	 * 
	 * @return
	 */
	private String getUsername() {
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		return settings.getString("username", "");
	}

	/**
	 * Retourne le password contenu dans le EditText correspondant
	 * 
	 * @return
	 */
	private String getPassword() {
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		return settings.getString("password", "");
	}

}
