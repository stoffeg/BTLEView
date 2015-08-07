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

import android.util.Log;

import java.io.Serializable;
import java.util.Arrays;

class BeaconReading implements Serializable {
    private static final String TAG = "Stoffe";
    private static final int MAX_BEACON_SIZE = 30;
    String addr;
    String beaconId;
    Integer major;
    Integer minor;
    int rssi;
    long txPow;
    int adIndex = 1; //If only FF

    //Copy over
    int latestFreq =1 ;
    int refqCnt = 1;
    int freqMax = 1;
    double prop = 2.0;

    private static final int MAX_DIST_SIZE = 32;
    private static final int MIN_DIST_SIZE = 5; //Before it does not even try
    double[] distbuff = null;
    long dIndex = 0;
    boolean dFull = false;

    public BeaconReading(String addr, byte[] leadv, int rssi) {
        this.addr = addr;
        adIndex = getFFIndex(leadv);
        beaconId = bytesToHex(leadv, adIndex + 5, adIndex + 21);
        //txPow = Integer.parseInt( new String(leadv, 29, 30) );
        major = Integer.valueOf( (leadv[adIndex+22] & 0xff) | ((leadv[adIndex+21] & 0xff) << 8) );
        minor = Integer.valueOf( (leadv[adIndex+24] & 0xff) | ((leadv[adIndex+23] & 0xff) << 8) );
        txPow = leadv[adIndex+25];
        this.rssi = rssi;
        //Log.d(TAG, "Created beacon : "+this.toString()+" IS : "+isIBeacon(leadv));
    }

    private static int getFFIndex(byte[] leadv) {
        int i = 0;

        while (i < (MAX_DIST_SIZE-1)) {
            int len = leadv[i];
            if (leadv[i + 1] == (byte)0xFF) {
                Log.d(TAG, "Found FF AD structure at index : " + (i + 1) + " len = " + len);
                return i+1;
            }
            else Log.d(TAG, "AD structure type : " + leadv[i + 1] + " len = " + len);
            i += len +1;
        }
        return -1;
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

    /**
 * RSSI = TxPower - 10 * n * lg(d)
 * n = 2 (in free space)
 * n = Path-Loss Exponent, ranges from 2.7 to 4.3
 *
 * d = 10 ^ ((TxPower - RSSI) / (10 * n))
 *
 *  RSSI (dBm) = -10n log10(d) + A
 */
    public double getDistance() {

        double result = 0;
        if(dFull) {
            int remove = (int) Math.ceil(freqMax*0.1); // 10%
            double[] copy = Arrays.copyOf(distbuff,freqMax);
            Arrays.sort(copy);
            int j = 0;
            for(int i = remove ; i < freqMax-remove ; i++) {
                result += copy[i];
                j++;
            }
            //Log.d(TAG,"J="+j+" Sum="+result+" result/j="+result/j+" freqMax-2r="+(freqMax-(2*remove)));
            result = result/j;
        } else {
            result = Math.pow(10d, ((double) txPow - rssi) / (10 * prop));
        }

        return result;
    }

    public void merge(BeaconReading last) {
        refqCnt = last.refqCnt + 1;
        latestFreq = last.latestFreq;
        prop = last.prop;
        distbuff = last.distbuff;
        dIndex = ++last.dIndex;
        dFull = last.dFull;

        if(latestFreq > last.freqMax ) {
            freqMax = latestFreq;
            Log.i(TAG, "Updating Max : " + latestFreq + " for " + toString());
            //Check if time to store dist buffer

            dIndex = 0;
            dFull = false;

            if( distbuff == null && latestFreq > MIN_DIST_SIZE ) {
                distbuff = new double[MAX_DIST_SIZE];
                Log.d(TAG,"Created distbuff array");
            }
        }
        else freqMax = last.freqMax; //just copy

        if( distbuff != null ) { //Add value to index.
            distbuff[(int)dIndex%freqMax] = Math.pow(10d, ((double) txPow - rssi) / (10 * prop));
            //Log.d(TAG, "dIndex="+dIndex);
            if( (!dFull) && (dIndex >= freqMax) ) {
                dFull = true;
                Log.d(TAG,"Full distbuff array");
            }
        }
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
        return "BT:"+addr+", id: "+beaconId+", M:"+major+", m:"+minor+", dist:"+getDistance()+", Freq:"+latestFreq+", Max:"+freqMax;
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
        int adi = getFFIndex(adv);
        if( adi == -1 ) return false;
        Log.d(TAG, "Comp - "+adv[adi+1]+" "+adv[adi+2]+" "+adv[adi+3]+" "+adv[adi+4]+" ");
        //This line is for Apple iBeacons, TODO change for Altbeacons
        if( adv[adi+1] != 0x4C ||  adv[adi+2] != 0x00 || adv[adi+3] != 0x02 || adv[adi+4] != 0x15) return false;
        //TODO add more checks
        return true;
    }

    public boolean isDeleted() {
        return ( rssi == Integer.MIN_VALUE && txPow == Long.MIN_VALUE );
    }

    public void setDeleted() {
        rssi = Integer.MIN_VALUE;
        txPow = Long.MIN_VALUE;
    }

    public void setPropagation(double p) {
        prop = p;
    }

    public double getPropagation(){ return prop; }
}
