(ns com.lemondronor.droneklv
  "Work with KLV metadata frome drone video."
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [com.lemonodor.xio :as xio]
            [gloss.core :as gloss]
            [gloss.io])
  (:import [com.lemondronor.droneklv KLV KLV$KeyLength KLV$LengthEncoding]
           [java.nio ByteBuffer]
           [java.util Arrays]))

(set! *warn-on-reflection* true)


(defn ints->bytes
  [ints]
  (mapv (fn [i]
          (let [i (int i)]
            (byte
             (cond (<= 0 i 127)
                   i
                   (<= 128 i 255)
                   (- i 256)
                   :else
                   (throw (IllegalArgumentException.
                           (format "Value out of range for byte: %s" i)))))))
        ints))


(defn klvs-from-bytes [^bytes data]
  (KLV/bytesToList
   data
   0
   (count data)
   KLV$KeyLength/SixteenBytes
   KLV$LengthEncoding/BER))


(defn read-ber [data offset]
  ;; FIXME: Handle > 127.
  [(get data offset)
   (inc offset)])


(def item-index
  (atom
   {;; Map of name (e.g. :unix-timestamp) to item definition.
    :items-by-name {}
    ;; Map from single byte (usually?) local set key (e.g. 3) to item
    ;; definition.
    :local-set-keys {}
    ;; Map from 16-byte universal set key to item definition. We use
    ;; ByteBuffers as keys in this map because we're really working with
    ;; byte arrays underneath, but byte arrays hash based on object
    ;; identity while ByteBuffers hash based on contents (and are
    ;; cheaper than creating vecs from byte arrays).
    :universal-set-keys {}}))


(defn defitems [item-specs]
  (let [bb (fn [bs] (ByteBuffer/wrap (byte-array (ints->bytes bs))))]
    (->> item-specs
         ;; Add name to attrs
         (map (fn [[name attr]]
                [name (assoc attr :name name)]))
         ;; Convert universal key vecs to ByteBuffers.
         (map (fn [[name attr]]
                (assert (or (:us-key attr) (:ls-key attr))
                        (str "Item must have :us-key or :ls-key: " name))
                [name (if-let [us-key (:us-key attr)]
                        (assoc attr :us-key (bb us-key))
                        attr)]))
         ;; Convert types to decoding functions.
         (map (fn [[name attr]]
                (assert (not (and (:type attr) (:decoder attr)))
                        (str "Item cannot have :type and :decoder: " name))
                [name (if-let [type (:type attr)]
                        (assoc
                         attr
                         :decoder
                         (let [frame (if-let [scale (:scale attr)]
                                       (gloss/compile-frame type nil scale)
                                       (gloss/compile-frame type))]
                           #(gloss.io/decode frame % false)))
                        attr)]))
         (into {})
         (swap! item-index assoc :items-by-name)))
  ;; Index local set keys.
  (swap! item-index assoc :local-set-keys
         (reduce-kv
          (fn [m name attr]
            (if-let [ls-key (:ls-key attr)]
              (do
                (assert
                 (nil? (m ls-key))
                 (str "Duplicate local set key for item " name ": " ls-key))
                (assoc m ls-key attr))
              m))
          {}
          (:items-by-name @item-index)))
  ;; Index universal set keys.
  (swap! item-index assoc :universal-set-keys
         (reduce-kv
          (fn [m name attr]
            (if-let [us-key (:us-key attr)]
              (do
                (assert
                 (nil? (m us-key))
                 (str "Duplicate universal set key for item " name ": " us-key))
                (assoc m us-key attr))
              m))
          {}
          (:items-by-name @item-index))))


(defn scaler [src-min src-max dst-min dst-max]
  #(double
    (+ (* (/ (- % src-min)
             (- src-max src-min))
          (- dst-max dst-min))
       dst-min)))




;; Taken from
;; http://trac.osgeo.org/ossim/browser/trunk/ossimPredator/src/ossimPredatorKlvTable.cpp

(defn decode-basic-universal-dataset [^bytes data]
  (let [klvs (klvs-from-bytes data)]
    (map (fn [^KLV klv]
           (let [item-def (get-in @item-index
                                  [:universal-set-keys
                                   (ByteBuffer/wrap (.getFullKey ^KLV klv))])
                 item-name (:name item-def)]
             (if item-def
               [item-name ((:decoder item-def) (.getValue klv))]
               [vec (.getFullKey klv) (vec (.getValue klv))])))
         klvs)))


(defn decode-local-set-tag
  ([data]
   (decode-local-set-tag data 0))
  ([^bytes data offset]
   (let [[tag-value offset] (read-ber data offset)
         item-def (get-in @item-index [:local-set-keys tag-value])
         [len offset] (read-ber data offset)
         tag-data (byte-array len)]
     (System/arraycopy data offset tag-data 0 len)
     [(+ offset len)
      (if item-def
        [(:name item-def)
         (if-let [decoder (:decoder item-def)]
           (decoder tag-data)
           tag-data)]
        [tag-value (vec tag-data)])])))


(defn decode-uas-datalink-local-dataset
  ([data]
   (decode-uas-datalink-local-dataset data 0))
  ([data offset]
   (loop [offset offset
          values '()]
     (if (>= offset (count data))
       (reverse values)
       (let [[offset tag] (decode-local-set-tag data offset)]
         (recur
          offset
          (conj values tag)))))))


(def lat-scaler (scaler -2147483647 2147483647 -90 90))
(def lon-scaler (scaler -2147483647 2147483647 -180 180))
(def pos-delta-scaler (scaler -32767 32767 -0.075 0.075))
(def alt-scaler (scaler 0 65535 -900 19000))
(def pressure-scaler (scaler 0 65535 0 5000))
(def pitch-scaler (scaler -32767 32767 -20 20))
(def range-scaler (scaler 0 4294967295 0 5000000))


(defitems
  {:basic-universal-dataset
   {:us-key [0x06 0x0E 0x2B 0x34 0x02 0x01 0x01 0x01 0x0E 0x01 0x01 0x02 0x01 0x01 0x00 0x00]
    :decoder decode-basic-universal-dataset}
   :uas-datalink-local-dataset
   {:us-key [0x06 0x0E 0x2B 0x34 0x02 0x0B 0x01 0x01 0x0E 0x01 0x03 0x01 0x01 0x00 0x00 0x00]
    :decoder decode-uas-datalink-local-dataset}
   :unix-timestamp
   {:ls-key 2
    :us-key [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x04 0x07 0x02 0x01 0x01 0x01 0x05 0x00 0x00]
    :type :uint64}
   :mission-id
   {:ls-key 3
    :type (gloss/string :ascii)}
   :platform-tail-number
   {:ls-key 4
    :us-key [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x01 0x05 0x05 0x00 0x00 0x00 0x00 0x00]
    :type (gloss/string :ascii)}
   :platform-heading
   {:ls-key 5
    :us-key [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x07 0x07 0x01 0x10 0x01 0x06 0x00 0x00 0x00]
    :type :uint16
    :scale (scaler 0 65535 0 360)}
   :platform-pitch
   {:ls-key 6
    :us-key [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x07 0x07 0x01 0x10 0x01 0x05 0x00 0x00 0x00]
    :type :int16
    :scale pitch-scaler}
   :platform-roll
   {:ls-key 7
    :type :int16
    :scale (scaler -32767 32767 -50 50)}
   :platform-true-airspeed
   {:ls-key 8
    :type :ubyte}
   :platform-indicated-airspeed
   {:ls-key 9
    :type :ubyte}
   :platform-designation
   {:ls-key 10
    :type (gloss/string :ascii)}
   :image-source-sensor
   {:ls-key 11
    :type (gloss/string :ascii)}
   :image-coordinate-system
   {:ls-key 12
    :type (gloss/string :ascii)}
   :sensor-lat
   {:ls-key 13
    :type :int32
    :scale lat-scaler}
   :sensor-lon
   {:ls-key 14
    :type :int32
    :scale lon-scaler}
   :sensor-true-alt
   {:ls-key 15
    :type :uint16
    :scale alt-scaler}
   :sensor-horizontal-fov
   {:ls-key 16
    :type :uint16
    :scale (scaler 0 65535 0 180)}
   :sensor-vertical-fov
   {:ls-key 17
    :type :uint16
    :scale (scaler 0 65535 0 180)}
   :sensor-relative-azimuth
   {:ls-key 18
    :type :uint32
    :scale (scaler 0 4294967295 0 360)}
   :sensor-relative-elevation
   {:ls-key 19
    :type :int32
    :scale (scaler -2147483647 2147483647 -180 180)}
   :sensor-relative-roll
   {:ls-key 20
    :type :int32
    :scale (scaler 0 4294967295 0 360)}
   :slant-range
   {:ls-key 21
    :type :uint32
    :scale range-scaler}
   :target-width
   {:ls-key 22
    :type :uint16
    :scale (scaler 0 65535 0 10000)}
   :frame-center-lat
   {:ls-key 23
    :type :int32
    :scale lat-scaler}
   :frame-center-lon
   {:ls-key 24
    :type :int32
    :scale lon-scaler}
   :frame-center-elevation
   {:ls-key 25
    :type :uint16
    :scale alt-scaler}
   :offset-corner-lat-point-1
   {:ls-key 26
    :type :int16
    :scale pos-delta-scaler}
   :offset-corner-lon-point-1
   {:ls-key 27
    :type :int16
    :scale pos-delta-scaler}
   :offset-corner-lat-point-2
   {:ls-key 28
    :type :int16
    :scale pos-delta-scaler}
   :offset-corner-lon-point-2
   {:ls-key 29
    :type :int16
    :scale pos-delta-scaler}
   :offset-corner-lat-point-3
   {:ls-key 30
    :type :int16
    :scale pos-delta-scaler}
   :offset-corner-lon-point-3
   {:ls-key 31
    :type :int16
    :scale pos-delta-scaler}
   :offset-corner-lat-point-4
   {:ls-key 32
    :type :int16
    :scale pos-delta-scaler}
   :offset-corner-lon-point-4
   {:ls-key 33
    :type :int16
    :scale pos-delta-scaler}
   :icing-detected
   {:ls-key 34
    :type :ubyte}
   :wind-direction
   {:ls-key 35
    :type :uint16
    :scale (scaler 0 65535 0 360)}
   :wind-speed
   {:ls-key 36
    :type :ubyte
    :scale (scaler 0 255 0 100)}
   :static-pressure
   {:ls-key 37
    :type :uint16
    :scale pressure-scaler}
   :density-altitude
   {:ls-key 38
    :type :uint16
    :scale alt-scaler}
   :outside-air-temp
   {:ls-key 39
    :type :byte}

   })


(def tags
  [[:klv-key-stream-id
    "stream ID",
    [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x01 0x03 0x04 0x02 0x00 0x00 0x00 0x00]],
   [:klv-key-organizational-program-number "Organizational Program Number",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x01 0x03 0x05 0x01 0x00 0x00 0x00 0x00]],
   [:klv-key-unix-timestamp "UNIX Timestamp",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x04 0x07 0x02 0x01 0x01 0x01 0x05 0x00 0x00]],
   [:klv-key-user-defined-utc-timestamp "User Defined UTC" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x02 0x01 0x01 0x01 0x01 0x00 0x00]],
   [:klv-key-user-defined-timestamp-microseconds-1970 "User Defined Timestamp Microseconds since 1970" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x02 0x01 0x01 0x01 0x05 0x00 0x00]],
   [:klv-key-video-start-date-time-utc "Video Timestamp Start Date and Time",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x02 0x01 0x02 0x01 0x01 0x00 0x00]],
   [:klv-timesystem-offset "Time System Offset From UTC" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x03 0x01 0x03 0x03 0x01 0x00 0x00 0x00]],
   [:klv-uas-datalink-local-dataset "UAS Datalink Local Data Set",[0x06 0x0E 0x2B 0x34 0x02 0x0B 0x01 0x01 0x0E 0x01 0x03 0x01 0x01 0x00 0x00 0x00]],
   [:klv-basic-universal-metadata-set "Universal Metadata Set",[0x06 0x0E 0x2B 0x34 0x02 0x01 0x01 0x01 0x0E 0x01 0x01 0x02 0x01 0x01 0x00 0x00]],
   [:klv-security-metadata-universal-set "Security metadata universal set" [0x06 0x0E 0x2B 0x34 0x02 0x01 0x01 0x01 0x02 0x08 0x02 0x00 0x00 0x00 0x00 0x00]],
   [:klv-url-string "URL String" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x01 0x02 0x01 0x01 0x00 0x00 0x00 0x00]],
   [:klv-key-security-classification-set "Security Classification Set" [0x06 0x0E 0x2B 0x34 0x02 0x01 0x01 0x01 0x02 0x08 0x02 0x00 0x00 0x00 0x00 0x00]],
   [:klv-key-byte-order "Byte Order" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x03 0x01 0x02 0x01 0x02 0x00 0x00 0x00]],
   [:klv-key-mission-number"Mission Number",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x01 0x05 0x05 0x00 0x00 0x00 0x00 0x00]],
   [:klv-key-object-country-codes "Object Country Codes" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x20 0x01 0x02 0x01 0x01 0x00]],
   [:klv-key-security-classification "Security Classification" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x02 0x08 0x02 0x01 0x00 0x00 0x00 0x00]],
   [:klv-key-security-release-instructions "Release Instructions" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x20 0x01 0x02 0x09 0x00 0x00]],
   [:klv-key-security-caveats "Caveats" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x02 0x08 0x02 0x02 0x00 0x00 0x00 0x00]],
   [:klv-key-classification-comment "Classification Comment" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x02 0x08 0x02 0x07 0x00 0x00 0x00 0x00]],
   [:klv-key-original-producer-name "Original Producer Name" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x02 0x01 0x03 0x00 0x00 0x00 0x00 0x00]],
   [:klv-key-platform-ground-speed"Platform Ground Speed",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x01 0x05 0x00 0x00 0x00]],
   [:klv-key-platform-magnetic-heading-angle"Platform Magnetic Heading Angle",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x01 0x08 0x00 0x00 0x00]],
   [:klv-key-platform-heading-angle"Platform Heading Angle",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x07 0x07 0x01 0x10 0x01 0x06 0x00 0x00 0x00]],
   [:klv-key-platform-pitch-angle"Platform Pitch Angle",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x07 0x07 0x01 0x10 0x01 0x05 0x00 0x00 0x00]],
   [:klv-key-platform-roll-angle "Platform Roll Angle",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x07 0x07 0x01 0x10 0x01 0x04 0x00 0x00 0x00]],
   [:klv-key-indicated-air-speed "Platform Indicated Air Speed",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x01 0x0B 0x00 0x00 0x00]],
   [:klv-key-platform-designation "Platform Designation",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x01 0x01 0x20 0x01 0x00 0x00 0x00 0x00]],
   [:klv-key-platform-designation2 "Platform Designation",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x01 0x01 0x21 0x01 0x00 0x00 0x00 0x00]],
   [:klv-key-image-source-sensor "Image Source Sensor",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x04 0x20 0x01 0x02 0x01 0x01 0x00 0x00]],
   [:klv-key-image-coordinate-system "Image Coordinate System",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x01 0x01 0x00 0x00 0x00 0x00]],
   [:klv-key-sensor-latitude "Sensor Latitude",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x02 0x04 0x02 0x00]],
   [:klv-key-sensor-longitude "Sensor Longitude",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x02 0x06 0x02 0x00]],
   [:klv-key-sensor-true-altitude "Sensor True Altitude",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x02 0x01 0x02 0x02 0x00 0x00]],
   [:klv-key-sensor-horizontal-fov "Sensor Horizontal Field Of View",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x02 0x04 0x20 0x02 0x01 0x01 0x08 0x00 0x00]],
   [:klv-key-sensor-vertical-fov1 "Sensor Vertical Field Of View",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x07 0x04 0x20 0x02 0x01 0x01 0x0A 0x01 0x00]],
   [:klv-key-sensor-vertical-fov2 "Sensor Vertical Field Of View",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x04 0x20 0x02 0x01 0x01 0x0A 0x01 0x00]],
   [:klv-key-slant-range "Slant Range",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x08 0x01 0x01 0x00 0x00 0x00]],
   [:klv-key-obliquity-angle "Obliquity Angle",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x10 0x01 0x03 0x00 0x00 0x00]],
   [:klv-key-angle-to-north "Angle To North" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x10 0x01 0x02 0x00 0x00 0x00]],
   [:klv-key-target-width "Target Width",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x09 0x02 0x01 0x00 0x00 0x00]],
   [:klv-key-frame-center-latitude "Frame Center Latitude",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x02 0x01 0x03 0x02 0x00 0x00]],
   [:klv-key-frame-center-longitude "Frame Center Longitude",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x02 0x01 0x03 0x04 0x00 0x00]],
   [:klv-key-frame-center-elevation "Frame Center elevation",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x06 0x07 0x01 0x02 0x03 0x10 0x00 0x00 0x00]],
   [:klv-key-corner-latitude-point-1 "Corner Latitude Point 1",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x07 0x01 0x00]],
   [:klv-key-corner-longitude-point-1 "Corner Longitude Point 1",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x0B 0x01 0x00]],
   [:klv-key-corner-latitude-point-2 "Corner Latitude Point 2",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x08 0x01 0x00]],
   [:klv-key-corner-longitude-point-2 "Corner Longitude Point 2",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x0C 0x01 0x00]],
   [:klv-key-corner-latitude-point-3 "Corner Latitude Point 3",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x09 0x01 0x00]],
   [:klv-key-corner-longitude-point-3 "Corner Longitude Point 3",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x0D 0x01 0x00]],
   [:klv-key-corner-latitude-point-4 "Corner Latitude Point 4",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x0A 0x01 0x00]],
   [:klv-key-corner-longitude-point-4 "Corner Longitude Point 4",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x03 0x07 0x01 0x02 0x01 0x03 0x0E 0x01 0x00]],
   [:klv-key-device-absolute-speed "Device Absolute Speed",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x03 0x01 0x01 0x01 0x00 0x00]],
   [:klv-key-device-absolute-heading "Device Absolute Heading",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x03 0x01 0x01 0x02 0x00 0x00]],
   [:klv-key-absolute-event-start-date "Absolute Event Start Date",[0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x02 0x01 0x02 0x07 0x01 0x00 0x00]],
   [:klv-key-sensor-roll-angle "Sensor Roll Angle" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x07 0x01 0x10 0x01 0x01 0x00 0x00 0x00]],
   [:klv-key-sensor-relative-elevation-angle "Sensor Relative Elevation Angle" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x02 0x05 0x00 0x00 0x00]],
   [:klv-key-sensor-relative-azimuth-angle "Sensor Relative Azimuth Angle" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x02 0x04 0x00 0x00 0x00]],
   [:klv-key-sensor-relative-roll-angle "Sensor Relative Roll Angle" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x02 0x06 0x00 0x00 0x00]],
   [:klv-key-uas-lds-version-number "UAS LDS Version Number" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x02 0x03 0x03 0x00 0x00 0x00]],
   [:klv-key-generic-flag-data-01 "Generic Flag Data 01" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x03 0x01 0x00 0x00 0x00]],
   [:klv-key-static-pressure "Static Pressure" [0x06 0x0E 0x2B 0x34 0x01 0x01 0x01 0x01 0x0E 0x01 0x01 0x01 0x0F 0x00 0x00 0x00]]])

(def tags-table
  (map (fn [[sym name key]]
         [sym name (byte-array (ints->bytes key))])
       tags))


(defn find-klv-signature [^bytes key]
  (loop [tags tags-table]
    (if-let [tag (first tags)]
      (if (Arrays/equals ^bytes (tag 2) key)
        tag
        (recur (rest tags)))
      nil)))


(def local-set-tags
  (into
   {}
   (map
    (fn [[tag [desc & codec-args]]]
      [tag
       [desc
        (apply gloss/compile-frame codec-args)]])
    {1 [:checksum :uint16]
     2 [:unix-timestamp :uint64]
     3 [:mission-id (gloss/string :ascii)]
     4 [:platform-tail-number (gloss/string :ascii)]
     5 [:platform-heading :uint16 nil (scaler 0 65535 0 360)]
     6 [:platform-pitch :int16 nil pitch-scaler]
     7 [:platform-roll :int16 nil (scaler -32767 32767 -50 50)]
     8 [:platform-true-airspeed :ubyte]
     9 [:platform-indicated-airspeed :ubyte]
     10 [:platform-designation (gloss/string :ascii)]
     11 [:image-source-sensor (gloss/string :ascii)]
     12 [:image-coordinate-system (gloss/string :ascii)]
     13 [:sensor-lat :int32 nil lat-scaler]
     14 [:sensor-lon :int32 nil lon-scaler]
     15 [:sensor-true-alt :uint16 nil alt-scaler]
     16 [:sensor-horizontal-fov :uint16 nil (scaler 0 65535 0 180)]
     17 [:sensor-vertical-fov :uint16 nil (scaler 0 65535 0 180)]
     18 [:sensor-relative-azimuth :uint32 nil (scaler 0 4294967295 0 360)]
     19 [:sensor-relative-elevation :int32 nil (scaler -2147483647 2147483647 -180 180)]
     20 [:sensor-relative-roll :uint32 nil (scaler 0 4294967295 0 360)]
     21 [:slant-range :uint32 nil range-scaler]
     22 [:target-width :uint16 nil (scaler 0 65535 0 10000)]
     23 [:frame-center-lat :int32 nil lat-scaler]
     24 [:frame-center-lon :int32 nil lon-scaler]
     25 [:frame-center-elevation :uint16 nil alt-scaler]
     26 [:offset-corner-lat-point-1 :int16 nil pos-delta-scaler]
     27 [:offset-corner-lon-point-1 :int16 nil pos-delta-scaler]
     28 [:offset-corner-lat-point-2 :int16 nil pos-delta-scaler]
     29 [:offset-corner-lon-point-2 :int16 nil pos-delta-scaler]
     30 [:offset-corner-lat-point-3 :int16 nil pos-delta-scaler]
     31 [:offset-corner-lon-point-3 :int16 nil pos-delta-scaler]
     32 [:offset-corner-lat-point-4 :int16 nil pos-delta-scaler]
     33 [:offset-corner-lon-point-4 :int16 nil pos-delta-scaler]
     34 [:icing-detected :ubyte]
     35 [:wind-direction :uint16 nil (scaler 0 65535 0 360)]
     36 [:wind-speed :ubyte nil (scaler 0 255 0 100)]
     37 [:static-pressure :uint16 nil pressure-scaler]
     38 [:density-altitude :uint16 nil alt-scaler]
     39 [:outside-air-temp :byte]
     40 [:target-location-lat :int32 nil lat-scaler]
     41 [:target-location-lon :int32 nil lon-scaler]
     42 [:target-location-elevation :uint16 nil alt-scaler]
     43 [:target-track-gate-width :ubyte]
     44 [:target-track-gate-height :ubyte]
     45 [:target-error-estimate-ce90 :uint16]
     46 [:target-error-estimate-le90 :uint16]
     47 [:generic-flag-data :ubyte]
     ;; FIXME
     48 [:security-local-metadata-set :ubyte]
     49 [:differential-pressure :uint16 nil pressure-scaler]
     50 [:platform-aoa :int16 nil pitch-scaler]
     51 [:platform-vertical-speed :int16 nil (scaler -32767 32767 -180 180)]
     52 [:platform-sideslip-angle :int16 nil (scaler -32767 32767 -20 20)]
     53 [:airfield-baro-pressure :uint16 nil pressure-scaler]
     54 [:airfield-elevation :uint16 nil alt-scaler]
     55 [:relative-humidity :uint8 nil (scaler 0 255 0 100)]
     56 [:platform-ground-speed :ubyte]
     57 [:ground-range :uint32 nil range-scaler]
     58 [:platform-fuel-remaining :uint16 nil (scaler 0 65535 0 10000)]
     59 [:platform-call-sign (gloss/string :ascii)]
     60 [:weapon-load :uint16]
     61 [:weapon-fired :uint8]
     62 [:laser-prf-code :uint16]
     63 [:sensor-fov-name :ubyte]
     64 [:platform-magnetic-heading :uint16 nil (scaler 0 65535 0 360)]
     65 [:uas-ls-version-number :ubyte]
     ;; "TBD" in ST0601.8 spec.
     66 [:target-location-covariance-matrix :ubyte]
     67 [:alternate-platform-lat :int32 nil lat-scaler]
     68 [:alternate-platform-lon :int32 nil lon-scaler]
     69 [:alternate-platform-alt :uint16 nil alt-scaler]
     70 [:alternate-platform-name (gloss/string :ascii)]
     71 [:alternate-platform-heading :uint16 nil (scaler 0 65535 0 360)]
     72 [:event-start-time-utc :uint64]
     })))


(defn -main [& args]
  (-> args
      first
      xio/binary-slurp
      decode-basic-universal-dataset
      pprint/pprint))
