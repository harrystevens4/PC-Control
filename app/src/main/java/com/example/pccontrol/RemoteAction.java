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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        thread_pool.execute(new RemoteUnlock(this));
        return START_NOT_STICKY;
    }
}

class RemoteUnlock implements Runnable {
    private Service service = null;
    public RemoteUnlock(Service caller){
        this.service = caller;
    }
    @Override
    public void run() {
        Log.d("RemoteAction", "initiating remote unlock");
        String computer_hostname = "harry-desktop.local";
        byte[] secret_key = "awesome test secret key".getBytes();
        Socket socket;
        OutputStream socket_output;
        InputStream socket_input;
        //====== open socket ======
        try {
            socket = new Socket(computer_hostname, 19532);
            socket_output = socket.getOutputStream();
            socket_input = socket.getInputStream();
        } catch (UnknownHostException e) {
            Log.e("RemoteAction", "Could not find host");
            return;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            //====== auth_request ======
            long action = 0; //unlock
            buffer.putLong(action);
            socket_output.write(buffer.array(),0,8);
            //====== auth_challenge ======
            socket_input.read(buffer.array(),0,64);
            //====== auth_response ======
            ByteBuffer bytes_to_digest = ByteBuffer.allocate(64+secret_key.length);
            bytes_to_digest.put(buffer.array(),0,64);
            bytes_to_digest.put(secret_key);
            MessageDigest md = MessageDigest.getInstance("sha256");
            byte[] digest = md.digest(bytes_to_digest.array());
            socket_output.write(digest);
            //====== request_status ======
            byte[] status_buffer = {0};
            socket_input.read(status_buffer,0,1);
            int status = Byte.toUnsignedInt(status_buffer[0]);
            Log.d("RemoteAction","status returned of "+status);
            //====== close socket ======
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException ignored){
            Log.e("RemoteAction","sha256 not available");
            return;
        } finally {
            try {
                socket.close();
            } catch (IOException ignored){}
        }
        Log.d("RemoteAction", "remote unlock complete");
    }
}