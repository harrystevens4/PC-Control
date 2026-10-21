package com.example.pccontrol;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

public class RemoteActionWorker extends Worker {
    public RemoteActionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("RemoteActionWorker", "initiating remote connection");
        Context context = this.getApplicationContext();
        int action = this.getInputData().getInt("action",-1);
        if (action == -1) {
            Log.e("RemoteActionWorker","no action specified");
            return Result.failure();
        }
        //====== get computer hostname and secret key ======
        SharedPreferences remote_action_config = context.getSharedPreferences("remote_action_config", MODE_PRIVATE);
        String computer_hostname = remote_action_config.getString("computer_hostname","");
        if (Objects.equals(computer_hostname,"")) {
            Notifier.notifyError(context,"computer hostname not set");
            return Result.failure();
        }
        String secret_key_base64 = remote_action_config.getString("secret_key","");
        if (Objects.equals(secret_key_base64,"")) {
            Notifier.notifyError(context,"secret key not set");
            return Result.failure();
        }
        byte[] secret_key = Base64.getDecoder().decode(secret_key_base64);
        //====== open socket ======
        Socket socket;
        OutputStream socket_output;
        InputStream socket_input;
        try {
            InetAddress address;
            try {
                address = QuickDiscover.discover(computer_hostname,"UNLOCKFPHONE");
            } catch (IOException e) {
                SharedPreferences sharedPreferences = context.getSharedPreferences("remote_action_config",MODE_PRIVATE);
                if (sharedPreferences.getBoolean("useMdnsFallback",true)) {
                    Log.e("RemoteActionsWidget","quick discover failed (mdns fallback): "+e);
                    address = java.net.InetAddress.getByName(computer_hostname + ".local");
                }else {
                    throw new UnknownHostException("no method for resolving hostname available");
                }
            }
            socket = new Socket(address, 19532);
            socket_output = socket.getOutputStream();
            socket_input = socket.getInputStream();
        } catch (UnknownHostException e) {
            Log.e("RemoteActionWorker", "Could not connect to host");
            Notifier.notifyError(context,"Couldn't connect to host "+computer_hostname);
            return Result.failure();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        try {
            ByteBuffer buffer = ByteBuffer.allocate(64);
            //====== auth_request ======
            buffer.putLong(action);
            socket_output.write(buffer.array(), 0, 8);
            //====== auth_challenge ======
            socket_input.read(buffer.array(), 0, 64);
            //====== auth_response ======
            ByteBuffer bytes_to_digest = ByteBuffer.allocate(64 + secret_key.length);
            bytes_to_digest.put(buffer.array(), 0, 64);
            bytes_to_digest.put(secret_key);
            MessageDigest md = MessageDigest.getInstance("sha256");
            byte[] digest = md.digest(bytes_to_digest.array());
            socket_output.write(digest);
            //====== request_status ======
            byte[] status_buffer = {0};
            socket_input.read(status_buffer, 0, 1);
            int status = Byte.toUnsignedInt(status_buffer[0]);
            Log.d("RemoteActionWorker", "status returned of " + status);
            if (status != 0) {
                String text_status = "";
                if (status == 1) {
                    text_status = "authentication failed";
                } else if (status == 2){
                    text_status = "command not implemented";
                } else {
                    text_status = "code " + status;
                }
                Notifier.notifyError(context,"Error performing action: " + text_status);
            }
            //====== close socket ======
        } catch (SocketException e){
            Notifier.notifyError(context,"Socket transport error: "+e.getMessage());
            return Result.failure();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException ignored){
            Log.e("RemoteActionWorker","sha256 not available");
            return Result.failure();
        } finally {
            try {
                socket.close();
            } catch (IOException ignored){}
        }
        Log.d("RemoteActionWorker", "remote unlock complete");
        Notifier.notifyInfoForDuration(context,"Request Status","remote action successful", Duration.ofSeconds(10));
        return Result.success();
    }
}
