import numpy as np
from scipy.signal import find_peaks
from datetime import datetime, timedelta
from scipy.signal import butter, lfilter

def butter_lowpass(cutoff, fs, order=5):
    nyquist = 0.5 * fs
    normal_cutoff = cutoff / nyquist
    b, a = butter(order, normal_cutoff, btype='low', analog=False)
    return b, a

def butter_lowpass_filter(data, cutoff, fs, order=5):
    # Ensure each element is converted to a float
    if not isinstance(data, list):
        data = list(data)
    data = [float(i) for i in data]
    data = np.array(data)

    b, a = butter_lowpass(cutoff, fs, order=order)
    y = lfilter(b, a, data)
    return y.tolist()  # Convert the filtered data back to a list for compatibility

def get_apnea_events(magnitudes_list, start_time_ms, sample_interval):
    # Convert start time in milliseconds to datetime object
    start_time = datetime.fromtimestamp(start_time_ms / 1000.0)

    # Convert each element in magnitudes_list to a Python float
    magnitudes_list = np.array([float(value) for value in magnitudes_list])
    magnitudes_list = magnitudes_list - np.mean(magnitudes_list)
    # Detect peaks - adjust parameters as necessary
    peaks, _ = find_peaks(magnitudes_list, prominence=0.03, distance=8)

    # Calculate intervals between peaks (in terms of data points)
    intervals = np.diff(peaks) * sample_interval

    # Initialize the list to hold the timestamps and durations of apnea events
    apnea_events = []

    # Define the apnea threshold (e.g., 8 seconds for adults)
    apnea_threshold = 8

    # Loop through intervals to find apnea events, calculate their exact times, and durations
    for i, interval in enumerate(intervals):
        if interval > apnea_threshold:
            event_time = start_time + timedelta(seconds=peaks[i] * sample_interval)
            # Format the timestamp and duration as strings and append as a sublist
            apnea_events.append([event_time.strftime('%Y-%m-%d %H:%M:%S'), str(interval)])

    return apnea_events