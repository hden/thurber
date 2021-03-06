(ns word-count.basic
  (:require [thurber :as th]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (org.apache.beam.sdk.io TextIO)
           (org.apache.beam.sdk.transforms Count)
           (org.apache.beam.sdk.values KV)))

;; Simple Clojure functions can serve as Beam DoFns.
;;
;; When such a function evaluates to a Clojure sequence, each value within the
;; sequence is output downstream as an element.
;;
;; By using lazy Clojure sequences, we can produce many elements
;; with minimal memory consumption.
(defn- extract-words [sentence]
  (remove empty? (str/split sentence #"[^\p{L}]+")))

;; When a function evaluates to a simple single value like a String,
;; this single value is emitted downstream. Note that we annotate
;; our function with the coder to use; otherwise it would wrongly
;; inherit the incoming KvCoder.
(defn- ^{:th/coder th/nippy} format-as-text [^KV kv]
  (let [[k v] (th/kv->clj* kv)]
    (format "%s: %d" k v)))

(defn- sink* [elem]
  (log/info elem))

;; A reusable transform.
(def count-words-xf
  (th/compose
    "count-words"
    #'extract-words
    #'th/->kv
    (Count/perKey)))

(defn- build-pipeline! [pipeline]
  (let [conf (th/get-custom-config pipeline)]
    (th/apply! pipeline
      (-> (TextIO/read)
        (.from ^String (:input-file conf)))
      count-words-xf
      #'format-as-text
      #'sink*)
    pipeline))

(defn demo! []
  (->
    (th/create-pipeline
      ;; Thurber fully supports Beam's PipelineOptions and static Java interfaces.
      ;;
      ;; Thurber also supports Clojure/EDN maps for providing options; core Beam
      ;; options are provided by their standard names (as skeleton case); config
      ;; unique to your pipeline can be specified under :custom-config.
      ;;
      ;; Config provided this way must be serializable to JSON (per Beam).
      {:target-parallelism 25
       :custom-config {:input-file "demo/word_count/lorem.txt"}})
    build-pipeline! .run))
