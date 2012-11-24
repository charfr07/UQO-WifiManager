package com.fcnapps.uqo_wifimanager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {
    public static final String USER_INFO   = "UserInfo";
    public EditText            txtUsername;
    public EditText            txtPassword;
    public EditText            txtMsgSysteme;
    public TextView            lblWifiName;
    public Button              btnLogin;
    public Button              btnRefresh;
    public Boolean             isConnected = false;

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
        attemptConnection();

        // Listener du bouton Refresh
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                refreshWifiLbl();
            }
        });

        // Listener du bouton Login
        btnLogin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                attemptConnection();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        saveUserInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
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
     * Retourne une string avec l'heure formatt�e (HH:mm:ss.SSS)
     * 
     * @return HH:mm:ss.SSS
     */
    private String getLogTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        Calendar cal = Calendar.getInstance();
        return sdf.format(cal.getTime());
    }

    /**
     * Log un message dans la boite de messages syst�me
     * 
     * @param message
     */
    private void logMsgSys(String message) {
        txtMsgSysteme.setText(getLogTimeString() + ": " + message + "\n" + txtMsgSysteme.getText().toString());
    }

    /**
     * Raffraichit le libell� du statut de la connection wifi
     */
    private void refreshWifiLbl() {
        ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();

        if (wifi == State.CONNECTED) {
            String wifiName = getWifiSSID();
            lblWifiName.setTextColor(Color.rgb(0, 0, 0));
            lblWifiName.setText("Connect� � " + wifiName);
            logMsgSys("Refresh; connect� � " + wifiName);
        }
        else {
            lblWifiName.setTextColor(Color.rgb(200, 0, 0));
            lblWifiName.setText("Wifi d�connect�/inactif");
            logMsgSys("Refresh; wifi d�connect�/inactif");
        }
    }

    /**
     * Tentera une connection au r�seau sans-fil "UQO"
     */
    private void attemptConnection() {
        if (getWifiSSID().equals("\"UQO\"") || getWifiSSID().equals("UQO")) {
            connectUQOWifiTask task = new connectUQOWifiTask();
            task.execute(new Void[] {});
        }
        else {
            logMsgSys("Login; �chec, vous devez �tre sur le r�seau \"UQO\"");
        }
    }

    /**
     * Sauvegarde le username + password de l'utilisateur
     */
    private void saveUserInfo() {
        //
        SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.putString("username", getUsername());
        editor.putString("password", getPassword());
        editor.commit();
    }

    /**
     * V�rifie de fa�on asynchrone si on a acc�s � internet en testant si
     * google.ca est accessible
     * 
     * @return
     */
    private boolean isConnected() {
        PingWebpageTask task = new PingWebpageTask();
        task.execute(new String[] { "http://www.google.ca" });
        return isConnected;
    }

    /**
     * Retourne le username contenu dans le EditText correspondant
     * 
     * @return
     */
    private String getUsername() {
        return txtUsername.getText().toString();
    }

    /**
     * Retourne le password contenu dans le EditText correspondant
     * 
     * @return
     */
    private String getPassword() {
        return txtPassword.getText().toString();
    }

    private class PingWebpageTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
                urlc.setRequestProperty("User-Agent", "Android Application");
                urlc.setRequestProperty("Connection", "close");
                urlc.setConnectTimeout(1000 * 4); // Abandonner apr�s 4 secondes
                urlc.connect();
                if (urlc.getResponseCode() == 200) {
                    return true;
                }
            }
            catch (MalformedURLException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                logMsgSys("Vous ne semblez pas avoir acc�s � l'internet. Une tentative de connection sera faite.");
                attemptConnection();
            }
            else {
                logMsgSys("Vous avez acc�s � l'internet");
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
                HttpResponse response = httpclient.execute(httppost);
            }
            catch (ClientProtocolException e) {
                logMsgSys(e.getMessage());
            }
            catch (IOException e) {
                logMsgSys(e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // V�rifier si on est connect� une fois l'information de login
            // soumise � la page d'authentification
            isConnected();
        }
    }
}
