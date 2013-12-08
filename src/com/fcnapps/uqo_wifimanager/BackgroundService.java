package com.fcnapps.uqo_wifimanager;

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
import android.util.Log;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BackgroundService extends Service {
    private static final String TAG = "UQO-WifiManager-Service";
    private Boolean isConnected = false;
    private static final String USER_INFO = "UserInfo";
    private final IBinder mBinder = new LocalBinder();

    private final BroadcastReceiver mConnReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // Si l'événement reçu est une connexion établie et que c'est le réseau sans-fil UQO, s'authentifier
            if (connectionExists() && connectedToUQO()) {
                Log.d(TAG, "Événement réseau survenu. Processus d'authentification lancé");
                attemptAuthentification();
            } else
            {
                Log.d(TAG, "Événement réseau survenu, mais aucune action car pas réseau UQO");
            }
        }
    };

    private boolean connectionExists() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null) {
                return ni.isConnected();
            }
        }
        return false;
    }

    // Lorsque le service démarre, commencer à monitorer l'état de la connection
    @Override
    public void onCreate() {
        Log.d(TAG, "Service créé");
        super.onCreate();
        this.registerReceiver(this.mConnReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        checkConnectionStatus();
    }

    @Override
    public void onDestroy()
    {
        Log.d(TAG, "Service détruit");
    }

    // Garder le service actif sauf si explicitement demandé de le fermer
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service démarré");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Vérifie de façon asynchrone si on a accès à internet en testant si
     * google.ca est accessible
     */
    private Void checkConnectionStatus() {
        PingWebpageTask task = new PingWebpageTask();
        task.execute("http://www.google.ca");
        return null;
    }

    /**
     * Tentera une authentification au réseau sans-fil "UQO"
     */
    public void attemptAuthentification() {
        if (connectedToUQO()) {
            if (getUsername().isEmpty() || getPassword().isEmpty()) {
                Toast.makeText(getApplicationContext(), getString(R.string.UsrPwNonSpecifie), Toast.LENGTH_LONG).show();
            } else if (!isConnected) {
                taskAuthentifyToUQONetwork task = new taskAuthentifyToUQONetwork();
                task.execute();
            }
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.WifiPasUQO), Toast.LENGTH_LONG).show();
        }
    }

    public boolean connectedToUQO() {
        return getWifiSSID().equals("\"UQO\"") || getWifiSSID().equals("UQO");
    }

    /**
     * Retourne une string avec le nom du réseau sans fil sur lequel l'appareil
     * est connecté (SSID)
     *
     * @return SSID
     */
    public String getWifiSSID() {
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
     * Tâche asynchrone qui essaie une connexion vers un URL
     */
    private class PingWebpageTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
                urlc.setRequestProperty("User-Agent", "Android Application");
                urlc.setRequestProperty("Connection", "close");
                urlc.setConnectTimeout(1000 * 5); // Abandonner après 5 secondes
                urlc.connect();
                if (urlc.getResponseCode() == 200) {
                    return true;
                }
            } catch (MalformedURLException ignored) {

            } catch (IOException ignored) {

            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (connectedToUQO()) {
                if (!result) {
                    isConnected = false;
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.failed_login), Toast.LENGTH_SHORT).show();
                } else {
                    isConnected = true;
                    Toast.makeText(getApplicationContext(), getString(R.string.success_login),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Tâche asynchrone qui tentera de soumettre les informations de login de
     * l'utilisateur à la page d'authentification
     */
    private class taskAuthentifyToUQONetwork extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... _void) {
            // Préparer le POST
            HttpClient httpclient = new DefaultHttpClient();
            String urlPost = "https://sansfil.uqo.ca/auth/index.html/u";
            HttpPost httppost = new HttpPost(urlPost);

            try {
                // Données à envoyer (user+password)
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
}
