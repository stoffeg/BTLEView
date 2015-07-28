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
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.elcris.btleview.R;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = "Stoffe";
    private BluetoothAdapter adapter;
    private MainActivityFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bluetoothManager.getAdapter();

        if (!adapter.isEnabled()) {
            adapter.enable();
        }

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

        Log.i(TAG,"Scan mode : "+adapter.getScanMode());

        setContentView(R.layout.activity_main);

        fragment = (MainActivityFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
//            scanner.stopScan(sc);
//
//        } else {
            adapter.stopLeScan(cb);
//        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    BluetoothAdapter.LeScanCallback cb = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            //System.out.println("RVI Rec = " + bytesToHex(scanRecord));
            MainActivity.this.fragment.setFoundBeacon(device.getAddress(), scanRecord, rssi);
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

}
