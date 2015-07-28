/**
 * Copyright 2015 Kristoffer Gronowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.elcris.btleview;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.elcris.btleview.R;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {
    private static final String TAG = "Stoffe";

    private TextView mytext;
    private ConcurrentHashMap<BeaconReading, BeaconReading> beacons = new ConcurrentHashMap<BeaconReading,BeaconReading>();

    private ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    public MainActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exec.scheduleAtFixedRate(beaconCounter,1,1, TimeUnit.SECONDS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        mytext = (TextView) view.findViewById(R.id.mytext);
        return view;
    }

    public void setFoundBeacon(String addr, byte[] adv, int rssi) {
        if( !BeaconReading.isIBeacon(adv) ) {
            //"1107ad7700c6a00099b2e2114c249" //Gimbal
            String beacon = BeaconReading.bytesToHex(adv, 0, adv.length);
            if(beacon.startsWith("1107ad7700c6a00099b2e2114c249")) return; //Do not report
            Log.d(TAG,"Rejected ADV = "+beacon



            );
            return;
        }

        BeaconReading br = new BeaconReading(addr, adv, rssi);
        synchronized (beacons) {
            BeaconReading last = beacons.get(br);
            if (last != null) {
                br.refqCnt = last.refqCnt + 1;
                br.latestFreq = last.latestFreq;
            }
            else {
                br.refqCnt = 1;
                br.latestFreq = 1;
            }
            beacons.put(br,br);
        }
        //System.out.println("Adding = " + br+ " Time - " +  System.currentTimeMillis());

        //TODO only update the new beacon.
        synchronized (beacons) {
            final StringBuilder sb = new StringBuilder();
            for(Map.Entry e : beacons.entrySet()) {
                sb.append(e.getValue().toString() + System.getProperty("line.separator"));
                //sb.append(e.getValue().toString() + " Time - " +  System.currentTimeMillis());
            }
            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mytext.setText(sb.toString());
                }
            });

        }
    }

    final private static int MAX_TIME_SEC = -20;
    final private ArrayList<BeaconReading> delete = new ArrayList<BeaconReading>(5);
    Runnable beaconCounter = new Runnable() {
        @Override
        public void run() {

            synchronized (beacons) {
                //System.out.println("RVI - tick, Size = "+beacons.size());
                delete.clear();
                for(Map.Entry<BeaconReading,BeaconReading> e : beacons.entrySet()) {
                    BeaconReading br = e.getValue();
                    //System.out.println("RVI - tick, latestFreq="+br.latestFreq+" refqCnt="+br.refqCnt);
                    if( br.refqCnt == 0 ) { //Non reported, age
                        br.latestFreq -= 1;
                    } else {
                        br.latestFreq = br.refqCnt;
                    }
                    br.refqCnt = 0;

                    if( br.latestFreq < MAX_TIME_SEC ) delete.add(br);
                    //System.out.println("RVI - tick, latestFreq="+br.latestFreq+" refqCnt="+br.refqCnt);
                }

                for(BeaconReading br:delete) {
                    beacons.remove(br);
                }
            }
        }
    };
}


class BeaconReading {
    private static final int MAX_BEACON_SIZE = 30;
    String addr;
    String beaconId;
    Integer major;
    Integer minor;
    int rssi;
    long txPow;
    int latestFreq;
    int refqCnt;

    public BeaconReading(String addr, byte[] leadv, int rssi) {
        this.addr = addr;
        beaconId = bytesToHex(leadv, 9, 25);
        //txPow = Integer.parseInt( new String(leadv, 29, 30) );
        major = Integer.valueOf( (leadv[26] & 0xff) | ((leadv[25] & 0xff) << 8) );
        minor = Integer.valueOf( (leadv[28] & 0xff) | ((leadv[27] & 0xff) << 8) );
        txPow = leadv[29];
        this.rssi = rssi;
    }

    @Override
    public boolean equals(Object o) {
        if( o == null ) return false;
        if(! (o instanceof BeaconReading ) ) return false;
        BeaconReading br = (BeaconReading) o;
        if(!addr.equals(br.addr))return false;
        if(!beaconId.equals(br.beaconId))return false;
        if(!major.equals(br.major))return false;
        if(!minor.equals(br.minor))return false;
        return true;
    }

    public double getDistance() {

    /*
     * RSSI = TxPower - 10 * n * lg(d)
     * n = 2 (in free space)
     * n = Path-Loss Exponent, ranges from 2.7 to 4.3
     *
     * d = 10 ^ ((TxPower - RSSI) / (10 * n))
     *
     *  RSSI (dBm) = -10n log10(d) + A
     */

        return Math.pow(10d, ((double) txPow - rssi) / (10 * 2));
    }

    public int getFrequency() {
        return latestFreq;
    }

    @Override
    public int hashCode() {
        return addr.hashCode();
    }

    @Override
    public String toString() {
        return "BT:"+addr+", id: "+beaconId+", M:"+major+", m:"+minor+", dist:"+getDistance()+", Freq:"+latestFreq;
    }

    public static String bytesToHex(byte[] in, int start, int stop) {
        final StringBuilder sb = new StringBuilder();
        for( int i = start ; i < stop ; i++ ) {
            sb.append(String.format("%02x", in[i]));
        }
        return sb.toString();
    }

    public static boolean isIBeacon(byte[] adv) {
        if( adv == null || adv.length < MAX_BEACON_SIZE ) return false;
        if( adv[0] != 0x02 ||  adv[1] != 0x01 || adv[3] != 0x1A) return false;
        //TODO add more checks
        return true;
    }
}
