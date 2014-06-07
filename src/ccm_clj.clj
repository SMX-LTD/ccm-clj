(ns ccm-clj
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell2 :as shell]
            [clojure.tools.logging :as log]
            [ccm-clj.impl :refer :all])
  (:import (java.util.concurrent ScheduledThreadPoolExecutor TimeUnit)
           (java.io File Reader)
           (java.net URL)))

;;;;;;;;;;;;;
;;; Public

(def default-keyspace (atom nil))

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
  "Get a map of the conf for the node `name` from active cluster"
  ([name]
   {:pre (= (get-active-cluster) nil)}
   (conf-as-map (io/file ccm-dir (get-active-cluster) name (str name ".conf")))))

(defn start!
  "Start CCM cluster `name`."
  [name]
  (let [result (ccm "start")]
    (log/info (str name " cluster started"))
    result))

(defn cql!
  "Execute cqlsh cmd (against 'node1') in keyspace `keyspace` from cmd-source (can be File, String or URL) into active cluster.
  Note this is a convienent way of loading schemas, and shouldn't be used in place of a proper CQL client like Alia or Cassaforte."
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
  (re-matches (re-pattern (str "(?s)" ".*?\\b" name "\\b.*?")) (:out (ccm "list" :quiet))))

(defn new!
  "Create and start cluster, there will be nodes running on `cql-port` from 127.0.0.1 to 127.0.0.n.
  Those loopbacks may need to be aliased depending on OS.
  Ports from cql-port to cql-port + 3 will be used."
  [name version num-nodes cql-port]
  (let [cluster-dir (io/file ccm-dir name)
        cluster-exists (.exists cluster-dir)]
    (if (not (cluster? name))
      (do
        (log/info "Creating new cluster (may require source downloading and building)" name)
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


