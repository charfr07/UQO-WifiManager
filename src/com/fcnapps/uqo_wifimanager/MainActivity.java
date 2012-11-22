package com.fcnapps.uqo_wifimanager;

import java.io.IOException;
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		final EditText txtUsername = (EditText) findViewById(R.id.txtUsername);
		final EditText txtPassword = (EditText) findViewById(R.id.txtPassword);
		final EditText txtMsgSysteme = (EditText) findViewById(R.id.txtMsgSysteme);
		final TextView lblWifiName = (TextView) findViewById(R.id.lblWifiName);
		final Button btnLogin = (Button) findViewById(R.id.btnLogin);
		final Button btnRefresh = (Button) findViewById(R.id.btnRefresh);
		final Button btnSave = (Button) findViewById(R.id.btnSave);

		// Permettre de faire une connection sur le thread principal de
		// l'application
		// Gelera si la connection echoue...
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		// Rendre le log de msg système readonly
		txtMsgSysteme.setKeyListener(null);

		// Charger les informations sauvegardées
		SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
		String username = settings.getString("username", "");
		String password = settings.getString("password", "");
		txtUsername.setText(username);
		txtPassword.setText(password);

		// Détecter le WIFI actuel
		lblWifiName.setText(getCurrentWifiSSID());

		/*
		 * Bouton Refresh
		 */
		btnRefresh.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String wifiName = getCurrentWifiSSID();
				lblWifiName.setText(wifiName);

				txtMsgSysteme.setText(getLogTimeString() + ": refresh; connecté à " + wifiName + "\n\n"
						+ txtMsgSysteme.getText().toString());
			}
		});

		/*
		 * Bouton Login
		 */
		btnLogin.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String username = txtUsername.getText().toString();
				String password = txtPassword.getText().toString();

				if (getCurrentWifiSSID().equals("\"UQO\"")) {
					connectUQOWifi(username, password);

					txtMsgSysteme.setText(getLogTimeString() + ": login; tentative de login \n\n"
							+ txtMsgSysteme.getText().toString());
				} else {
					txtMsgSysteme.setText(getLogTimeString()
							+ ": login; échec, vous devez être sur le réseau \"UQO\"\n\n"
							+ txtMsgSysteme.getText().toString());
				}

			}
		});

		/*
		 * Bouton Save
		 */
		btnSave.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Sauvegarder les informations de l'utilisateur
				SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
				SharedPreferences.Editor editor = settings.edit();

				String username = txtUsername.getText().toString();
				String password = txtPassword.getText().toString();

				editor.putString("username", username);
				editor.putString("password", password);
				editor.commit();

				String passwordSemiHidden = "";
				for (char ch : password.toCharArray()) {
					if (passwordSemiHidden.length() == 0 || passwordSemiHidden.length() == (password.length() - 1)) {
						passwordSemiHidden += ch;
					} else {
						passwordSemiHidden += "*";
					}
				}

				txtMsgSysteme.setText(getLogTimeString() + ": sauvegarde; \n\tUN:" + username + "\n\tPW:"
						+ passwordSemiHidden + "\n\n" + txtMsgSysteme.getText().toString());
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
	 * @return String avec le résultat du login
	 */
	public void connectUQOWifi(String username, String password) {
		// Préparer le POST
		HttpClient httpclient = new DefaultHttpClient();
		String urlPost = "https://sansfil.uqo.ca/auth/index.html/u";
		HttpPost httppost = new HttpPost(urlPost);

		try {
			// Données à envoyer
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
	 * @return String avec le nom du réseau sans fil sur lequel l'appareil est
	 *         connecté (SSID)
	 */
	private String getCurrentWifiSSID() {
		WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		return wifiInfo.getSSID();
	}

	/**
	 * @return String avec l'heure précise (pour log)
	 */
	private String getLogTimeString() {
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
		Calendar cal = Calendar.getInstance();
		return sdf.format(cal.getTime());
	}
}
