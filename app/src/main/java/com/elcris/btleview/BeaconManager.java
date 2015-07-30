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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;

public class BeaconManager {
    private ArrayList<Subscriber<? super BeaconReading>> discovery = new ArrayList<Subscriber<? super BeaconReading>>();
    private ArrayList<Subscriber<? super BeaconReading>> reading = new ArrayList<Subscriber<? super BeaconReading>>();

    private BluetoothAdapter adapter;

    private ConcurrentHashMap<BeaconReading, BeaconReading> beacons = new ConcurrentHashMap<BeaconReading,BeaconReading>();

    private ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    private Context context;

    private static final String TAG = "Stoffe";

    public BeaconManager(Context ctx) {
        final BluetoothManager bluetoothManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bluetoothManager.getAdapter();

        Log.i(TAG, "Scan mode : " + adapter.getScanMode());

        if (!adapter.isEnabled()) {
            adapter.enable();
        }

        context = ctx;
    }

    public void start() {
        //        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
//            ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
//            //ScanFilter filter = new ScanFilter.Builder().setDeviceAddress("00:02:72:C8:A8:1B").build();
//            ScanFilter filter = new ScanFilter.Builder().build();
//
//            filters.add(filter);
//            ScanSettings settings = new ScanSettings.Builder().setReportDelay(0).setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
//            //ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
//            //ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
//            scanner.startScan(filters,settings,sc);
//
//        } else {
        adapter.startLeScan(cb);
//        }

        exec.scheduleAtFixedRate(beaconCounter, 1, 1, TimeUnit.SECONDS);
        Log.i(TAG, "Started BeaconManager ---");
    }

    public void stop() {

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
//            scanner.stopScan(sc);
//
//        } else {
        adapter.stopLeScan(cb);
//        }

        exec.shutdownNow();
        Log.i(TAG, "Stopped BeaconManager ---");
    }

    BluetoothAdapter.LeScanCallback cb = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            //Log.d(TAG,"Rec = " + bytesToHex(scanRecord));
            //System.out.println("RVI Rec = " + bytesToHex(scanRecord));
            if( !BeaconReading.isIBeacon(scanRecord) ) {
                //"1107ad7700c6a00099b2e2114c249" //Gimbal
                String beacon = BeaconReading.bytesToHex(scanRecord, 0, scanRecord.length);
                if(beacon.startsWith("1107ad7700c6a00099b2e2114c249")) return; //Do not report
                Log.d(TAG,"Rejected ADV = "+beacon);
                return;
            }

            BeaconReading br = new BeaconReading(device.getAddress(), scanRecord, rssi);
            synchronized (beacons) {
                BeaconReading last = beacons.get(br);
                if (last != null) {
                    //Existing Beacon

                    br.refqCnt = last.refqCnt + 1;
                    br.latestFreq = last.latestFreq;

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

                    br.refqCnt = 1;
                    br.latestFreq = 1;

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
    };

    //    ScanCallback sc = new ScanCallback() {
//        @Override
//        public void onScanResult(int callbackType, ScanResult result) {
//            handleResult(result);
//        }
//
//        @Override
//        public void onBatchScanResults(List<ScanResult> results) {
//            System.out.println("RVI 5.x batch :"+results.size());
//            for(ScanResult result:results) {
//                handleResult(result);
//            }
//        }
//
//        private void handleResult(ScanResult result) {
//            System.out.println("RVI 5.x Rec = " + bytesToHex(result.getScanRecord().getBytes()));
//            MainActivity.this.fragment.setFoundBeacon(result.getDevice().getAddress(),
//                    result.getScanRecord().getBytes(), result.getRssi());
//        }
//    };

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
                        br.latestFreq = br.refqCnt;
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
    }
