(ns ccm-clj.impl
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell2 :as shell]
            [clojure.tools.logging :as log])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]
           [java.io File Reader]
           [java.net URL ServerSocket]
           [java.util Properties]))

;;;;;;;;;;;;;
;;; Impl

(def ccm-dir (io/file (.getProperty ^Properties (System/getProperties) "user.home") ".ccm"))

(defn ccm
  [& cmd]
  (let [quiet (some #{:quiet} cmd)
        cmd* (vec (filter #(not= :quiet %) cmd))
        r (apply shell/sh "ccm" cmd*)
        exit (:exit r)
        out (str/trim (.replace (:out r) "\\\\" "\\"))
        err (str/trim (.replace (:err r) "\\\\" "\\"))]
    (if-not quiet (log/info "cmd:" cmd*))
    (if (and (not quiet) (not= out "") (not (.contains (:out r) "JavaLaunchHelper"))) ;java logging bug
      (log/info (str "CCM => " (str/trim out))))
    (if (not= err "")
      (log/error (str "CCM => " (str/trim err))))
    (if (not= exit 0)
      (throw (RuntimeException. (str "CCM failure [" exit "]:" (str/trim (:err r)) " cmd:" cmd*))))
    r))

(defn conf-as-map [conf-file]
  ;todo this is pretty suss
  ;Python config parsers do not guess datatypes of values in configuration files, always storing them internally as string,
  ;BUT we'll keyword cos we can't help ourselves."
  {:pre (true? (and (not (nil? conf-file)) (.exists conf-file)))}
  (apply array-map (mapcat
                     (fn [line]
                       (let [k (subs line 0 (inc (.indexOf line ":")))
                             v (subs line (inc (.indexOf line ":")))]      ;todo comments multi-lines
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


