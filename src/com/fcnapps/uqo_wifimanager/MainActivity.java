package com.fcnapps.uqo_wifimanager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.app.Activity;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class MainActivity extends Activity {
	public static final String USER_INFO = "UserInfo";
	public EditText txtUsername;
	public EditText txtPassword;
	public EditText txtMsgSysteme;
	public TextView lblWifiName;
	public Button btnLogin;
	public Button btnRefresh;
	public Button btnSave;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Retrouver les �l�ments du GUI de l'activit�
		txtUsername = (EditText) findViewById(R.id.txtUsername);
		txtPassword = (EditText) findViewById(R.id.txtPassword);
		txtMsgSysteme = (EditText) findViewById(R.id.txtMsgSysteme);
		lblWifiName = (TextView) findViewById(R.id.lblWifiName);
		btnLogin = (Button) findViewById(R.id.btnLogin);
		btnRefresh = (Button) findViewById(R.id.btnRefresh);
		btnSave = (Button) findViewById(R.id.btnSave);

		// Permettre de faire une connection sur le thread principal de
		// l'application. TODO: Fix g�le si la connection �choue...
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
				.permitAll().build();
		StrictMode.setThreadPolicy(policy);

		// Rendre le log de msg syst�me readonly
		txtMsgSysteme.setKeyListener(null);

		// Charger les informations sauvegard�es
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		String username = settings.getString("username", "");
		String password = settings.getString("password", "");
		txtUsername.setText(username);
		txtPassword.setText(password);

		// D�tecter le WIFI actuel et tenter une connection si n�cessaire
		refreshWifiLbl();
		if (!isConnected()) {
			attemptConnection();
		} else 
		{
			logMsgSys("Vous avez d�j� acc�s � l'internet");
		}

		// Bouton Refresh
		btnRefresh.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				refreshWifiLbl();
			}
		});

		// Bouton Login
		btnLogin.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				attemptConnection();
			}
		});

		// Bouton Save
		btnSave.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				saveUserInfo();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	/**
	 * @param username
	 * @param password
	 * @return String avec le r�sultat du login
	 */
	public void connectUQOWifi(String username, String password) {
		// Pr�parer le POST
		HttpClient httpclient = new DefaultHttpClient();
		String urlPost = "https://sansfil.uqo.ca/auth/index.html/u";
		HttpPost httppost = new HttpPost(urlPost);

		try {
			// Donn�es � envoyer
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
			nameValuePairs.add(new BasicNameValuePair("user", username));
			nameValuePairs.add(new BasicNameValuePair("password", password));
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

			// Faire le POST
			HttpResponse response = httpclient.execute(httppost);

		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
	}

	/**
	 * @return String avec le nom du r�seau sans fil sur lequel l'appareil est
	 *         connect� (SSID)
	 */
	private String getCurrentWifiSSID() {
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		return wifiInfo.getSSID();
	}

	/**
	 * @return String avec l'heure pr�cise (pour log)
	 */
	private String getLogTimeString() {
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
		Calendar cal = Calendar.getInstance();
		return sdf.format(cal.getTime());
	}

	/**
	 * @param message
	 */
	private void logMsgSys(String message) {
		txtMsgSysteme.setText(getLogTimeString() + ": " + message + "\n\n"
				+ txtMsgSysteme.getText().toString());
	}

	/**
	 * 
	 */
	private void refreshWifiLbl() {
		String wifiName = getCurrentWifiSSID();
		lblWifiName.setText("Connect� � " + wifiName);
		logMsgSys("Refresh; connect� � " + wifiName);
		isConnected();
	}

	/**
	 * 
	 */
	private void attemptConnection() {
		String username = txtUsername.getText().toString();
		String password = txtPassword.getText().toString();

		if (getCurrentWifiSSID().equals("\"UQO\"")
				|| getCurrentWifiSSID().equals("UQO")) {
			connectUQOWifi(username, password);
			
			if (isConnected())
			{
				logMsgSys("Login; Succ�s!");
			} else 
			{
				logMsgSys("Login; �chec :(");
			}
		} else {
			logMsgSys("Login; �chec, vous devez �tre sur le r�seau \"UQO\"");
		}
	}

	/**
	 * 
	 */
	private void saveUserInfo() {
		// Sauvegarder les informations de l'utilisateur
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		SharedPreferences.Editor editor = settings.edit();

		String username = txtUsername.getText().toString();
		String password = txtPassword.getText().toString();

		editor.putString("username", username);
		editor.putString("password", password);
		editor.commit();

		// G�n�r� une variable avec le mot de passe cach� sauf
		// 1er/dernier caract�re
		String partialPwd = "";
		for (char ch : password.toCharArray()) {
			if (partialPwd.length() == 0
					|| partialPwd.length() == (password.length() - 1)) {
				partialPwd += ch;
			} else {
				partialPwd += "*";
			}
		}

		logMsgSys("Sauvegarde; \n\tUN:" + username + "\n\tPW:" + partialPwd);
	}

	/**
	 * @return
	 */
	private boolean isConnected() {
		try {
			URL url = new URL("http://www.google.ca");
			HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
			urlc.setRequestProperty("User-Agent", "Android Application");
			urlc.setRequestProperty("Connection", "close");
			urlc.setConnectTimeout(1000 * 3); // mTimeout is in seconds
			urlc.connect();
			if (urlc.getResponseCode() == 200) {
				return true;
			}
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
}
