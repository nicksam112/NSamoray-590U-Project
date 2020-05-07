from keras.applications.resnet50 import ResNet50
import pandas as pd
import numpy as np
import os
import keras
import cv2
import matplotlib.pyplot as plt
from keras.layers import Dense,GlobalAveragePooling2D,Dropout
from keras.applications import MobileNet
from keras.preprocessing import image
from keras.applications.mobilenet import preprocess_input
from keras.preprocessing.image import ImageDataGenerator
from keras.models import Model
from keras.optimizers import Adam

#create model based on Mobile Net
base_model = MobileNet(weights=None,include_top=False, input_shape=(128,128,3))
x=base_model.output
x=GlobalAveragePooling2D()(x)
x=Dropout(0.5)(x)
x=Dense(512,activation='relu')(x) 
x=Dropout(0.5)(x)
preds=Dense(1,activation='sigmoid')(x) 
model=Model(inputs=base_model.input,outputs=preds)

# summarize the model
print(model.summary())

#test and train generator
train_datagen = ImageDataGenerator(rescale=1./255)
train_generator=train_datagen.flow_from_directory('train/', 
                                                 target_size=(128,128),
                                                 color_mode='rgb',
                                                 batch_size=32,
                                                 class_mode='binary',
                                                 shuffle=True)

test_datagen = ImageDataGenerator(rescale=1./255)
test_generator=test_datagen.flow_from_directory('test/', 
                                                 target_size=(128,128),
                                                 color_mode='rgb',
                                                 batch_size=32,
                                                 class_mode='binary',
                                                 shuffle=True)
#compile
model.compile(optimizer='Adam',loss='binary_crossentropy',metrics=['accuracy'])

#tensorboard callback
tb = keras.callbacks.TensorBoard(log_dir='./logs', histogram_freq=0,  
          write_graph=True, write_images=True)

#train and save model
model.fit_generator(generator=train_generator,
                   steps_per_epoch=128,
                   epochs=64,
                   validation_data=test_generator,
                   validation_steps=32)

model.save('mobile_model.h5')