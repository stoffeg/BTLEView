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

import android.content.Context;
import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.elcris.btleview.R;


public class MainActivity extends AppCompatActivity implements MainActivityFragment.OnItemSelectedListener {
    private static final String TAG = "Stoffe";

    private MainActivityFragment fragment;
    private BeaconManager beaconManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        beaconManager = new BeaconManager(this);
        beaconManager.start();

        setContentView(R.layout.activity_main);

        fragment = (MainActivityFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
        fragment.setBeaconManager(beaconManager);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if( beaconManager != null ) {
            beaconManager.stop();
        }
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

    @Override
    public void onItemSelected(BeaconReading reading) {
        BeaconActivityFragment baFragment = new BeaconActivityFragment();

        baFragment.setBeaconReading(reading);
        baFragment.setBeaconManager(beaconManager);

        //getFragmentManager().beginTransaction().detach(this).add(new BeaconActivityFragment(), "someTag1").commit();
        FragmentTransaction tm = getSupportFragmentManager().beginTransaction();
        tm.replace(R.id.fragment, baFragment);
        tm.addToBackStack(null);
        tm.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        tm.commit();
    }
}
