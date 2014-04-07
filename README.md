# ccm-clj

A Clojure interface to Cassandra Cluster Manager (https://github.com/pcmanus/ccm) suitable for use in integration tests.
It is not a recommended for use as a CQL client (see https://github.com/mpenet/alia).


## Current Version

Artifacts are released to Clojars. If you are using Maven, add the following repository definition to your pom.xml:

<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>

With Leiningen:

```clojure
[clojurewerkz/money "0.1.1"]
```

With Maven:

```xml
<dependency>
  <groupId>com.smxemail</groupId>
  <artifactId>ccm-clj</artifactId>
  <version>0.1.1</version>
</dependency>
```

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

For more see ;;Public in [`src/ccm_clj.clj`](src/ccm_clj.clj) or tests in [`test/ccm_clj_test.clj`](test/ccm-clj_test.clj).

Note: tests run via:

```clojure
lein all expectations
```

## Contact

Email Colin Taylor courtesy gmail.

## License

Copyright © 2014 SMX Ltd (http://smxemail.com) and Contributors.

Distributed under the Eclipse Public License.

