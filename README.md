# droneklv

[![Build Status](https://travis-ci.org/wiseman/droneklv.svg?branch=master)](https://travis-ci.org/wiseman/droneklv)[![Coverage Status](https://coveralls.io/repos/wiseman/droneklv/badge.svg?branch=master)](https://coveralls.io/r/wiseman/droneklv?branch=master)

Droneklv is a Clojure library for accessing
[KLV metadata](http://en.wikipedia.org/wiki/KLV) embedded in drone
video.

This code attempts to support two of the standards for drone-related
metadata, both of which come from the Motion Imagery Standards Board:

* "UAS Datalink Local Set", [MISB ST 0601.8](http://www.gwg.nga.mil/misb/docs/standards/ST0601.8.pdf).
* "Predator UAV Basic Universal Metadata Set", [MISB EG 0104.4](http://www.gwg.nga.mil/misb/docs/eg/EG0104.4.pdf).

EG 0104.4 is an earlier standard (actually, "engineering guideline")
that requires higher bandwidth. ST 0601.8 is a more recent standard
with more efficient encoding that is otherwise mostly equivalent.


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

([:klv-uas-datalink-local-dataset
  ([:unix-timestamp 1245257585099653N]
   [:uas-ls-version-number 1]
   [:platform-heading 86.10666056305791]
   [:platform-pitch 3.359477523117771]
   [:platform-roll 0.5157628101443525]
   [:image-source-sensor "EON"]
   [:image-coordinate-system "Geodetic WGS84"]
   [:sensor-lat 54.68132328460055]
   [:sensor-lon -110.1685597701783]
   [:sensor-true-alt 1532.272831311513]
   [:sensor-horizontal-fov 0.3653009842069123]
   [:sensor-vertical-fov 0.2059967956054017]
   [:sensor-relative-azimuth 46.10314941175821]
   [:sensor-relative-elevation -4.41094972398642]
   [:sensor-relative-roll 358.2026063087868]
   [:slant-range 10928.62454497456]
   [:target-width 0.0]
   [:frame-center-lat 54.7491234516488]
   [:frame-center-lon -110.0466381153309]
   [:frame-center-elevation -4.522774090180819]
   [:target-location-lat 54.7491234516488]
   [:target-location-lon -110.0466381153309]
   [:target-location-elevation -4.522774090180819]
   [:platform-ground-speed 46]
   [:ground-range 10820.67494532575]
   [:checksum 7263])]
 [:klv-uas-datalink-local-dataset
   ... etc.
```

FIXME

## License

Copyright © 2015 John Wiseman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
