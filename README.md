# NSamoray-590U-Project

URL for this project: https://github.com/nicksam112/NSamoray-590U-Project

Videos demo-ing this project can be seen here: https://drive.google.com/drive/folders/1VcMXyK1JLSOkns_dIQr3I-IKI6rEvS26?usp=sharing

## Android

The Android portion of this project consists of three seperate parts, a map viewer, a bluetooth data collector, and a sound classifier. The goal of this project was to give users a broad overview of potential "hot spots" in their area, so they can better plan their routes and locations throughout the day in order to minimize their contact with these areas. All data is kept anonymous, with no user id's or bluetooth devices ever tracked or stored in order to mitigate concerns about privacy. 

#### Map Viewer
The Map View allows the user to view the collected data in their area through a handful of different methods. The primary ones are the heatmap and the marker methods. The heatmap provides a broad overview of which areas contain user collected data, and is weighted heavier in areas with larger amounts of detected bluetooth devices. The Marker view on the other hand, shows the raw collected data by users in the form of markers, one marker per data point. Users are able to click on each marker to see when that data was collected, and the number of devices that were detected. These views can be toggled by their respective buttons on the bottom of the screen.

#### Bluetooth Data Collection
All data is user collected, and is performed by simply tapping the "Nearby Devices" button. This will automatically run a bluetooth scan for one second, count the total number of devices found, discard any identifying information, and upload this data point to the cloud database. This database keeps itself automatically updated as data points are collected by users and is syncronized across all devices.

#### Cough Detection
The Cough Detection model is a tflite tensorflow model that operates on snippets of audio 1 second long collected by the devices microphone. This audio is collected at 44khz, and then fed into the model. The model converts this raw audio data into a spectrogram, and then runs the produced spectrogram through a Convolutional Neural Network in order to classify if the given audio sample contains coughing or even sneezing. As more coughs are detected, the "Cough Safety" bar steadily drops, indicating that a given area is a potential hotspot. 


HeatMap View               |  Marker View              | HeatMap View with Cough Detection
:-------------------------:|:-------------------------:|:-------------------------:
<img src="https://github.com/nicksam112/NSamoray-590U-Project/blob/master/Photos/1.png" width="250"> | <img src="https://github.com/nicksam112/NSamoray-590U-Project/blob/master/Photos/2.png" width="250"> | <img src="https://github.com/nicksam112/NSamoray-590U-Project/blob/master/Photos/3.png" width="250">


## Model Scripts
This folder contains the scripts used to develop and train the cough detection model. Ultimately, this is meant to generate a keras model file that can accurately determine whether a given audio sample contains a cough or not. Before these scripts can be used, youtube videos need to be downloaded with a tool such as https://github.com/ytdl-org/youtube-dl

#### extract_audio
Script extracts audio samples from the given youtube videos and the given csv files. These csv files are taken from Google's Audio dataset: https://research.google.com/audioset/. The files are saved as mp3's by default.

#### extract_spectrograms
Script extract spectrograms from audio samples. Self explanatory. Example of a generated spectrogram below.

![spec](https://github.com/nicksam112/NSamoray-590U-Project/blob/master/Photos/spec.png)

#### audio_model
Contains all the code needed to setup and train a MobileNet model on spectrogram data produced by the above scripts. We were able to achieve accuracy of ~90% on a validation dataset with just the balanced labels from the Google AudioSet. Model is then saved as an h5 file, which can then be converted to a tflite file. Ultimately, this isn't the model I ended up using as I wasn't able to successfully convert audio to spectragrams with keras. Final approach taken was a modified version of this link: https://github.com/tensorflow/docs/blob/master/site/en/r1/tutorials/sequences/audio_recognition.md

![spec](https://github.com/nicksam112/NSamoray-590U-Project/blob/master/Photos/model.PNG)
