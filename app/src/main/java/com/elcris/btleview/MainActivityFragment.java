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
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.elcris.btleview.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;


/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends ListFragment {
    private static final String TAG = "Stoffe";

    private TextView mytext;
    //private BeaconManager beaconManager;

    public MainActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setListAdapter(myAdapter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//                             Bundle savedInstanceState) {
//        View view = inflater.inflate(R.layout.beaconlist, container, false);
//        setListAdapter(myAdapter);
//        return view;
//    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        BeaconReading br = (BeaconReading) getListAdapter().getItem(position);

        ((OnItemSelectedListener) getActivity()).onItemSelected(br);

        //myAdapter.notifyDataSetInvalidated();
        //getListView().invalidate();
        Log.i(TAG,"ON CLICK");
    }

    public void setBeaconManager(BeaconManager manager) {
        //beaconManager = manager;
        manager.getBeaconDiscoveryListener().subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<BeaconReading>() { //Next
            @Override
            public void call(BeaconReading beaconReading) {
                if (beaconReading.isDeleted()) {
                    beacons.remove(beaconReading);
                } else {
                    beacons.add(beaconReading);
                }
                myAdapter.notifyDataSetChanged();
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

    ArrayList<BeaconReading> beacons = new ArrayList<BeaconReading>();

    BaseAdapter myAdapter = new BaseAdapter () {
        @Override
        public int getCount() {
            return beacons.size();
        }

        @Override
        public BeaconReading getItem(int position) {
            BeaconReading result = null;
            if( position < beacons.size() ) {
                Iterator<BeaconReading> it = beacons.iterator();
                for(int i=0 ; it.hasNext() ; i++ ) {
                    BeaconReading next = it.next();
                    if( i == position ) return next;
                }
            }

            return result;
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if( convertView == null ) {
                LayoutInflater inflater = LayoutInflater.from(MainActivityFragment.this.getActivity());
                convertView = inflater.inflate(R.layout.beaconlist, parent, false);
            }
            //TODO update

            BeaconReading br = getItem(position);
            if( br != null ) {
                TextView addr = (TextView) convertView.findViewById(R.id.btaddr);
                addr.setText(br.addr);
                TextView id = (TextView) convertView.findViewById(R.id.bid);
                StringBuilder output = new StringBuilder();
                for (int i = 0; i < br.beaconId.length(); i+=2) {
                    String str = br.beaconId.substring(i, i+2);
                    output.append((char)Integer.parseInt(str, 16));
                }
                id.setText(output.toString());
                TextView maj = (TextView) convertView.findViewById(R.id.major);
                maj.setText(br.major.toString());
                TextView min = (TextView) convertView.findViewById(R.id.minor);
                min.setText(br.minor.toString());
            }

            return convertView;
        }
    };

    public interface OnItemSelectedListener {
        public void onItemSelected(BeaconReading reading);
    }
}
