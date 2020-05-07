import os
from pydub import AudioSegment
import matplotlib.pyplot as plt
from scipy.io import wavfile
from tempfile import mktemp
import numpy as np

in_folder = ""
out_folder = ""

for f in os.listdir(in_folder):
	print(f)
	#load in file
	mp3_audio = AudioSegment.from_file(infolder + '/'+f, format="mp3")
	#convert to wav
	wname = mktemp('.wav')
	mp3_audio.export(wname, format="wav")
	FS, data = wavfile.read(wname)
	#if it has multiple channels, average them
	if len(data.shape) > 1:
		data = np.mean(data,axis=1)
	#plot and save spectrogram
	plt.clf()
	ax = plt.axes()
	ax.set_axis_off()
	plt.specgram(data, Fs=FS, NFFT=512, noverlap=0,cmap="plasma")
	plt.savefig(out_folder+"/"+f[:-4]+".png", bbox_inches='tight', transparent=True, pad_inches=0.0, dpi=250)