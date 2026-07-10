package com.example.pccontrol;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;

public class RemoteAction extends Service {
    private ExecutorService thread_pool = null;

    @Override
    public IBinder onBind(Intent intent){
        return null;
    }
    @Override
    public void onCreate(){
        this.thread_pool = newSingleThreadExecutor();
        Log.d("RemoteAction","RemoteAction service created");
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d("RemoteAction","RemoteAction for "+" requested");
        thread_pool.execute(new RemoteUnlock());
        return START_NOT_STICKY;
    }
}

class RemoteUnlock implements Runnable {
    @Override
    public void run(){
        Log.d("RemoteAction","initiating remote unlock");
    }
}