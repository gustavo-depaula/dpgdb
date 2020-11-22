(ns dpgdb.core
  (:require [clojure.string :as str])
  (:gen-class))

(def MAX-SEGMENT-ENTRIES 25)
(def segment-counter (atom {0 0}))
(def value-refs (atom {}))

(defn read-nth-line
  "Read line-number from the given text file. The first line has the number 1."
  [file line-number]
  (with-open [rdr (clojure.java.io/reader file)]
    (nth (line-seq rdr) (dec line-number))))

(defn get-segment-filename [index]
  (str "db-files/segment-" index ".log"))

(defn get-value [key]
  (let [refs (deref value-refs)
        value-ref (get refs key nil)]
    (if (nil? value-ref)
      nil
      (let [line-number (:line value-ref)
            segment (get-segment-filename (:segment value-ref))
            line (read-nth-line segment line-number)
            line-breakdown (str/split line #"\ +")
            value (get line-breakdown 2)]
        value))))

(defn save-kv-pair [key value]
  (let [segments (keys (deref segment-counter))
        max-segment (apply max segments)
        max-segment-entries (get (deref segment-counter) max-segment)

        segment (if (>= max-segment-entries MAX-SEGMENT-ENTRIES) (inc max-segment) max-segment)
        segment-entries (get (deref segment-counter) segment 0)
        appended-line (inc segment-entries)

        segment-filename (get-segment-filename segment)
        entry (str "set " key " " value "\n")]

    (spit segment-filename entry :append true)
    (swap! value-refs assoc key {:line appended-line
                                 :segment segment})
    (swap! segment-counter assoc segment appended-line)))

(defn kv-dict-in-logfile [index]
  (let [segment-filename (get-segment-filename index)
        file-content (slurp segment-filename)
        lines (str/split file-content #"\n")
        kv-pairs (map
                  (fn  [line]
                    (let [breakdown (str/split line #"\ +")
                          key (get breakdown 1)
                          value (get breakdown 2)]
                      (vec [key value])))
                  lines)
        values-dict (into (sorted-map) kv-pairs)]
    values-dict))

(defn keys-in-segment [index]
  (let [refs (deref value-refs)
        keys (keys refs)
        keys-in-segment (filter #(= (str index)  (str (:segment (get refs %)))) keys)]
    keys-in-segment))

(defn compact-file [index]
  (let [kv-pairs-in-file (kv-dict-in-logfile index)
        ks-in-segment (keys-in-segment index)

        segments (keys (deref segment-counter))
        max-segment (apply max segments)]
    (swap! segment-counter assoc (inc max-segment) 0)
    (doseq [keyval kv-pairs-in-file] (save-kv-pair (key keyval) (val keyval)))
    (clojure.java.io/delete-file (get-segment-filename index))
    (println (str "compacted segment file " (get-segment-filename index) " into " (get-segment-filename (inc max-segment))))))

(defn process-command [command args]
  (when (= command "set")
    (let [key (get args 0)
          value (get args 1)]
      (save-kv-pair key value)
      (println "key" key "setted to" value)))
  (when (= command "get")
    (let [key (get args 0)]
      (println (get-value key))))
  (when (= command "compact")
    (let [index (get args 0)]
      (compact-file index))))

(defn repl []
  (while true
    (print "> ")
    (flush)
    (def input (read-line))
    (def input-breakdown (str/split input #"\ +"))
    (def command (get input-breakdown 0))
    (process-command command (subvec input-breakdown 1))))

(defn cache-segment-file-entries [filename]
  (let [file-content (slurp filename)]))

(defn cache-log-entries [])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (repl))
