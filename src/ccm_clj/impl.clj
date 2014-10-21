(ns ccm-clj.impl
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell2 :as shell]
            [clojure.tools.logging :as log])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]
           [java.io File Reader StringWriter]
           [java.net URL ServerSocket]
           [java.util Properties]))

;;;;;;;;;;;;;
;;; Impl

(def ccm-dir (io/file (.getProperty ^Properties (System/getProperties) "user.home") ".ccm"))
(def savepoint-dir (io/file ccm-dir "savepoints"))
(if (not (.exists savepoint-dir)) (.mkdir savepoint-dir))

(defonce default-keyspaces (atom {}))
(def default-base-port 19100)
(def jmx-increment (atom 100))

(defn filter-mods [cmd*]
  (let [[mods cmd] ((juxt filter remove) #{:quiet :quiet! :warn-only} cmd*)]
    [(set mods) cmd]))

(defn sh-exec
  [& cmd]
  (let [[mods cmd*] (filter-mods cmd)
        ;remove lein? prop from env that inteferes with ant
        cmd* (concat cmd* [:env (-> (into {} (System/getenv)) (dissoc "classpath" "CLASSPATH"))])
        r (apply shell/sh cmd*)
        r (-> r
              (assoc :cmd (butlast (butlast cmd*)))         ;remove env from logging
              (assoc :out (str/trim (.replace (:out r) "\\\\" "\\")))
              (assoc :err (str/trim (.replace (:err r) "\\\\" "\\"))))
        exit (:exit r)]
    (when-not (or (:quiet! mods) (= exit 0))
      (if (:warn-only mods)
        (log/warn "sh.exit =" exit)
        (log/error "sh.exit =" exit)))
    (when-not (or (:quiet! mods) (= (:err r) "") (.contains (:err r) "JavaLaunchHelper"))
      (if (:warn-only mods)
        (log/warn "sh.<err> =>" (:err r))
        (log/error "sh.<err> =>" (:err r))))
    r))

(defn ccm [& cmd]
  (let [[mods cmd*] (filter-mods cmd)
        _ (if-not (or (some mods [:quiet :quiet!]))
            (log/info (apply str "sh <= ccm " (str/join " " (map name cmd*)))))
        r (apply sh-exec "ccm" cmd)]
    (if (and (not (:quiet! mods)) (not= (:exit r) 0))
      (throw (ex-info (str "ccm.<err> => " (str/trim (:err r))) r))
      r)))

(defn copy-dir
  "Assumes parents all exist"
  [from-dir to-dir]
  (sh-exec "cp" "-r" (.getAbsolutePath from-dir) (.getAbsolutePath to-dir) :quiet))

(defn del-dir
  ([dir]
   (try (sh-exec "rm" "-r" (.getAbsolutePath dir) :quiet)
        (catch Exception e (throw e)))))

(defn sync-dir
  "Assumes parents all exist, syncs contents of `from-dir with `to-dir`"
  [from-dir to-dir]
  (sh-exec "rsync" "-avq" "--delete" (str (.getAbsolutePath from-dir) "/") (.getAbsolutePath to-dir)))

(defn get-active []
  (let [current (io/file ccm-dir "CURRENT")]
    (if (.exists current)
      (str/trim (slurp current))
      nil)))

(defn ensure-active []
  (if (get-active)
    true
    (do
      (log/error "No active cluster")
      false)))

(defn conf-as-map [conf-file]
  ;todo this is pretty suss
  ;Python config parsers do not guess datatypes of values in configuration files, always storing them internally as string,
  ;BUT we'll keyword cos we can't help ourselves."
  {:pre (true? (and (not (nil? conf-file)) (.exists conf-file)))}
  (apply array-map (mapcat
                     (fn [line]
                       (let [k (subs line 0 (inc (.indexOf line ":")))
                             v (subs line (inc (.indexOf line ":")))] ;todo comments multi-lines
                         (letfn [(realize [i]
                                          (let [i (str/trim i)]
                                            (cond
                                              (.startsWith i "[") (-> (vec (map realize (re-seq #"[^\[\],]+" i))))
                                              (.startsWith i "{") (-> (apply array-map (map realize (re-seq #"[^\{\},:]+" i))))
                                              (.endsWith i ":") (keyword (subs i 0 (dec (.length i))))
                                              (= i "null") nil
                                              :else i)))]
                           [(realize k) (realize v)])))
                     (re-seq #"[^\n]+" (slurp conf-file)))))

(defprotocol CCMCoercions
  "Coerce to cqlsh args."
  (as-cqlsh-arg [x] "Coerce argument to a file.")
  (to-str [_] "For logging convienence"))

(defn string-as-tmp-file [string]
  (let [tmpFile (File/createTempFile (str (.hashCode string)) nil)]
    (spit tmpFile (if (.endsWith string ";") string (str string ";")))
    tmpFile))


(extend-protocol CCMCoercions
  nil
  (as-cqlsh-arg [_] (throw (IllegalArgumentException. "Nil arg to cqlsh")))
  (to-str [_] "")
  File
  (as-cqlsh-arg [x] ["--file" (.getAbsolutePath x)])
  (to-str [x] (.getAbsolutePath x))
  URL
  (as-cqlsh-arg [x] (as-cqlsh-arg (string-as-tmp-file (slurp x))))
  (to-str [x] (.toString x))
  ;ccm is borked for string args gonna cheat
  String
  (as-cqlsh-arg [x] (as-cqlsh-arg (string-as-tmp-file x)))
  (to-str [x] (subs x 0 (min (.length x) 100)))
  Reader
  (as-cqlsh-arg [x] (as-cqlsh-arg (slurp x)))
  (to-str [x] "<reader>"))


