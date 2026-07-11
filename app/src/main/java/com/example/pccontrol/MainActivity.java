package com.example.pccontrol;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

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
        //retreive config values
        SharedPreferences remote_action_config = this.getSharedPreferences("remote_action_config", Context.MODE_PRIVATE);
        SharedPreferences.Editor remote_action_config_editor = remote_action_config.edit();
        String computer_hostname = remote_action_config.getString("computer_hostname","");
        String secret_key = remote_action_config.getString("secret_key","");
        //build ui
        setContentView(R.layout.main_activity);
        EditText computer_hostname_text_entry = (EditText)findViewById(R.id.computer_hostname_text_entry);
        computer_hostname_text_entry.setText(computer_hostname);
        EditText secret_key_text_entry = (EditText)findViewById(R.id.secret_key_text_entry);
        secret_key_text_entry.setText(secret_key);
        //interactions
        Button computer_hostname_save_button = (Button)findViewById(R.id.computer_hostname_save_button);
        Button secret_key_save_button = (Button)findViewById(R.id.secret_key_save_button);
        computer_hostname_save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                remote_action_config_editor.putString("computer_hostname",computer_hostname_text_entry.getText().toString());
                remote_action_config_editor.apply();
            }
        });
        secret_key_save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                remote_action_config_editor.putString("secret_key",secret_key_text_entry.getText().toString());
                remote_action_config_editor.apply();
            }
        });

    }
    public static Context getAppContext(){
        return app.getApplicationContext();
    }
}
