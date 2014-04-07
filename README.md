# ccm-clj

A Clojure interface to Cassandra Cluster Manager (https://github.com/pcmanus/ccm) suitable for use in integration tests.
It is not a recommended for use as a CQL client (see https://github.com/mpenet/alia).


## Current Version

https://clojars.org/com.smxemail/ccm-clj/latest-version.svg

## Usage

```clojure
(if (not (ccm/cluster? "testcluster"))
  (do
    (ccm/new! "testcluster" cass-version num-nodes cql-port)
    (ccm/cql! (io/file "./test/resources/test-keyspace.cql") nil "Keyspace")
    (ccm/cql! (io/resource "schema/test-schema.cql") "testkeyspace" "Schema")
    (ccm/cql! (io/file "./test/resources/test-data.cql") "testkeyspace" "Data"))
  (do
    (ccm/switch! "testcluster")
    (ccm/start! "testcluster")))
```

For more see ;;Public in [`src/ccm-clj.clj`](ccm-clj.clj) or tests in [`test/ccm-clj-test.clj`](ccm-clj-test.clj).
Note: tests run via:
```clojure
lein all expectations
```

## Contact

Email Colin Taylor courtesy gmail.

## License

Copyright © 2014 SMX Ltd (http://smxemail.com) and Contributors.

Distributed under the Eclipse Public License.
