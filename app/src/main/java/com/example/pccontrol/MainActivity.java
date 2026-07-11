package com.example.pccontrol;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;

public class MainActivity extends Activity {
    private static Activity app;
    @Override
    public void onCreate(Bundle saved_instance_state){
        super.onCreate(saved_instance_state);
        MainActivity.app = this;
        //setup notification channel
        NotificationChannel notification_channel = new NotificationChannel("ErrorChannel","Errors", NotificationManager.IMPORTANCE_HIGH);
        notification_channel.enableVibration(true);
        notification_channel.enableLights(true);
        NotificationManager notification_manager = this.getSystemService(NotificationManager.class);
        notification_manager.createNotificationChannel(notification_channel);
        //build ui
        setContentView(R.layout.main_activity);
    }
    public static Context getAppContext(){
        return app.getApplicationContext();
    }
}
