(ns ccm-clj
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell2 :as shell]
            [clojure.tools.logging :as log])
  (:import (java.util.concurrent ScheduledThreadPoolExecutor TimeUnit)
           (java.io File Reader)
           (java.net URL)))


;;;;;;;;;;;;;
;;; Impl

(def default-keyspace (atom nil))

(def ccm-dir (io/file (.getProperty (System/getProperties) "user.home") ".ccm"))

(defn- ccm
  [& cmd]
  (let [quiet (some #{:quiet} cmd)
        cmd* (vec (filter #(not= :quiet %) cmd))
        r (apply shell/sh "ccm" cmd*)]
    (log/debug "cmd: " cmd*)
    (if (and (not quiet) (not= (:out r) "") (not (.contains (:out r) "JavaLaunchHelper"))) ;java logging bug
      (log/info (str "CCM=> " (str/trim (:out r)))))
    (if (not= (:err r) "")
      (log/error (str "CCM=> " (str/trim (:err r)))))
    (if (not= (:exit r) 0)
      (throw (RuntimeException. (str "CCM failure: " (str/trim (:err r)) " cmd:" cmd*))))
    r))

(defn- conf-as-map [conf-file]
  ;Config parsers do not guess datatypes of values in configuration files, always storing them internally as string,
  ;BUT we'll keyword cos we can't help ourselves."
  (apply array-map (mapcat
                     (fn [line]
                       (let [k (subs line 0 (inc (.indexOf line ":")))
                             v (subs line (inc (.indexOf line ":")))]
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

(extend-protocol CCMCoercions
  nil
  (as-cqlsh-arg [_] (throw (IllegalArgumentException. "Nil arg to cqlsh")))
  (to-str [_] "")
  ;String    ;ccm bug doenst like trailing ';' ?
  ;(as-cqlsh-arg [x] [(str "-x "  "\"" (if (.endsWith x ";") (subs x 0 (dec (.length x)))  x) "\"" " -v")])
  ;(to-str [x] (subs x 0 (min (.length x) 100)))

  File
  (as-cqlsh-arg [x] ["--file" (.getAbsolutePath x)])
  (to-str [x] (.getAbsolutePath x))

  URL
  (as-cqlsh-arg [x] ["--file" (let [content (slurp x)
                                    tmpFile (File/createTempFile (str x) nil)]
                                (spit tmpFile content)
                                (.getAbsolutePath tmpFile))])
  (to-str [x] (.toString x))

  ;Reader     ;ccm bug ?
  ;(as-cqlsh-arg [x] [(str "-x "  "\"" (let [c (slurp x)] (if (.endsWith c ";") (subs c 0 (dec (.length c))) c)) "\"" " -v")])
  ;(to-str [x] x)
  )

(defn- coerce-as-cqlsh-arg [thing]
  ;todo use pipe redirects ?
  (condp = (type thing)
    File ["--file" (.getAbsolutePath thing)]
    URL ["--file" (let [content (slurp thing)
                        tmpFile (File/createTempFile (str thing) nil)]
                    (spit tmpFile content)
                    (.getAbsolutePath tmpFile))]
    Reader ["-x " (str "\"" (slurp thing) "\"") "-v"]
    String ["-x " (str "\"" thing "\"") "-v"]
    nil (throw (IllegalArgumentException. "Nil arg to cqlsh"))
    (throw (IllegalArgumentException. (str "Unknown type " (type thing) " for cql! arg " thing)))))

(declare stop!)

;;;;;;;;;;;;;
;;; Public

(defn exec!
  "Execute ccm `cmd`, returns {:err <stderr messages>, :out <stdout messages>, :exit <return code>}"
  [cmd & args]
  (apply ccm cmd args))

(defn get-active-cluster
  "Get name of active cluster"
  []
  (if (.exists (io/file ccm-dir "CURRENT"))
    (str/trim (slurp (io/file ccm-dir "CURRENT")))
    nil))

(defn set-default-keyspace!
  "Set default keyspace"
  [keyspace]
  (reset! default-keyspace keyspace))

(defn get-default-keyspace
  "Get name of default cluster"
  []
  @default-keyspace)

(defn get-cluster-conf
  "Get a map of the cluster conf"
  ([]
   (if (get-active-cluster) (get-cluster-conf (get-active-cluster)) nil))
  ([name]
   (conf-as-map (io/file ccm-dir name "cluster.conf"))))

(defn get-node-conf
  "Get a map of the node conf `name`"
  ([name]
   {:pre (= (get-active-cluster) nil)}
   (get-node-conf (get-active-cluster) name))
  ([cluster name]
   (conf-as-map (io/file ccm-dir cluster name (str name ".conf")))))

(defn start!
  "Start CCM cluster `name`."
  [name]
  (let [result (ccm "start")]
    (log/info (str name " cluster started"))
    result))

(defn cql!
  "Execute cqlsh cmd (against 'node1') in keyspace `keyspace` from cmd-source (can be File, String or URL) into active cluster."
  ([cmd-source]
   (cql! cmd-source @default-keyspace "" "node1"))
  ([cmd-source keyspace]
   (cql! cmd-source keyspace "" "node1"))
  ([cmd-source keyspace log-name]
   (cql! cmd-source keyspace log-name "node1"))
  ([cmd-source keyspace log-name node-name]
   (let [thing cmd-source
         result (apply ccm (concat (if keyspace [node-name "cqlsh" "-k" keyspace] [node-name "cqlsh"]) (as-cqlsh-arg thing)))]
     (log/info (str log-name " load finished of " (to-str cmd-source) " (check for errors in output)"))
     result)))

(defn switch!
  "Switch active cluster to `name`"
  [name]
  (let [result (ccm "switch" name)]
    (log/info (str "Switch active cluster to " name))
    result))

(defn get-clusters
  "Returns a vector of clusters in CCM"
  []
  (vec (re-seq #"[^\n\s*]+" (:out (ccm "list" :quiet)))))

(defn stop!
  "Stop the current CCM cluster."
  []
  (let [result (ccm "stop")]
    (log/info (str (get-active-cluster) " cluster stopped"))
    result))

(defn remove!
  "Remove cluster `name` from CCM including all data."
  [name]
  (let [result (ccm "remove" name)]
    (log/info (str name " cluster removed"))
    result))

(defn cluster?
  "Is `name` found in list of CCM clusters."
  [name]
  ;Correct by CCM but do we go further?
  (re-matches (re-pattern (str "(?s)" ".*?\\b" name "\\b.*?")) (:out (ccm "list" :quiet))))

(defn new!
  "Create and start cluster, there will be nodes running on `cql-port` from 127.0.0.1 to 127.0.0.n.
  Those loopbacks may need to be aliased depending on OS.
  Ports from cql-port to cql-port + 3 will be used."
  [name version num-nodes cql-port]
  (let [cluster-dir (io/file ccm-dir name)
        cluster-exists (.exists cluster-dir)]
    (if (not (cluster? `name))
      (do
        (log/info "Creating new cluster" name)
        (ccm "create" name "-v" version)
        (doseq [i (range 1 (inc num-nodes))
                :let [ip (str "127.0.0." i)
                      node-name (str "node" i)
                      remote-debug "0"
                      thrift (str ip ":" (inc cql-port))
                      jmx (str (+ cql-port 2 i))
                      storage (str ip ":" (+ cql-port 3))
                      cql (str ip ":" cql-port)]]
          (ccm "add"
               "-r" remote-debug
               "-t" thrift
               "-j" jmx
               "-l" storage
               (str "--binary-itf=" cql)
               node-name)
          (log/info "Added node " node-name "@" (str ip ":" cql-port))))
      (log/info "Found existing cluster at" cluster-dir))
    (start! name)
    (switch! name)))

