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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;

public class BeaconManager {
    private static final int FREQ_MULTI = 2; //Multiply 1 = Hz 2 = *2 3 = *3
    private ArrayList<Subscriber<? super BeaconReading>> discovery = new ArrayList<Subscriber<? super BeaconReading>>();
    private ArrayList<Subscriber<? super BeaconReading>> reading = new ArrayList<Subscriber<? super BeaconReading>>();

    private BluetoothAdapter adapter;

    private ConcurrentHashMap<BeaconReading, BeaconReading> beacons = new ConcurrentHashMap<BeaconReading,BeaconReading>();

    private ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    private ScanCallback sc; //Only Android 5.x
    private AdvertiseCallback advcb;
    private BluetoothLeAdvertiser advertiser = null;

    private BeaconTransmitter beaconTransmitter;
    private Context context;

    private static final String TAG = "Stoffe";

    public BeaconManager(Context ctx) {
        final BluetoothManager bluetoothManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bluetoothManager.getAdapter();

        context = ctx;

        Log.i(TAG, "Scan mode : " + adapter.getScanMode());

        if (!adapter.isEnabled()) {
            adapter.enable();
        }
    }

    public void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
            ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
            //ScanFilter filter = new ScanFilter.Builder().setDeviceAddress("00:02:72:C8:A8:1B").build();
            ScanFilter filter = new ScanFilter.Builder().build();

            filters.add(filter);
            ScanSettings settings = new ScanSettings.Builder().setReportDelay(0).setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            //ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
            //ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
            sc = new LollipopScanCallback();
            scanner.startScan(filters, settings, sc);
        } else {
            adapter.startLeScan(cb);
        }

        exec.scheduleAtFixedRate(beaconCounter, 1, 1, TimeUnit.SECONDS);
        Log.i(TAG, "Started BeaconManager ---");
    }

    public void stop() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
            scanner.stopScan(sc);

            stopAdvert();

        } else {
            adapter.stopLeScan(cb);
        }

        exec.shutdownNow();
        Log.i(TAG, "Stopped BeaconManager ---");
    }

    public void advertize(String id, int major, int minor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Adv
            advertiser = adapter.getBluetoothLeAdvertiser();
            AdvertiseSettings.Builder advSettingsBuilder = new AdvertiseSettings.Builder().
                    setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
            advSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
            advSettingsBuilder.setConnectable(false);
            advSettingsBuilder.setTimeout(0);

            AdvertiseData.Builder advDataBuilder = new AdvertiseData.Builder();
            // { (byte)0x15, (byte)0x02 };
            ByteBuffer manufacturerData = ByteBuffer.allocate(24);
            manufacturerData.put((byte)0x02);
            manufacturerData.put((byte)0x15);
            //BigInteger bigInt = new BigInteger(ParcelUuid.fromString("12345678-1234-5678-1234-012345678910").toString());
            //BigInteger bigInt = new BigInteger(ParcelUuid.fromString("12345678123456781234012345678910").toString());
            //String hexString = bigInt.toString(16);
            //String hexString = "1234567890123456";
            manufacturerData.put(id.getBytes());
            manufacturerData.putShort((short) major); //Major
            manufacturerData.putShort((short) minor); //Minor
            manufacturerData.put((byte) 0xc5);

            Log.d(TAG, " Raw : " + bytesToHex(manufacturerData.array()));
            //0x0000 = Ericsson ...
            advDataBuilder.addManufacturerData(0x004c, manufacturerData.array());
            advDataBuilder.setIncludeDeviceName(false);
            advDataBuilder.setIncludeTxPowerLevel(false);

            advcb = new LollipopAdvertiseCallback();

            Log.i(TAG,"Adv = "+advertiser+", sett : "+advSettingsBuilder+", data : "+advDataBuilder+", cb = "+advcb );


            advertiser.startAdvertising(advSettingsBuilder.build(), advDataBuilder.build(), advcb);
        } else {
            Log.w(TAG, "Not supported BLTE ADV!");
        }
    }

    public void startAltBeacon(String id, int major, int minor){
        Beacon beacon = new Beacon.Builder()
                .setId1(id)
                .setId2(""+major)
                .setId3(""+minor)
                .setManufacturer(0x004c)
                .setTxPower(-59)
                .setDataFields(Arrays.asList(new Long[]{0l}))
                .build();
        beaconTransmitter = new BeaconTransmitter(context, new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconTransmitter.startAdvertising(beacon);
    }

    public void stopAdvert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if( advertiser != null && advcb != null ) advertiser.stopAdvertising(advcb);
        }
    }

    public void stopAltBeacon() {
        if( beaconTransmitter != null ) beaconTransmitter.stopAdvertising();
    }

    BluetoothAdapter.LeScanCallback cb = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG,"4.x Rec = " + bytesToHex(scanRecord));
            handleScan(device.getAddress(), scanRecord, rssi);
        }
    };


    private void handleScan(String address, byte[] scanRecord, int rssi) {
        //System.out.println("RVI Rec = " + bytesToHex(scanRecord));
        if( !BeaconReading.isIBeacon(scanRecord) ) {
            //"1107ad7700c6a00099b2e2114c249" //Gimbal
            String beacon = BeaconReading.bytesToHex(scanRecord, 0, scanRecord.length);
            if(beacon.startsWith("1107ad7700c6a00099b2e2114c249")) return; //Do not report
            //Log.d(TAG,"Rejected ADV = "+beacon);
            return;
        }

        BeaconReading br = new BeaconReading(address, scanRecord, rssi);
        synchronized (beacons) {
            BeaconReading last = beacons.get(br);
            if (last != null) {
                //Existing Beacon

                br.merge(last);

                beacons.put(br, br);

                for (Subscriber<? super BeaconReading> sub : reading) {
                    if (!sub.isUnsubscribed()) sub.onNext(br);
                    else {
                        Log.w(TAG, "Unsubscribed Readings - " + sub);
                        //TODO remove
                    }
                }
            }
            else {
                //New Beacon

                Log.d(TAG,"New Beacon : " + discovery.size());

                if(discovery.size() == 0) return; //Don't track before one attaches
                beacons.put(br, br);

                for (Subscriber<? super BeaconReading> sub : discovery) {
                    if (!sub.isUnsubscribed()) sub.onNext(br);
                    else {
                        Log.w(TAG, "Unsubscribed Discovery - " + sub);
                        //TODO remove
                    }
                }
            }
        }
    }


    public static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    //Beacon Cleanup Thread
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
                    if (br.refqCnt == 0) { //Non reported, age
                        br.latestFreq -= 1;
                    } else {
                        br.latestFreq = br.refqCnt*FREQ_MULTI;
                    }
                    br.refqCnt = 0;

                    if (br.latestFreq < MAX_TIME_SEC) {
                        delete.add(br);
                        for (Subscriber<? super BeaconReading> sub : discovery) {
                            if (!sub.isUnsubscribed()) {
                                br.setDeleted();
                                sub.onNext(br);
                            }
                        }
                        //System.out.println("RVI - tick, latestFreq="+br.latestFreq+" refqCnt="+br.refqCnt);
                    }
                }

                for(BeaconReading br:delete) {
                    beacons.remove(br);
                }

                //Move to Sub
                //Fire Adapter
                // myAdapter.notifyDataSetChanged();

            }
        }
    };

    public Observable<BeaconReading> getBeaconDiscoveryListener() {
        Observable<BeaconReading> obs = Observable.create(new Observable.OnSubscribe<BeaconReading>() {
            @Override
            public void call(Subscriber<? super BeaconReading> observer) {
                if(!observer.isUnsubscribed()) {
                    discovery.add(observer);
                }
            }
        });
        return obs;
    }

    public Observable<BeaconReading> getBeaconReadingListener() {
        Observable<BeaconReading> obs = Observable.create(new Observable.OnSubscribe<BeaconReading>() {
            @Override
            public void call(Subscriber<? super BeaconReading> observer) {
                if(!observer.isUnsubscribed()) {
                    reading.add(observer);
                }
            }
        });
        return obs;
    }

    //Use the new API fo 5.x
    @TargetApi(21)
    class LollipopScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            System.out.println("RVI 5.x batch :" + results.size());
            for(ScanResult result:results) {
                handleResult(result);
            }
        }

        private void handleResult(ScanResult result) {
            Log.d(TAG, "5.x Rec = " + BeaconManager.bytesToHex(result.getScanRecord().getBytes()));
            handleScan(result.getDevice().getAddress(),
                    result.getScanRecord().getBytes(), result.getRssi());
        }
    };

    @TargetApi(21)
    class LollipopAdvertiseCallback extends AdvertiseCallback {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.e(TAG,"Success :"+settingsInEffect.toString());
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG,"Failed : +"+errorCode);
            super.onStartFailure(errorCode);
        }
    }
}
