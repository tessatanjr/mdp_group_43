//name space of the class
package com.example.mdp_group_43;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

//fragment means its a screen/section of ui that can be swapped in and out of activity
public class BluetoothSetUp extends Fragment {
//used as tags for debugging messages
    private static final String TAG = "Bluetooth Debug";
//tracks connection status
    private String connStatus;
//the android systems bluetooth controller
    BluetoothAdapter mBluetoothAdapter;
//lists for discovered / already paired devices
    public ArrayList<BluetoothDevice> mNewBTDevices = new ArrayList<>();
    public ArrayList<BluetoothDevice> mPairedBTDevices = new ArrayList<>();
//custom list adapters to show devices in ListView
    public DeviceListAdapter mNewDeviceListAdapter;
    public DeviceListAdapter mPairedDeviceListAdapter;
//UI references
    public static TextView connStatusTextView;
    ListView lvNewDevices;  // otherDevicesListView
    ListView lvPairedDevices;   // pairedDevicesListView
    Button connectBtn;
    Button btnSearch;
    ProgressDialog myDialog;

//arrays of required runtime permissions
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };
    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };

//used to persist connection status between app sessions
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

//custom service for handling actual BT connection
    BluetoothConnectionService mBluetoothConnection;
//standard UUID for serial port profile
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
//mBTDevice is the selected bluetooth device
    public static BluetoothDevice mBTDevice;

//handles auto connection if bluetooth disconnects
    boolean retryConnection = false;
    Handler reconnectionHandler = new Handler();
    Context mContext;
//    public BluetoothSetUp(Context context) {
//
//        this.mContext = context;
//
//    }

//defines a task to try reconnecting to the last device every 5s if disconnected
    Runnable reconnectionRunnable = new Runnable() {
        @Override
        public void run() {
            // Magic here
            try {
                if (!BluetoothConnectionService.BluetoothConnectionStatus) {
                    showLog("Reconnecting...");
                    startBTConnection(mBTDevice, MY_UUID);
                    updateStatus("Reconnection Success");

                }
                reconnectionHandler.removeCallbacks(reconnectionRunnable);
                retryConnection = false;
            } catch (Exception e) {
                showLog("Reconnection failed");
                e.printStackTrace();
                updateStatus("Failed to reconnect, trying in 5 second");
            }
        }
    };

//called when fragment is created
                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                }

//in fragments, entry point for building UI is onCreateView()
//inflates the bluetooth.xml layout, initializes UI elements, sets up bluetooth discovery pairing + connection logic, registers multiple broadcast receivers for bluetooth events
                @Override
                public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
                {
                    // loads bluetooth.xml and creates the UI
                    View root = inflater.inflate(R.layout.bluetooth, container, false);
                    // get shared preferences
                    sharedPreferences = getActivity().getSharedPreferences("Shared Preferences",
                            Context.MODE_PRIVATE);

                    // gets screen width and height
                    DisplayMetrics dm = new DisplayMetrics();
                    getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

                    //initialise UI elements, gets ListViews for paired devices/ new devices, connect buttons and search buttons
                    // mNewBTDevices and mPairedBTDevices are lists of found devices
                    lvNewDevices = root.findViewById(R.id.otherDevicesListView);
                    lvPairedDevices = root.findViewById(R.id.pairedDevicesListView);
                    mNewBTDevices = new ArrayList<>();
                    mPairedBTDevices = new ArrayList<>();

                    connectBtn = root.findViewById(R.id.connectBtn);
                    btnSearch = root.findViewById(R.id.scanButton);

                    int width = dm.widthPixels;
                    int height = dm.heightPixels;

                    // Get bluetooth adapter, entry point to local bluetooth hardware
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                    // initialises toggle switch to enable/disable bluteooth, updates switch to match if its currently ON
                    Switch bluetoothSwitch = root.findViewById(R.id.bluetoothSwitch);
                    if(mBluetoothAdapter.isEnabled()){
                        bluetoothSwitch.setChecked(true);
                        bluetoothSwitch.setText("ON");
                    }

                    // registers listeners for bluetooth pairing (BOND_STATE_CHANGED) and connection status (ConnectionStatus)
                    // mBroadcastReceiver4 is defined elsewhere, handles events like device paired, device connected, device disconnected
                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                    getActivity().registerReceiver(mBroadcastReceiver4, filter);

                    IntentFilter filter2 = new IntentFilter("ConnectionStatus");
                    LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastReceiver5, filter2);

//        checkBTPermissions(); // might help with the 1st time crashing when clicking 'Scan'
                    //
                     // when tap on new device: cancels discovery, get new info initiates pairing and stores selected device
                    lvNewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            mBluetoothAdapter.cancelDiscovery();
                            lvPairedDevices.setAdapter(mPairedDeviceListAdapter);

                            String deviceName = mNewBTDevices.get(i).getName();
                            String deviceAddress = mNewBTDevices.get(i).getAddress();
                            Log.d(TAG, "onItemClick: A device is selected.");
                            Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                            Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                Log.d(TAG, "onItemClick: Initiating pairing with " + deviceName);
                                mNewBTDevices.get(i).createBond();

                                mBluetoothConnection = new BluetoothConnectionService(getContext());
                                mBTDevice = mNewBTDevices.get(i);
                            }
                        }
                    });

                    // when tap a paired device,selects it and prepares it for connection
                    lvPairedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                            mBluetoothAdapter.cancelDiscovery();
                            lvNewDevices.setAdapter(mNewDeviceListAdapter);

                            String deviceName = mPairedBTDevices.get(i).getName();
                            String deviceAddress = mPairedBTDevices.get(i).getAddress();
                            Log.d(TAG, "onItemClick: A device is selected.");
                            Log.d(TAG, "onItemClick: DEVICE NAME: " + deviceName);
                            Log.d(TAG, "onItemClick: DEVICE ADDRESS: " + deviceAddress);

                            mBluetoothConnection = new BluetoothConnectionService(getContext());
                            mBTDevice = mPairedBTDevices.get(i);
                        }
                    });

                    // switch change listener, toggles actual enable bluetooth button on tablet
                    bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
                        // Enabling and Disabling of Bluetooth on The Device
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                            Log.d(TAG, "onChecked: Enabling/Disabling Bluetooth");
                            if(isChecked){
                                compoundButton.setText("ON");
                            }else
                            {
                                compoundButton.setText("OFF");
                            }

                            if (mBluetoothAdapter == null) {
                                Log.d(TAG, "enableDisableBT: Does not have Bluetooth capabilities");
                                updateStatus("Error: Bluetooth is not supported on this device");
                                compoundButton.setChecked(false);
                            }
                            else {
                                if (!mBluetoothAdapter.isEnabled()) {
                                    Log.d(TAG, "enableDisableBT: Enabling Bluetooth");

                                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                                    startActivity(discoverableIntent);

                                    compoundButton.setChecked(true);

                                    //IntentFilter catches the state change
                                    IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                                    getActivity().registerReceiver(mBroadcastReceiver1, BTIntent);
                                }
                                if (mBluetoothAdapter.isEnabled()) {
                                    Log.d(TAG, "enableDisableBT: Disabling Bluetooth");
                                    mBluetoothAdapter.disable();

                                    IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                                    getActivity().registerReceiver(mBroadcastReceiver1, BTIntent);
                                }
                            }
                        }
                    });
                    // connect button to connect to a currently selected device
                    connectBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if(mBTDevice ==null)
                            {
                                updateStatus("Please Select a Device before connecting.");
                            }
                            else {
                                startConnection();
                            }
                        }
                    });
                    //search button, starts bluetooth device discovery scan
                    btnSearch.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View view) {
                            Log.d(TAG, "onClick: search button");
                            toggleButtonScan(view);
                        }
                    });

                    // back button, saves connection status to shared pref and closes fragment/activity
                    Button backBtn = root.findViewById(R.id.backBtn);

                    connStatusTextView = root.findViewById(R.id.connStatusTextView);
                    connStatus ="Disconnected";
                    sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
                    if (sharedPreferences.contains("connStatus"))
                        connStatus = sharedPreferences.getString("connStatus", "");

                    connStatusTextView.setText(connStatus);

                    backBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            editor = sharedPreferences.edit();
                            editor.putString("connStatus", connStatusTextView.getText().toString());
                            editor.commit();
                            TextView status = Home.getBluetoothStatus();
                            String s = connStatusTextView.getText().toString();
                            //status.setText(s);
                            getActivity().finish();
                        }
                    });

                    // shows non cancellable loading dialogue while waiting to reconnect
                    myDialog = new ProgressDialog(getContext());
                    myDialog.setMessage("Waiting for other device to reconnect...");
                    myDialog.setCancelable(false);
                    myDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });









                    return root;
                }

//
//    private void checkBTPermissions() {
//        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
//            int permissionCheck = 0;
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//                permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
//            }
//            if (permissionCheck != 0) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
//                }
//            }
//        } else {
//            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
//        }
//    }

//check and request runtime permissions if missing
                private void checkBTPermissions(){
                    int permission1 = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    int permission2 = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.BLUETOOTH_SCAN);
                    if (permission1 != PackageManager.PERMISSION_GRANTED) {
                        // We don't have permission so prompt the user
                        ActivityCompat.requestPermissions(
                                getActivity(),
                                PERMISSIONS_STORAGE,
                                1
                        );
                    } else if (permission2 != PackageManager.PERMISSION_GRANTED){
                        ActivityCompat.requestPermissions(
                                getActivity(),
                                PERMISSIONS_LOCATION,
                                1
                        );
                    }
                }
//start scanning for new devices and updates the list + retrieves already paired devices
                public void Scanning() {
                    Log.d(TAG, "toggleButton: Scanning for unpaired devices.");
                    checkBTPermissions();
                    mNewBTDevices.clear();
                    if (mBluetoothAdapter != null) {
                        if (!mBluetoothAdapter.isEnabled()) {
                            updateStatus( "Please turn on Bluetooth first!");
                        }
                        //If discovering, cancel discovery and start again
                        if (mBluetoothAdapter.isDiscovering()) {
                            mBluetoothAdapter.cancelDiscovery();
//                checkBTPermissions();

                            mBluetoothAdapter.startDiscovery();
                            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                            getActivity().registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
                        }
                        // If not discovering, start discovery
                        else if (!mBluetoothAdapter.isDiscovering()) {
//                checkBTPermissions();

                            mBluetoothAdapter.startDiscovery();
                            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                            getActivity().registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
                        }
                        mPairedBTDevices.clear();
                        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                        Log.d(TAG, "toggleButton: Number of paired devices found: "+ pairedDevices.size());
                        for(BluetoothDevice d : pairedDevices){
                            Log.d(TAG, "Paired Devices: "+ d.getName() +" : " + d.getAddress());
                            mPairedBTDevices.add(d);
                            mPairedDeviceListAdapter = new DeviceListAdapter(getContext(), R.layout.device_adapter_view, mPairedBTDevices);
                            lvPairedDevices.setAdapter(mPairedDeviceListAdapter);
                        }
                    }
                }

                public void toggleButtonScan(View view){
                    Scanning();
                }

//broadcast receivers that listen to system broadcasts related to bluetooth events
                private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (action.equals(mBluetoothAdapter.ACTION_STATE_CHANGED)) {
                            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,mBluetoothAdapter.ERROR);
                            //Turning on or off Bluetooth on Device
                switch(state){
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "mBroadcastReceiver1: onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "onReceive: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "mBroadcastReceiver1: onReceive: STATE ON");
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
                final int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                switch (mode) {
                    //Device is in discoverable mode. i.e. other devices can find this device.
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.");
                        break;
                    //Device is not discoverable.
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "mBroadcastReceiver2: Connecting...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "mBroadcastReceiver2: Connected.");
                        break;
                }
            }
        }
    };

        private final BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                Log.d(TAG, "onReceive: ACTION FOUND.");

                if(action.equals(BluetoothDevice.ACTION_FOUND)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if(device.getBondState()!=BluetoothDevice.BOND_BONDED) {
                        mNewBTDevices.add(device);
                        Log.d(TAG, "onReceive: " + device.getName() + " : " + device.getAddress());
                        mNewDeviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, mNewBTDevices);
                        lvNewDevices.setAdapter(mNewDeviceListAdapter);
                    }
                }
            }
        };

    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // 3 ACTIONS
                if(mDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                    Log.d(TAG, "BOND_BONDED.");
                    updateStatus("Successfully paired with " + mDevice.getName());
                    //mBTDevice = mDevice;
                    Scanning();
                }
                if(mDevice.getBondState() == BluetoothDevice.BOND_BONDING){
                    Log.d(TAG, "BOND_BONDING.");
                }
                if(mDevice.getBondState() == BluetoothDevice.BOND_NONE){
                    Log.d(TAG, "BOND_NONE.");
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver5 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            BluetoothDevice mDevice = intent.getParcelableExtra("Device");
            String status = intent.getStringExtra("Status");
            sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
            editor = sharedPreferences.edit();

            if(status.equals("connected")){
                try {
                    myDialog.dismiss();
                } catch(NullPointerException e){
                    e.printStackTrace();
                }

                Log.d(TAG, "mBroadcastReceiver5: Device now connected to "+mDevice.getName());
                updateStatus( "Device now connected to "+mDevice.getName());
                editor.putString("connStatus", "Connected to " + mDevice.getName());
                connStatusTextView.setText("Connected to " + mDevice.getName());

            }
            else if(status.equals("disconnected") && retryConnection == false){
                Log.d(TAG, "mBroadcastReceiver5: Disconnected from "+mDevice.getName());
                updateStatus("Disconnected from "+mDevice.getName());
                mBluetoothConnection = new BluetoothConnectionService(getContext());
                //mBluetoothConnection.startAcceptThread();


                sharedPreferences = getActivity().getSharedPreferences("Shared Preferences", Context.MODE_PRIVATE);
                editor = sharedPreferences.edit();
                editor.putString("connStatus", "Disconnected");

               connStatusTextView.setText("Disconnected");

                editor.commit();

                try {
                    myDialog.show();
                }catch (Exception e){
                    Log.d(TAG, "BluetoothPopUp: mBroadcastReceiver5 Dialog show failure");
                }
                retryConnection = true;
                reconnectionHandler.postDelayed(reconnectionRunnable, 5000);

            }
            editor.commit();
        }
    };
//starts a client thread to connect to a given bluetooth device
    public void startConnection(){
        startBTConnection(mBTDevice,MY_UUID);
    }

    public void startBTConnection(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection");
        mBluetoothConnection.startClientThread(device, uuid);
    }

//fragment cleanup, unregisters receivers
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: called");
        super.onDestroy();
        try {
            getActivity().unregisterReceiver(mBroadcastReceiver1);
            getActivity().unregisterReceiver(mBroadcastReceiver2);
            getActivity().unregisterReceiver(mBroadcastReceiver3);
            getActivity().unregisterReceiver(mBroadcastReceiver4);
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mBroadcastReceiver5);
        } catch(IllegalArgumentException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause: called");
        super.onPause();
        try {
            getActivity().unregisterReceiver(mBroadcastReceiver1);
            getActivity().unregisterReceiver(mBroadcastReceiver2);
            getActivity().unregisterReceiver(mBroadcastReceiver3);
            getActivity().unregisterReceiver(mBroadcastReceiver4);
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mBroadcastReceiver5);
        } catch(IllegalArgumentException e){
            e.printStackTrace();
        }
    }

//utility methods
    private void showLog(String message) {
        Log.d(TAG, message);
    }
    private void updateStatus(String message) {
        Toast toast = Toast.makeText(getContext(), message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP,0, 0);
        toast.show();
    }
}