import pandas as pd
import numpy as np
import os
import subprocess

label = "/m/01b_21"
csvfile = "unbalanced_train_segments.csv"
positive = False
vid_folder = "unbalanced_negative"
aud_folder = "unbalanced_negative_mp3"

#read in the csv file and determine if we want positive or negative samples
data = pd.read_csv(csvfile, skipinitialspace=True, header=0, quotechar='"',skiprows=0,engine="python")
if positive:
	data = data[data['positive_labels'].str.contains(label)]
else: 
	data = data[data['positive_labels'].str.contains(label) != True]

#get rid of extra characters in youtube file
data['YTID']=data['YTID'].str.replace('-','')
data['YTID']=data['YTID'].str.replace('=','')

#go through each video
for f in os.listdir(vid_folder):
	name = f[f.rfind('-')+1:-4]
	#find the data entry and the amount to trim
	loc = data.loc[data['YTID'] == name]
	st = loc["start_seconds"].values[0]
	ft = loc["end_seconds"].values[0]
	path = "\"" + vid_folder + "/"+f+"\""
	#run the ffmpeg command
	command = "ffmpeg -ss {} -i {} -t {} {}".format(st,path,ft-st,aud_folder+"/"+name+".mp3")
	print(command)
	#call the command to extract the correct audio portion
	subprocess.call(command, shell=True)