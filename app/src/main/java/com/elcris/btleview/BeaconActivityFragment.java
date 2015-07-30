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

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * A placeholder fragment containing a simple view.
 */
public class BeaconActivityFragment extends Fragment {
    private static final String TAG = "Stoffe";

    private TextView addr;
    private TextView id;
    private TextView major;
    private TextView minor;
    private TextView distance;
    private TextView fequency;
    private TextView propagation;
    private SeekBar seekBar;

    private BeaconReading br = null;
    private Subscription sub = null;

    public BeaconActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_beacon, container, false);
        Intent i = getActivity().getIntent();

        if( i != null && i.hasExtra("beacon") ) {
            br = (BeaconReading) i.getSerializableExtra("beacon");
        }

        addr = (TextView) v.findViewById(R.id.btaddr);
        id = (TextView) v.findViewById(R.id.bid);
        major = (TextView) v.findViewById(R.id.major);
        minor = (TextView) v.findViewById(R.id.minor);
        distance = (TextView) v.findViewById(R.id.distance);
        fequency = (TextView) v.findViewById(R.id.fequency);
        propagation = (TextView) v.findViewById(R.id.propagation);
        seekBar = (SeekBar) v.findViewById(R.id.seekBar);

        if(br != null) {
            updateReading(br);
        }

        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(sub != null) sub.unsubscribe();
    }

    public void setBeaconReading( BeaconReading reading ) {
        br = reading;
    }

    public void setFoundBeacon(String addr, byte[] adv, int rssi) {
        Log.i("Stoffe", "Other Fragment SET !!!");
    }

    public void setBeaconManager(BeaconManager manager) {
        if(sub != null) sub.unsubscribe();

        sub = manager.getBeaconReadingListener().subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<BeaconReading>() { //Next
            @Override
            public void call(BeaconReading beaconReading) {
                //Log.d(TAG, "Reading! : "+beaconReading);
                if (beaconReading != null && beaconReading.equals(br)) {
                    //Log.d(TAG, "Equals! : "+beaconReading);
                    //updateReading(br);
                    br = beaconReading;
                    BeaconActivityFragment.this.getFragmentManager().beginTransaction()
                            .detach(BeaconActivityFragment.this).attach(BeaconActivityFragment.this)
                            .commit();
                }
            }
        }, new Action1<Throwable>() { //Error
            @Override
            public void call(Throwable throwable) {
                Log.i(TAG, "Stream Error!", throwable);
            }
        }, new Action0() { //Complete
            @Override
            public void call() {
                Log.i(TAG, "Stream Done!");
            }
        });
    }

    protected void updateReading(BeaconReading beaconReading) {
        addr.setText(beaconReading.addr);
        id.setText(beaconReading.beaconId);
        major.setText(""+beaconReading.major);
        minor.setText(""+beaconReading.minor);
        distance.setText(""+beaconReading.getDistance());
        fequency.setText(""+beaconReading.getFrequency());
        propagation.setText("3");//Todo
    }
}
