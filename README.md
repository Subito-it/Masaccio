#Masaccio

This library provides a useful widget class which automatically detects the presence of faces in the source image and crop it accordingly so to achieve the best visual result.

![Masaccio](https://github.com/Subito-it/Masaccio/blob/master/masaccio_demo.gif)

##Download [![Maven Central](https://maven-badges.herokuapp.com/maven-central/it.subito/masaccio-library/badge.svg)](https://maven-badges.herokuapp.com/maven-central/it.subito/masaccio-library)
Grab the latest version via Gradle:

```groovy
compile 'it.subito:masaccio-library:1.0.0'
```

##Usage

The typical usage is to declare the widget directly into the layout XML file. For example:

```xml
<it.subito.masaccio.MasaccioImageView
    android:id="@+id/masaccio_view"
    android:layout_width="300dp"
    android:layout_height="200dp"
    masaccio:center_face="true"
    masaccio:activate_matrix="ifNoFace"
    masaccio:translate_y="0.25"
    android:scaleType="centerCrop"/>
```

Since the detection API provided by the Android SDK is synchronous, in order to avoid performance degradation in the UI thread, the library provides an helper object which enables the face detection processing to be performed in the loading thread.

An example of integration with the [UIL][1] library is provided in the **app** module.

The widget supports all the attributes of a [ImageView][2]

Customisation
-------------

Please see the [Customisation page][3] for more information on how to change the behaviour of the View.

#License

    Copyright (C) 2014 Subito.it S.r.l (www.subito.it)

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	     http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

[1]:https://github.com/nostra13/Android-Universal-Image-Loader
[2]:http://developer.android.com/reference/android/widget/ImageView.html
[3]:https://github.com/Subito-it/Masaccio/wiki/Customisation
