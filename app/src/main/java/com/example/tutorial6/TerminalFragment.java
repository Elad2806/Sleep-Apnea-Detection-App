package com.example.tutorial6;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {
    private boolean isStopped = false;
    private ArrayList<Float> smoothedMagnitudeValues = new ArrayList<>();
    private Float bufferStartTime = null; // Start time of the first sample in the buffer
    private List<List<String>> apneaEventsList = new ArrayList<>();
    private ArrayList<Float> heartRateValues = new ArrayList<>();
    private ArrayList<Float> spo2Values = new ArrayList<>();
    private ArrayList<String> timestampValues = new ArrayList<>();
    private TextView totalEventsView;
    private long timeBetweenProccessings = 60;
    private float lastReceivedHR = 0.0f;
    private float lastReceivedSpO2 = 0.0f;
    private ArrayList<Float> smoothedValues = new ArrayList<>();
    TextView appneaEventsView;
    private StringBuilder receiveBuffer = new StringBuilder();
    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private String newline = TextUtil.newline_crlf;

    LineChart mpLineChart;
    LineDataSet lineDataSet1;
    ArrayList<ILineDataSet> dataSets = new ArrayList<>();
    LineData data;
    private int lineCount = 0; // Added to keep track of the x-value for chart entries

    // Additional state variables
    private boolean isRecording = false;
    private float recordingStartTime = 0;
    private float currentTime = 0;
    private CSVWriter accelDataCsvWriter = null;
    private CSVWriter hrSp02DataCsvWriter = null;
    private String accelCsvFilePath = null;
    private String hrCsvFilePath = null;
    // Additional UI elements
    private Button startButton, stopButton, resetButton, saveButton;
    private EditText numberOfStepsEditText, fileNameEditText;
    private RadioGroup activityTypeRadioGroup;
    private String selectedActivityType;


    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        appneaEventsView = (TextView) view.findViewById(R.id.textView);
        appneaEventsView.setMovementMethod(new ScrollingMovementMethod());

        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(view.getContext()));
        }


        appneaEventsView.setText("Apnea event have not been detected");
        totalEventsView = view.findViewById(R.id.totalEventsView);



        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        hexWatcher = new TextUtil.HexWatcher(sendText);
        hexWatcher.enable(hexEnabled);
        sendText.addTextChangedListener(hexWatcher);
        sendText.setHint(hexEnabled ? "HEX mode" : "");

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        mpLineChart = (LineChart) view.findViewById(R.id.line_chart);
        // Initialize UI elements
        startButton = view.findViewById(R.id.start_button);
        stopButton = view.findViewById(R.id.stop_button);
        resetButton = view.findViewById(R.id.reset_button);
        saveButton = view.findViewById(R.id.save_button);

        // Set click listeners
        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());
        resetButton.setOnClickListener(v -> resetRecording());
        saveButton.setOnClickListener(v -> saveRecording());

        // Initialize all six datasets
        // Define an array of colors
        int[] colors = new int[] {
                getResources().getColor(R.color.line1Color),
                getResources().getColor(R.color.line2Color),
                getResources().getColor(R.color.line3Color),
                getResources().getColor(R.color.line4Color),
                getResources().getColor(R.color.line5Color),
                getResources().getColor(R.color.line6Color)
        };

        LineDataSet dataSet = new LineDataSet(new ArrayList<Entry>(), "Accel Magnitude");
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4.5f);
        dataSet.setColor(colors[0]);  // Use the first color
        dataSet.setCircleColor(colors[0]);
        dataSets.add(dataSet);

        LineDataSet dataSet2 = new LineDataSet(new ArrayList<Entry>(), "Heart Rate");
        dataSet2.setLineWidth(2.5f);
        dataSet2.setCircleRadius(4.5f);
        dataSet2.setColor(colors[2]);
        dataSet2.setCircleColor(colors[2]);
        dataSets.add(dataSet2);

        LineDataSet dataSet3 = new LineDataSet(new ArrayList<Entry>(), "SpO2 values");
        dataSet3.setLineWidth(2.5f);
        dataSet3.setCircleRadius(4.5f);
        dataSet3.setColor(colors[4]);
        dataSet3.setCircleColor(colors[4]);
        dataSets.add(dataSet3);


        LineData lineData = new LineData(dataSets);
        mpLineChart.setData(lineData);
        mpLineChart.invalidate();

        Button buttonClear = (Button) view.findViewById(R.id.button1);
        Button buttonCsvShow = (Button) view.findViewById(R.id.button2);



        buttonClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clearChartData();
            }
        });


        buttonCsvShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenLoadCSV();

            }
        });



        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            sendText.setText("");
            hexWatcher.enable(hexEnabled);
            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private String[] clean_str(String[] stringsArr){
         for (int i = 0; i < stringsArr.length; i++)  {
             stringsArr[i]=stringsArr[i].replaceAll(" ","");
        }


        return stringsArr;
    }
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        String msg = new String(data);
        if (hexEnabled) {
            receiveText.append(TextUtil.toHexString(data) + '\n');
        } else {
            receiveBuffer.append(msg);

            int startIndex = receiveBuffer.indexOf("<");
            int endIndex = receiveBuffer.indexOf(">", startIndex);

            while (startIndex != -1 && endIndex != -1) {
                String frame = receiveBuffer.substring(startIndex + 1, endIndex).trim();
                receiveBuffer.delete(0, endIndex + 1);

                if (!frame.isEmpty()) {
                    String[] parts = frame.split(",");
                    parts = clean_str(parts);

                    processCompleteDataSet(parts);

                }

                startIndex = receiveBuffer.indexOf("<");
                endIndex = receiveBuffer.indexOf(">", startIndex);
            }
        }
    }

    private void processCompleteDataSet(String[] parts) {
        try {
            currentTime = Float.parseFloat(parts[0]);
            if (bufferStartTime == null) {
                bufferStartTime = currentTime; // Initialize the start time with the first sample's time
            }

            if (parts.length >= 7) {  // Make sure we have all the expected data points
                if (isRecording && accelDataCsvWriter != null) {
                    String[] partsCopy = parts.clone();
                    partsCopy[0] = String.valueOf((currentTime - recordingStartTime));
                    accelDataCsvWriter.writeNext(partsCopy);
                }
                float accX = Float.parseFloat(parts[1]);
                float accY = Float.parseFloat(parts[2]);
                float accZ = Float.parseFloat(parts[3]);
                float magnitude = (float) Math.sqrt(accX * accX + accY * accY + accZ * accZ);

                //float smoothedMagnitude = smoothData(magnitude);

                smoothedMagnitudeValues.add(magnitude);

// Additional code to check the time span and reset the buffer
                if (currentTime - bufferStartTime >= timeBetweenProccessings) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "Applying filter and checking for apnea events...", Toast.LENGTH_SHORT).show();
                        }
                    });

                    ApplyLowPassFilter();
                    getApneaDates();
                    smoothedMagnitudeValues.clear(); // Reset the buffer
                    bufferStartTime = currentTime; // Reset the start time
                }

                LineDataSet dataSetAcc = (LineDataSet) mpLineChart.getData().getDataSetByIndex(0);
                dataSetAcc.addEntry(new Entry(currentTime, magnitude));
                dataSetAcc.notifyDataSetChanged();

            }
            else if (parts.length == 3) {
                if (isRecording && hrSp02DataCsvWriter != null) {
                    String[] partsCopy = parts.clone();
                    partsCopy[0] = String.valueOf((currentTime - recordingStartTime));
                    hrSp02DataCsvWriter.writeNext(partsCopy);
                }
                // Process and display heart rate data
                float heartRate = Float.parseFloat(parts[1]);

                LineDataSet dataSetHR = (LineDataSet) mpLineChart.getData().getDataSetByIndex(1);  // Assuming second dataset is for heart rate
                dataSetHR.addEntry(new Entry(currentTime, heartRate));
                dataSetHR.notifyDataSetChanged();
                lastReceivedHR = heartRate;

                // Process and display heart rate data
                float sp02 = Float.parseFloat(parts[2]);
                LineDataSet dataSetSpO2 = (LineDataSet) mpLineChart.getData().getDataSetByIndex(2);  // Assuming second dataset is for heart rate
                dataSetSpO2.addEntry(new Entry(currentTime, sp02));
                dataSetSpO2.notifyDataSetChanged();
                lastReceivedSpO2 = sp02;

                if (isRecording){
                    spo2Values.add(sp02); // Add SpO2 to the list
                    heartRateValues.add(heartRate); // Add heart rate to the list
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                    timestampValues.add(timestamp);
                }
            }
            mpLineChart.getData().notifyDataChanged();
            mpLineChart.notifyDataSetChanged();
            mpLineChart.invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ApplyLowPassFilter() {
        try {
            Python py = Python.getInstance();
            PyObject pyobj = py.getModule("test");
            PyObject pyList = py.getBuiltins().callAttr("list");

            for (Float value : smoothedMagnitudeValues) {
                pyList.callAttr("append", value);
            }

            // Define your cutoff frequency and sampling rate
            double cutoff = 0.7;  // Example cutoff frequency
            double fs = 1 / 0.1;  // Example sampling rate

            // Call the Python function for low-pass filtering
            PyObject filteredData = pyobj.callAttr("butter_lowpass_filter", pyList, cutoff, fs);

            // Convert the filtered data back to ArrayList
            List<PyObject> filteredList = filteredData.asList();
            ArrayList<Float> filteredArrayList = new ArrayList<>();
            for (PyObject value : filteredList) {
                filteredArrayList.add(value.toFloat());
            }

            smoothedMagnitudeValues = filteredArrayList;

            // Save the filtered data to a CSV file
            saveToCSV(smoothedMagnitudeValues);

        } catch (Exception e) {
            Log.e("ApplyLowPassFilterError", "Error applying low-pass filter: " + e.getMessage());
            e.printStackTrace();
            // Update the UI to show the error
        }
    }

    private void saveToCSV(ArrayList<Float> data) {
        // Define the file name
        String fileName = "/sdcard/csv_dir/filtered_data.csv";
        File file = new File(fileName);


        try (FileWriter fw = new FileWriter(file, true);
             PrintWriter out = new PrintWriter(fw)) {

            // Write each float value to the file
            for (Float value : data) {
                out.println(value);
            }

            Log.i("ApplyLowPassFilter", "Data successfully saved to " + file.getAbsolutePath());

        } catch (IOException e) {
            Log.e("ApplyLowPassFilterError", "Error saving data to CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private float smoothData(float currentValue) {
        final int windowSize = 10
                ;
        float sum = currentValue;
        int count = 1;

        for (int i = 0; i < Math.min(windowSize, smoothedValues.size()); i++) {
            sum += smoothedValues.get(smoothedValues.size() - 1 - i);
            count++;
        }

        float average = sum / count;
        smoothedValues.add(average);

        if (smoothedValues.size() > windowSize) {
            smoothedValues.remove(0);
        }

        return average;
    }
    private void getApneaDates() {
        try {
            Python py = Python.getInstance();
            PyObject pyobj = py.getModule("test");
            PyObject pyList = py.getBuiltins().callAttr("list");

            // Sending smoothed magnitude values to the Python script
            for (Float value : smoothedMagnitudeValues) {
                pyList.callAttr("append", value);
            }

            long currentTimeMillis = System.currentTimeMillis() - timeBetweenProccessings * 1000;
            double sampleInterval = 0.1; // This should match your data's sampling interval

            // Fetching apnea events from the Python script
            PyObject obj = pyobj.callAttr("get_apnea_events", pyList, currentTimeMillis, sampleInterval);

            // Processing the returned apnea events
            if (obj != null && obj.asList() != null) {
                for (PyObject event : obj.asList()) {
                    List<PyObject> eventDetails = event.asList();
                    if (eventDetails.size() == 2) {
                        String timestamp = eventDetails.get(0).toString();
                        String duration = eventDetails.get(1).toString();

                        // Appending HR and SpO2 values
                        String hrValue = String.valueOf(getValueFromTimestamp(timestamp, "hr"));
                        String spo2Value = String.valueOf(getValueFromTimestamp(timestamp, "spo2"));

                        // Adding the event details to the list
                        List<String> eventDetailsList = Arrays.asList(timestamp, duration, hrValue, spo2Value);
                        if (isRecording) {
                            apneaEventsList.add(eventDetailsList);
                        }
                    }
                }
                // Updating the UI with the new event details
                updateApneaEventsView();
            }
        } catch (Exception e) {
            Log.e("sendDataToPythonError", "Error calling Python function: " + e.getMessage());
            e.printStackTrace();
            // Displaying an error message in the UI
            appneaEventsView.setText("Error processing data." + e.getMessage());
        }
    }


    private void updateApneaEventsView() {
        StringBuilder sb = new StringBuilder();
        if (apneaEventsList.isEmpty()) {
            sb.append("No apnea events detected.");
        } else {
            for (List<String> event : apneaEventsList) {
                if (event.size() == 4) {  // Check if the list contains timestamp, duration, HR, and SpO2
                    String timestamp = event.get(0);
                    String duration = event.get(1);
                    String hrValue = event.get(2);
                    String spo2Value = event.get(3);
                    sb.append("Event at ").append(timestamp)
                            .append(" with duration ").append(duration).append(" seconds")
                            .append(", HR: ").append(hrValue)
                            .append(", SpO2: ").append(spo2Value).append("\n");
                }
            }
        }

        Log.d("ApneaEvents", "Updating TextView with: " + sb.toString());
        // Update the total events view if necessary
        updateTotalEventsView();
        // Ensure this runs on the main thread
        getActivity().runOnUiThread(() -> appneaEventsView.setText(sb.toString()));
    }



    private void startRecording() {
        if (!isRecording) {
            isRecording = true;
            isStopped = false;
            recordingStartTime = currentTime;
            clearChartData();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            Toast.makeText(getContext(), "Recording has started at " + timestamp, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        isStopped = !isStopped;
        if (isStopped) {
            isRecording = false;
            Toast.makeText(getContext(), "Recording has stopped", Toast.LENGTH_SHORT).show();
        }
        else {
            isRecording = true;
            Toast.makeText(getContext(), "Recording has continued", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetRecording() {
        if (isRecording) {
            isRecording = false;
        }
        clearDataLists();
        Toast.makeText(getContext(), "Recording has been reset", Toast.LENGTH_SHORT).show();


    }


    private void saveRecording() {
        if (isRecording) {
            isRecording = false;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Enter Base File Name");

        // Set up the input for base file name
        final EditText inputBaseFileName = new EditText(getContext());
        inputBaseFileName.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(inputBaseFileName);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String baseFileName = inputBaseFileName.getText().toString();
                if (baseFileName.isEmpty()) {
                    baseFileName = "defaultData";
                }

                // Construct file paths
                String hrSpo2FilePath = "/sdcard/csv_dir/hr_spo2_data/" + baseFileName + ".csv";
                String apneaFilePath = "/sdcard/csv_dir/apnea_events_data/" + baseFileName + ".csv";

                // Save HR and SpO2 data
                saveHrAndSpo2Data(hrSpo2FilePath);

                // Save Apnea events data
                saveApneaEvents(apneaFilePath);
                clearDataLists();
                Toast.makeText(getContext(), "Data saved with base name: " + baseFileName, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void saveHrAndSpo2Data(String filePath) {
        File hrSpo2File = new File(filePath);
        File hrSpo2Directory = hrSpo2File.getParentFile();

        if (!hrSpo2Directory.exists()) {
            hrSpo2Directory.mkdirs();  // Create the directory if it doesn't exist
        }

        try {
            CSVWriter writer = new CSVWriter(new FileWriter(hrSpo2File));
            writer.writeNext(new String[]{"Timestamp", "Heart Rate", "SpO2"});

            for (int i = 0; i < timestampValues.size(); i++) {
                String timestamp = timestampValues.get(i);
                String heartRate = i < heartRateValues.size() ? heartRateValues.get(i).toString() : "0";
                String spo2 = i < spo2Values.size() ? spo2Values.get(i).toString() : "0";
                writer.writeNext(new String[]{timestamp, heartRate, spo2});
            }

            writer.close();
            Toast.makeText(getContext(), "HR and SpO2 data saved: " + filePath, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error saving HR and SpO2 file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    private void showDataSelectionDialog() {
        boolean[] checkedItems = {true, true}; // Initially, both HR and SpO2 are shown
        String[] dataOptions = {"Heart Rate", "SpO2"};

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Data to Display");
        builder.setMultiChoiceItems(dataOptions, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

        builder.setPositiveButton("OK", (dialog, which) -> updateGraphDisplay(checkedItems));
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateGraphDisplay(boolean[] selections) {
        LineData data = mpLineChart.getData();
        if (data != null) {
            // HR data is the first dataset, SpO2 is the second
            data.getDataSetByIndex(0).setVisible(selections[0]); // Heart Rate
            data.getDataSetByIndex(1).setVisible(selections[1]); // SpO2
            mpLineChart.notifyDataSetChanged();
            mpLineChart.invalidate();
        }
    }

    private void saveApneaEvents(String filePath) {
        File apneaFile = new File(filePath);
        File apneaDirectory = apneaFile.getParentFile();

        if (!apneaDirectory.exists()) {
            apneaDirectory.mkdirs();  // Create the directory if it doesn't exist
        }

        try {
            CSVWriter writer = new CSVWriter(new FileWriter(apneaFile));
            writer.writeNext(new String[]{"Apnea Time", "Duration", "hr","spo2"});

            for (List<String> event : apneaEventsList) {
                String[] record = {event.get(0), event.get(1),event.get(2),event.get(3)};
                writer.writeNext(record);
            }

            writer.close();
            Toast.makeText(getContext(), "Apnea events data saved: " + filePath, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error saving Apnea events file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private float getValueFromTimestamp(String timestampStr, String valueType) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        try {
            Date targetDate = format.parse(timestampStr);
            long minDiff = Long.MAX_VALUE;
            int closestIndex = -1;

            for (int i = 0; i < timestampValues.size(); i++) {
                Date date = format.parse(timestampValues.get(i));
                long diff = Math.abs(date.getTime() - targetDate.getTime());

                if (diff < minDiff) {
                    minDiff = diff;
                    closestIndex = i;
                }
            }

            // Return the corresponding HR or SpO2 value based on the closest timestamp
            if (closestIndex != -1) {
                if ("hr".equalsIgnoreCase(valueType)) {
                    return heartRateValues.get(closestIndex);
                } else if ("spo2".equalsIgnoreCase(valueType)) {
                    return spo2Values.get(closestIndex);
                }
            }
        } catch (ParseException e) {
            Log.e("getValueFromTimestamp", "Error parsing dates: " + e.getMessage());
        }

        return -1; // Return an invalid value if not found or in case of an error
    }


    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        try {
        receive(data);}
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    private ArrayList<Entry> emptyDataValues()
    {
        ArrayList<Entry> dataVals = new ArrayList<Entry>();
        return dataVals;
    }

    private void OpenLoadCSV(){
        Intent intent = new Intent(getContext(),LoadCSV.class);
        startActivity(intent);
    }

    public void clearChartData() {
        Toast.makeText(getContext(), "Clear", Toast.LENGTH_SHORT).show();
        LineData data = mpLineChart.getData();
        if (data != null) {
            for (int i = 0; i < data.getDataSetCount(); i++) {
                ILineDataSet set = data.getDataSetByIndex(i);
                set.clear();
            }
            mpLineChart.notifyDataSetChanged(); // let the chart know it's data changed
            mpLineChart.invalidate(); // refresh
        }
    }
    private void updateTotalEventsView() {
        String totalEventsText = "Total events: " + apneaEventsList.size();
        totalEventsView.setText(totalEventsText);
    }
    private void clearDataLists() {
        // Clear all the lists
        if (apneaEventsList != null) {
            apneaEventsList.clear();
        }

        if (heartRateValues != null) {
            heartRateValues.clear();
        }

        if (spo2Values != null) {
            spo2Values.clear();
        }

        if (timestampValues != null) {
            timestampValues.clear();
        }
    }

}


