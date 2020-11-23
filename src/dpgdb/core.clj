(ns dpgdb.core
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]])
  (:gen-class))


(defn read-nth-line
  "Read line-number from the given text file. The first line has the number 1."
  [file line-number]
  (with-open [rdr (clojure.java.io/reader file)]
    (nth (line-seq rdr) (dec line-number))))


(def entries-refs (atom {}))
(defn make-entry
  ([key value] (str key " " value "\n"))
  ([line] (str line "\n")))
(defn get-entry-key [entry] (get (str/split entry #"\ +") 0))
(defn get-entry-value [entry] (get (str/split entry #"\ +") 1))


(def MAX-SEGMENT-ENTRIES 25)
(def segment-counter (atom {}))
(defn get-segment-filename [index]
  (str "db-files/segment-" index ".log"))

(defn add-new-segment
  ([] (add-new-segment -1))
  ([default]
   (let [new-segment-index (->> @segment-counter
                                keys
                                (#(if (nil? %) [default] %))
                                (apply max)
                                inc)]
     (swap! segment-counter assoc new-segment-index 0)
     new-segment-index)))

(defn delete-segments [& indexes]
  (doseq [index indexes] (clojure.java.io/delete-file (get-segment-filename index))))

(defn get-all-segments-indexes []
  (let [directory (clojure.java.io/file "db-files")
        files (.listFiles directory)
        filenames (map #(.getName %) files)
        segmentfiles (filter #(str/starts-with? % "segment-") filenames)
        indexes (map #(str/replace % #"segment-|.log" "") segmentfiles)]
    indexes))


(defn read-entry [key]
  (let [entry-ref (get @entries-refs key nil)]
    (if (nil? entry-ref)
      nil
      (let [line-number (:line entry-ref)
            segment (get-segment-filename (:segment entry-ref))
            entry (read-nth-line segment line-number)
            value (get-entry-value entry)]
        value))))

(defn read-all-entries-in-segment [index]
  (let [segment-filename (get-segment-filename index)
        file-content (slurp segment-filename)
        lines (str/split file-content #"\n+")
        entries (map make-entry lines)]
    entries))


(defn which-segment-to-write-new-entry-in []
  (let [segments-indexes (keys @segment-counter)
        highest-index (apply max segments-indexes)
        highest-index-segment-entries (get (deref segment-counter) highest-index)

        segment-index (if (>= highest-index-segment-entries MAX-SEGMENT-ENTRIES)
                        (inc highest-index)
                        highest-index)
        segment-entries-count (get (deref segment-counter) segment-index -1)]
        
    {:segment-index segment-index
     :entries-count segment-entries-count}))

(defn save-entry [entry]
  (let [{:keys [segment-index entries-count]} (which-segment-to-write-new-entry-in)
        appended-line (inc entries-count)
        segment-filename (get-segment-filename segment-index)
        key (get-entry-key entry)]

    (spit segment-filename entry :append true)
    (swap! entries-refs assoc key {:line appended-line
                                   :segment segment-index})
    (swap! segment-counter assoc segment-index appended-line)))

(defn save-entries [entries]
  (doseq [entry entries] (save-entry entry)))


(defn compact-entries-list [entries]
  (let [entries-map (reduce #(assoc %1 (get-entry-key %2) %2)
                            {}
                            entries)
        compacted-entries (vals entries-map)]
    compacted-entries))

(defn compact-segment [index]
  (let [entries (-> index
                    read-all-entries-in-segment
                    compact-entries-list)
        new-segment-index (add-new-segment)]
    (save-entries entries)
    (delete-segments index)
    (println (str "compacted segment file " (get-segment-filename index) " into " (get-segment-filename new-segment-index)))))


(defn get-merged-segments-entries [& indexes]
  (let [entries-lists (map read-all-entries-in-segment indexes)
        entries-list (apply concat entries-lists)
        merged-entries-list (compact-entries-list entries-list)]
    merged-entries-list))

(defn merge-segments [indexes]
  (let [sorted-indexes (sort (map #(Integer/parseInt %) indexes))
        entries (apply get-merged-segments-entries sorted-indexes)
        new-segment-index (add-new-segment (apply max sorted-indexes))]
    (save-entries entries)
    (apply delete-segments sorted-indexes)
    (add-new-segment)
    (println (str "merged segments " (vec indexes) " into new segment " new-segment-index))))


(defn load-memcache
  "Read logfiles and merge them, thus setting the memcache atoms"
  []
  (let [segments (get-all-segments-indexes)]
    (if (> (count segments) 0)
      (merge-segments segments)
      (do
        (add-new-segment)
        (println "nothing to merge!")))))

(defn process-command [command args]
  (when (= command "set")
    (let [key (get args 0)
          value (get args 1)]
      (save-entry (make-entry key value))
      (println "key" key "setted to" value)))
  (when (= command "get")
    (let [key (get args 0)]
      (println (read-entry key))))
  (when (= command "compact")
    (let [index (get args 0)]
      (compact-segment index)))
  (when (= command "merge")
    (let [indexes args]
      (merge-segments indexes)))
  (when (= command "debug")
    (let [key (get args 0)]
      (println "segment-counter: ")
      (pprint @segment-counter)
      (println "entries refs: ")
      (pprint @entries-refs))))

(defn repl []
  (while true
    (print "> ")
    (flush)
    (def input (read-line))
    (def input-breakdown (str/split input #"\ +"))
    (def command (get input-breakdown 0))
    (process-command command (subvec input-breakdown 1))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (load-memcache)
  (repl))
