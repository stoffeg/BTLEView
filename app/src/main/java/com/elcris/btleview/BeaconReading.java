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

import java.io.Serializable;

class BeaconReading implements Serializable {
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

    public boolean isDeleted() {
        return ( rssi == Integer.MIN_VALUE && txPow == Long.MIN_VALUE );
    }

    public void setDeleted() {
        rssi = Integer.MIN_VALUE;
        txPow = Long.MIN_VALUE;
    }
}
