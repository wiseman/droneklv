# droneklv

Droneklv is a Clojure library for accessing KLV metadata embedded in
drone video.

One relevant standard is the Motion Imagery Standards Board's "UAS
Datalink Local Set",
[MISB ST 0601.8](http://www.gwg.nga.mil/misb/docs/standards/ST0601.8.pdf).


## Usage

![Drone video with KLV](/images/drone-klv-still.jpg?raw=true "Drone video with KLV")

First find a video from a drone with embedded KLV metadata. The image
above is from
http://samples.ffmpeg.org/MPEG2/mpegts-klv/Day%20Flight.mpg

Download the video, and use `ffmpeg` to extract the KLV metadata
stream. Then run com.lemondronor.droneklv on the extracted KLV.

```
$ curl -O http://samples.ffmpeg.org/MPEG2/mpegts-klv/Day%20Flight.mpg
$ ffmpeg -i Day\ Flight.mpg -map data-re -codec copy -f data out.klv
$ lein run -m com.lemondronor.droneklv out.klv

([:klv_uas_datalink_local_dataset
  ([:unix-timestamp 1245257585099653N]
   [:uas-ls-version-number 1]
   [:platform-heading 86.10666]
   [:platform-pitch 3.3594775]
   [:platform-roll 0.5157628]
   [:image-source-sensor "EON"]
   [:image-coordinate-system "Geodetic WGS84"]
   [:sensor-lat 54.681324]
   [:sensor-lon -110.16856]
   [:sensor-true-alt 1532.2728]
   [:sensor-horizontal-fov 0.36530098]
   [:sensor-vertical-fov 0.2059968]
   [:sensor-relative-azimuth 46.10315]
   [:sensor-relative-elevation 0.0054258574]
   [:sensor-relative-roll 358.2026]
   [:slant-range 10928.625]
   [:target-width 0.0]
   [:frame-center-lat 54.749123]
   [:frame-center-lon -110.04664]
   [:frame-center-elevation -4.522774]
   [40 [77, -35, -116, 42]]
   [41 [-79, -66, -98, -12]]
   [42 [11, -123]]
   [56 [46]]
   [57 [0, -115, -44, 41]]
   [:checksum 7263])]
...
```

FIXME

## License

Copyright © 2015 John Wiseman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
