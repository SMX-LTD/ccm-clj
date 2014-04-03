(ns ccm-clj-test
  (:require [clojure.java.io :as io]
            [expectations :refer :all]
            [ccm-clj :refer :all]))

(def existing (get-clusters))
(def current (get-active-cluster))

(defn tidy-up {:expectations-options :after-run} []
  (ccm-clj/remove! "ccmcljtest1")
  (if current (switch! current)))

;todo check :err expect-no-err ?
(expect (new! "ccmcljtest1" "2.0.4" 3 19111))

;cql as file
(expect (cql! (io/file "./test/resources/test-keyspace.cql")))
;cql as url, keyspace in file
(expect (cql! (io/resource "test-schema.cql")))
;file with given keyspace
(expect (cql! (io/file "./test/resources/test-data.cql") "ccmclj"))

(expect (set-default-keyspace! "ccmclj"))
;load string using default keyspace - and trim surplus ; which ccm spews on   ;todo still doesnt work?
;(expect (cql! "update testtable set data = '22' where id = 2; insert into testtable (id, data) values (3, '2')"));
;(expect (slurp (io/file "./test/resources/test-data2.table")) (cql! "select * from testtable"))

(expect (set ["node1" "node2" "node3"]) (set (:nodes (get-cluster-conf))))

(expect "ccmcljtest1" (get-active-cluster))
(expect (conj (set existing) "ccmcljtest1") (set (get-clusters)))
(expect (cluster? "ccmcljtest1"))
(expect "ccmclj" (get-default-keyspace))
(expect (not (cluster? "ccmcljtest2")))

(expect (new! "ccmcljtest2" "1.2.0" 1 19212))
(expect "ccmcljtest2" (get-active-cluster))
(expect (remove! "ccmcljtest2"))
(expect nil (get-active-cluster))

(expect (switch! "ccmcljtest1"))
(expect (stop!))
(expect (start! "ccmcljtest1"))
(expect (switch! "ccmcljtest1"))
(expect "ccmcljtest1" (get-active-cluster))

(expect (exec! "add" "-r 0" "-t 127.0.0.4:19112" "-j 19114" "-l 127.0.0.4:19114" "--binary-itf=127.0.0.4:19111" "node4"))
(expect (hash-set "node1" "node2" "node3" "node4") (set (:nodes (get-cluster-conf))))

(expect (exec! "node4" "remove"))
(expect (hash-set "node1" "node2" "node3") (set (:nodes (get-cluster-conf))))

