package com.fcnapps.uqo_wifimanager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
import android.os.CountDownTimer;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * @author Francois Charette Nguyen
 * 
 */
public class MainActivity extends Activity {
    public static final String USER_INFO   = "UserInfo";
    public CheckBox            chkAutoLogin;
    public EditText            txtUsername;
    public EditText            txtPassword;
    public EditText            txtMsgSysteme;
    public TextView            lblWifiName;
    public Button              btnLogin;
    private Timer              autoUpdate;
    public Boolean             isConnected = false;
    public ProgressBar         progressBar;
    public CountDownTimer      progressBarTimer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrouver les éléments du GUI de l'activité
        chkAutoLogin = (CheckBox) findViewById(R.id.chkAutoLogin);
        txtUsername = (EditText) findViewById(R.id.txtUsername);
        txtPassword = (EditText) findViewById(R.id.txtPassword);
        txtMsgSysteme = (EditText) findViewById(R.id.txtMsgSysteme);
        lblWifiName = (TextView) findViewById(R.id.lblWifiName);
        btnLogin = (Button) findViewById(R.id.btnLogin);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        // Rendre le log de msg système readonly
        txtMsgSysteme.setKeyListener(null);

        // Charger les informations sauvegardées
        SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
        Boolean autologin = settings.getBoolean("autologin", true);
        String username = settings.getString("username", "");
        String password = settings.getString("password", "");
        chkAutoLogin.setChecked(autologin);
        txtUsername.setText(username);
        txtPassword.setText(password);

        // Listener du bouton Login
        btnLogin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                attemptConnection();
            }
        });

        // Listener du checkbox Auto-Login
        chkAutoLogin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startAutoLogin();
                }
                else {
                    autoUpdate.cancel();
                    progressBarTimer.cancel();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        startAutoLogin();
    }

    @Override
    public void onPause() {
        autoUpdate.cancel();
        progressBarTimer.cancel();
        super.onPause();
        saveUserInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    /**
     * Démarre une boucle qui tentera de se connecter au wifi de l'UQO à chaque
     * 10 secondes (incluant la seconde 0)
     */
    private void startAutoLogin() {
        autoUpdate = new Timer();
        autoUpdate.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    public void run() {
                        checkConnectionStatus();
                        if (getAutoLogin()) {
                            attemptConnection();
                        }

                        progressBarTimer = new CountDownTimer(10000, 200) {
                            public void onTick(long millisUntilFinished) {
                                progressBar.incrementProgressBy(1);
                                int dtotal = (int) ((10200 - millisUntilFinished) / (double) 10000 * 100);
                                progressBar.setProgress(dtotal);
                            }

                            public void onFinish() {
                            }
                        }.start();
                    }
                });
            }
        }, 0, 10000); // Réessayer aux 10 secondes
    }

    /**
     * Log un message dans la boite de messages système
     * 
     * @param message
     */
    private void logMsgSys(String message) {
        txtMsgSysteme.setText(getLogTimeString() + ": " + message);
    }

    /**
     * Raffraichit le libellé du statut de la connexion wifi et le status de la
     * connectivité internet si le wifi est actif
     */
    private void refreshWifiLbl() {
        ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        State wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();

        if (wifi == State.CONNECTED) {
            String wifiName = getWifiSSID();
            lblWifiName.setText("Connecté à " + wifiName);

            if (isConnected) {
                lblWifiName.setTextColor(Color.rgb(130, 180, 100));
                lblWifiName.append("\n avec accès internet");

                if (wifiName.equals("UQO") || wifiName.equals("\"UQO\"")) {
                    logMsgSys("Vous êtes déjà connecté au réseau UQO. Vous pouvez quitter l'application");
                }
            }
            else {
                lblWifiName.setTextColor(Color.rgb(255, 200, 0));
                lblWifiName.append("\n sans accès internet");
            }
        }
        else {
            lblWifiName.setTextColor(Color.rgb(200, 0, 0));
            lblWifiName.setText("Wifi déconnecté/inactif");
        }
    }

    /**
     * Tentera une connexion au réseau sans-fil "UQO"
     */
    private void attemptConnection() {
        if (getWifiSSID().equals("\"UQO\"") || getWifiSSID().equals("UQO")) {
            if (getUsername().isEmpty() || getPassword().isEmpty()) {
                logMsgSys("La tentative de connexion n'a pas été effectuée. Vous devez spécifier votre nom d'usager et mot de passe");
            }
            else {
                if (!isConnected) {
                    connectUQOWifiTask task = new connectUQOWifiTask();
                    task.execute(new Void[] {});
                }
                else {
                    logMsgSys("Vous êtes déjà connecté au réseau UQO. Vous pouvez quitter l'application");
                }
            }
        }
        else {
            logMsgSys("La tentative de connexion n'a pas été effectuée. Vous devez être sur le réseau \"UQO\"");
        }
    }

    /**
     * Sauvegarde le username + password de l'utilisateur
     */
    private void saveUserInfo() {
        SharedPreferences settings = getSharedPreferences(USER_INFO, 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.putString("username", getUsername());
        editor.putString("password", getPassword());
        editor.putBoolean("autologin", chkAutoLogin.isChecked());
        editor.commit();
    }

    /**
     * Vérifie de façon asynchrone si on a accès à internet en testant si
     * google.ca est accessible
     * 
     * @return
     */
    private Void checkConnectionStatus() {
        PingWebpageTask task = new PingWebpageTask();
        task.execute(new String[] { "http://www.google.ca" });
        return null;
    }

    /**
     * Retourne une string avec le nom du réseau sans fil sur lequel l'appareil
     * est connecté (SSID)
     * 
     * @return SSID
     */
    private String getWifiSSID() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return wifiInfo.getSSID();
    }

    /**
     * Retourne une string avec l'heure formattée (HH:mm:ss.SSS)
     * 
     * @return HH:mm:ss.SSS
     */
    private String getLogTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        return sdf.format(cal.getTime());
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

    /**
     * Retourne la valeur "checked" du CheckBox auto-login
     * 
     * @return
     */
    private Boolean getAutoLogin() {
        return chkAutoLogin.isChecked();
    }

    private class PingWebpageTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
                urlc.setRequestProperty("User-Agent", "Android Application");
                urlc.setRequestProperty("Connection", "close");
                urlc.setConnectTimeout(1000 * 3); // Abandonner après 3 secondes
                urlc.connect();
                if (urlc.getResponseCode() == 200) {
                    return true;
                }
            }
            catch (MalformedURLException e) {

            }
            catch (IOException e) {

            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                isConnected = false;
            }
            else {
                isConnected = true;
            }
            refreshWifiLbl();
        }
    }

    /**
     * Tâche asynchrone qui tentera de soumettre les informations de login de
     * l'utilisateur à la page d'authentification
     */
    private class connectUQOWifiTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... _void) {
            // Préparer le POST
            HttpClient httpclient = new DefaultHttpClient();
            String urlPost = "https://sansfil.uqo.ca/auth/index.html/u";
            HttpPost httppost = new HttpPost(urlPost);

            try {
                // Données à envoyer
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
                nameValuePairs.add(new BasicNameValuePair("user", getUsername()));
                nameValuePairs.add(new BasicNameValuePair("password", getPassword()));
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                // Faire le POST
                // HttpResponse response = httpclient.execute(httppost);
                httpclient.execute(httppost);
            }
            catch (ClientProtocolException e) {

            }
            catch (IOException e) {

            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // Vérifier si on est connecté une fois l'information de login
            // soumise à la page d'authentification
            checkConnectionStatus();
        }
    }
}
