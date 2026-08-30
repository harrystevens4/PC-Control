package com.example.pccontrol;

import android.provider.ContactsContract;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class QuickDiscover {
    static int HOSTNAME_MAX_LEN = 254;
    static int PORT = 15006;
    static int SERVICE_LEN = 12;
    static int TIMEOUT_MS = 1000;
    static InetAddress discover(String hostname, String service) throws IOException, UnknownHostException {
        try (DatagramSocket socket = new DatagramSocket()){
            //====== setup socket ======
            socket.setBroadcast(true);
            final InetAddress broadcastAddress = Inet4Address.getByAddress(new byte[] {(byte)255,(byte)255,(byte)255,(byte)255});
            socket.setSoTimeout(TIMEOUT_MS);
            //====== discover packet ======
            //create buffer
            final int discoverPacketLength = HOSTNAME_MAX_LEN+1+SERVICE_LEN;
            byte[] discoverPacketByteBuffer = new byte[discoverPacketLength];
            DatagramPacket discoverPacket = new DatagramPacket(discoverPacketByteBuffer,discoverPacketLength,broadcastAddress,PORT);
            //fill in hostname
            if (hostname != null){
                byte[] hostnameBytes = hostname.getBytes();
                System.arraycopy(hostnameBytes, 0, discoverPacketByteBuffer, 0, Math.min(hostname.length(), HOSTNAME_MAX_LEN));
                //fill in the hostname length
                Array.setByte(discoverPacketByteBuffer,HOSTNAME_MAX_LEN,(byte)Math.min(hostname.length(), HOSTNAME_MAX_LEN));
            }
            //fill in service
            if (service != null){
                byte[] serviceBytes = service.getBytes();
                System.arraycopy(serviceBytes, 0, discoverPacketByteBuffer, HOSTNAME_MAX_LEN+1, Math.min(service.length(), SERVICE_LEN));
            }
            //send
            socket.send(discoverPacket);
            //====== receive an answer ======
            //allocate buffer
            final int responsePacketLength = HOSTNAME_MAX_LEN+1+SERVICE_LEN+16+1;
            byte[] responsePacketByteBuffer = new byte[responsePacketLength];
            DatagramPacket responsePacket = new DatagramPacket(responsePacketByteBuffer,responsePacketLength);
            //receive packet
            try {
                socket.receive(responsePacket);
            } catch (SocketTimeoutException e){
                throw new UnknownHostException("Host with service "+service+" and hostname "+hostname+" did not respond within timeout");
            }
            return responsePacket.getAddress();
        }
    }
}