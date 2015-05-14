(ns com.lemondronor.droneklv-test
  (:require [clojure.test :refer :all]
            [com.lemondronor.droneklv :as droneklv]))


(defn b [bytes]
  (byte-array (droneklv/ints->bytes bytes)))


(defn a= [a b eps]
  (< (Math/abs (- a b)) eps))


(deftest local-set-tag-parsing
  (testing "unix timestamp"
    (let [[offset tag] (droneklv/parse-local-set-tag
                        (b [0x02 0x08 0x00 0x04 0x59 0xF4 0xA6 0xAA 0x4A 0xA8]))]
      (is (= 10 offset))
      (is (= [:unix-timestamp 1224807209913000N] tag))
      (is (= #inst "2008-10-24T00:13:29.913-00:00"
             (java.util.Date. (long (/ (second tag) 1000.0)))))))
  (testing "mission ID"
    (is (= [11 [:mission-id "MISSION01"]]
           (droneklv/parse-local-set-tag
            (b [0x03 0x09 0x4D 0x49 0x53 0x53 0x49 0x4F 0x4E 0x30 0x31])))))
  (testing "platform tail number"
    (is (= [8 [:platform-tail-number "AF-101"]]
           (droneklv/parse-local-set-tag
            (b [0x04 0x06 0x41 0x46 0x2D 0x31 0x30 0x31])))))
  (testing "platform heading"
    (let [[off [tag angle]] (droneklv/parse-local-set-tag
                             (b [0x05 0x02 0x71 0xC2]))]
      (is (= 4 off))
      (is (= :platform-heading tag))
      (is (a= 159.9744 angle 0.001))))
  (testing "platform pitch"
    (let [[off [tag angle]] (droneklv/parse-local-set-tag
                             (b [0x06 0x02 0xFD 0x3D]))]
      (is (= 4 off))
      (is (= :platform-pitch tag))
      (is (a= -0.4315251 angle 0.001))))
  (testing "platform roll"
    (let [[off [tag angle]] (droneklv/parse-local-set-tag
                             (b [0x07 0x02 0x08 0xB8]))]
      (is (= 4 off))
      (is (= :platform-roll tag))
      (is (a= 3.405814 angle 0.001)))))
