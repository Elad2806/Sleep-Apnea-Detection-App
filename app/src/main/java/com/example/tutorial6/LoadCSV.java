package com.example.tutorial6;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.opencsv.CSVReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class LoadCSV extends AppCompatActivity {
    EditText editTextFileName;
    LineChart lineChart;
    ListView apneaEventsListView;
    ArrayList<String> apneaEventsList = new ArrayList<>();
    TextView apneaEventCountView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_csv);
        Switch switchHeartRate = findViewById(R.id.switchHeartRate);
        Switch switchSpO2 = findViewById(R.id.switchSpO2);
        apneaEventCountView = findViewById(R.id.ApneaCounterView);
        switchHeartRate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleDataSet(0, isChecked); // Assuming heart rate is the second dataset (index 1)
        });

        switchSpO2.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleDataSet(1, isChecked); // Assuming SpO2 is the third dataset (index 2)
        });

        editTextFileName = findViewById(R.id.editTextFileName);
        lineChart = findViewById(R.id.line_chart);
        apneaEventsListView = findViewById(R.id.apneaEventsListView);
        Button openButton = findViewById(R.id.button_open);
        Button backButton = findViewById(R.id.button_back);

        openButton.setOnClickListener(v -> loadCsvData());
        backButton.setOnClickListener(v -> finish());
    }
    private void toggleDataSet(int index, boolean isVisible) {
        LineData data = lineChart.getData();
        if (data != null && index < data.getDataSetCount()) {
            ILineDataSet dataSet = data.getDataSetByIndex(index);
            dataSet.setVisible(isVisible);
            lineChart.notifyDataSetChanged();
            lineChart.invalidate();
        }
    }

    private void loadCsvData() {
        String fileName = editTextFileName.getText().toString();
        if (!fileName.isEmpty()) {
            try {
                // Load HR and SpO2 data
                String hrSpo2FilePath = "/sdcard/csv_dir/hr_spo2_data/" + fileName + ".csv";
                Log.d("LoadCSV", "Loading HR and SpO2 data from: " + hrSpo2FilePath);
                ArrayList<String[]> hrSpo2Data = CsvRead(hrSpo2FilePath);
                if (hrSpo2Data.isEmpty()) {
                    Log.d("LoadCSV", "No HR and SpO2 data found in the file.");
                } else {
                    updateChart(hrSpo2Data, "Heart Rate", "SpO2");
                }

                // Load Apnea events data
                String apneaEventsFilePath = "/sdcard/csv_dir/apnea_events_data/" + fileName + ".csv";
                Log.d("LoadCSV", "Loading apnea events data from: " + apneaEventsFilePath);
                displayApneaEvents(apneaEventsFilePath);

            } catch (Exception e) {
                Log.e("LoadCSV", "Error loading data: " + e.getMessage());
                e.printStackTrace();
                // Show an error message to the user or handle the error appropriately
            }
        } else {
            Log.d("LoadCSV", "File name is empty.");
            // Inform the user to input a file name or handle accordingly
        }
    }

    private void updateChart(ArrayList<String[]> csvData, String label1, String label2) {
        ArrayList<Entry> valuesHR = new ArrayList<>();
        ArrayList<Entry> valuesSpO2 = new ArrayList<>();
        ArrayList<String> timestamps = new ArrayList<>();

        for (int i = 0; i < csvData.size(); i++) {
            String[] row = csvData.get(i);
            timestamps.add(row[0]); // Assuming the first column is the timestamp
            float hr = Float.parseFloat(row[1]);
            float spo2 = Float.parseFloat(row[2]);
            valuesHR.add(new Entry(i, hr));
            valuesSpO2.add(new Entry(i, spo2));
        }

        LineDataSet dataSetHR = new LineDataSet(valuesHR, label1);
        dataSetHR.setColor(Color.RED);
        dataSetHR.setValueTextColor(Color.BLACK);
        dataSetHR.setLineWidth(1.5f);
        dataSetHR.setCircleRadius(3f);

        LineDataSet dataSetSpO2 = new LineDataSet(valuesSpO2, label2);
        dataSetSpO2.setColor(Color.BLUE);
        dataSetSpO2.setValueTextColor(Color.BLACK);
        dataSetSpO2.setLineWidth(1.5f);
        dataSetSpO2.setCircleRadius(3f);

        LineData data = new LineData(dataSetHR, dataSetSpO2);
        lineChart.setData(data);

        // Formatting the XAxis
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new TimestampAxisValueFormatter(timestamps));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setGranularity(1f); // Set minimum interval to 1 to avoid overlapping
        xAxis.setGranularityEnabled(true); // Enable granularity
        xAxis.setLabelCount(timestamps.size() / 2, false); // Adjust the label count to your needs

        // Adjusting the Legend and Description
        Legend legend = lineChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setYOffset(10f);

        Description description = new Description();
        description.setEnabled(false);
        lineChart.setDescription(description);

        lineChart.setExtraBottomOffset(21f); // Add extra offset at the bottom for the labels

        lineChart.invalidate();
    }





    private LineDataSet createDataSet(ArrayList<String[]> csvData, int index, String label) {
        ArrayList<Entry> dataVals = new ArrayList<>();
        for (String[] row : csvData) {
            float xValue = Float.parseFloat(row[0]); // Assuming the first column is the timestamp
            float yValue = Float.parseFloat(row[index]);
            dataVals.add(new Entry(xValue, yValue));
        }

        LineDataSet lineDataSet = new LineDataSet(dataVals, label);
        lineDataSet.setLineWidth(2.5f);
        lineDataSet.setCircleRadius(4.5f);
        return lineDataSet;
    }

    private ArrayList<String[]> CsvRead(String path) {
        ArrayList<String[]> csvData = new ArrayList<>();
        try {
            CSVReader reader = new CSVReader(new FileReader(new File(path)));
            String[] nextLine;
            boolean isFirstLine = true; // Assuming the first line is a header

            while ((nextLine = reader.readNext()) != null) {
                if (isFirstLine) {
                    isFirstLine = false; // Skip the header line
                } else {
                    // Add all other lines without checking if the first column is numeric
                    csvData.add(nextLine);
                }
            }
        } catch (IOException e) {
            Log.e("LoadCSV", "Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
        return csvData;
    }


    private void displayApneaEvents(String filePath) {
        ArrayList<String[]> apneaEventsData = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                String[] parts = line.split(",");
                // Assuming the file contains timestamp, duration, hr, and spo2 in each line
                if (parts.length >= 4) {
                    apneaEventsData.add(new String[] {parts[0], parts[1], parts[2], parts[3]});
                }
            }
        } catch (IOException e) {
            Log.e("LoadCSV", "Error reading apnea events file: " + e.getMessage());
            apneaEventsData.add(new String[] {"Error loading events", "", "", ""});
        }

        // Update the TextView with the count of apnea events
        String countText = "Total Apnea Events: " + apneaEventsData.size();
        apneaEventCountView.setText(countText);

        ApneaEventAdapter adapter = new ApneaEventAdapter(this, apneaEventsData);
        apneaEventsListView.setAdapter(adapter);
    }



    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }
    // Define the TimestampAxisValueFormatter as an inner class
    public class TimestampAxisValueFormatter extends ValueFormatter {
        private ArrayList<String> mValues;

        public TimestampAxisValueFormatter(ArrayList<String> values) {
            this.mValues = values;
        }

        @Override
        public String getAxisLabel(float value, AxisBase axis) {
            int index = Math.round(value);
            if (index >= 0 && index < mValues.size()) {
                return mValues.get(index);
            } else {
                return "";
            }
        }
    }
}
class ApneaEventAdapter extends ArrayAdapter<String[]> {
    public ApneaEventAdapter(Context context, ArrayList<String[]> events) {
        super(context, 0, events);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String[] event = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_apnea_event, parent, false);
        }

        TextView textViewTime = convertView.findViewById(R.id.apneaEventTime);
        TextView textViewDuration = convertView.findViewById(R.id.apneaEventDuration);
        TextView textViewHR = convertView.findViewById(R.id.hrValue); // ID for HR TextView
        TextView textViewSpO2 = convertView.findViewById(R.id.spo2Value); // ID for SpO2 TextView

        if (event != null && event.length >= 4) {
            String timeWithoutQuotes = event[0].replace("\"", "");
            String durationWithoutQuotes = event[1].replace("\"", "");
            String hrValue = event[2].replace("\"", "");
            String spo2Value = event[3].replace("\"", "");

            textViewTime.setText(timeWithoutQuotes);
            textViewDuration.setText(durationWithoutQuotes);
            textViewHR.setText(hrValue);
            textViewSpO2.setText(spo2Value);
        }

        return convertView;
    }
}
