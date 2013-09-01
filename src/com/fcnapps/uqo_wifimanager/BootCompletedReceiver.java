package com.fcnapps.uqo_wifimanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Classe utilitaire pour lancer le service lorsque le système émet
// un signal comme de quoi le système est démarré
public class BootCompletedReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Intent service = new Intent(context, BackgroundService.class);
		context.startService(service);
	}
}