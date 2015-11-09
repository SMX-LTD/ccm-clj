# CCM-clj

A Clojure (1.6+) interface to Cassandra Cluster Manager (https://github.com/pcmanus/ccm) suitable for use in integration tests and creating arbitrary Cassandra Clusters.
CCM-clj is courtesy of SMX (http://smxemail.com) where we have been using it for a couple of years to simplify development with Cassandra and Clojure.
As a JVM library, it is however easily used with other languages [http://clojure.github.io/clojure/javadoc/].

## Current Version

**Note: breaking change with 1.0. The ns moved from ccm-clj to ccm-clj.core for better Java integration (no longer using empty package).**

Artifacts are released to Clojars. If you are using Maven, add the following repository definition to your pom.xml:


```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

With Leiningen:

```clojure
[com.smxemail/ccm-clj "1.1.0"]
```

With Maven:

```xml
<dependency>
  <groupId>com.smxemail</groupId>
  <artifactId>ccm-clj</artifactId>
  <version>1.1.0</version>
</dependency>
```

### CCM installation

On OSX:
```bash
brew install ccm
```
Otherwise see Requirements and Installation at https://github.com/pcmanus/ccm x`

Note that local loopback aliasing may be required on OSX.
I have /Library/LaunchDaemons/moreloopbacks.plist:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>MORE_LOOPBACKS</string>
	<key>ProgramArguments</key>
	<array>
		<string>/usr/local/scripts/moreloopbacks.sh</string>
	</array>
	<key>RunAtLoad</key>
	<true/>
	<key>UserName</key>
	<string>ROOT</string>
	<key>GroupName</key>
	<string>WHEEL</string>
</dict>
</plist>
```

And then /usr/local/scripts/moreloopbacks.sh:
```bash
#!/bin/bash
for ((i=2;i<6;i++))
do
  sudo ifconfig lo0 alias 127.0.0.$i up
done
```

## Usage

One line test cluster setup with  `auto-cluster!`, args: name, version, number-of-nodes, map-of-keyspace-to-cql-re-paths

```clojure
(ccm/auto-cluster! "test-cluster" "2.0.14" 3 ["schema/test-keyspace.cql"
                      {"my-keyspace" [#"schema/test.*schema.cql" #"schema/test.*data.cql"]})

```

Or for full fine-grained control:

```clojure
(ns some-cassandra-test
  (:require [ccm-clj :as ccm]
            [clojure.java.io :as io]))

(if (not (ccm/cluster? "testcluster"))
  (do
    (ccm/new! "testcluster" cass-version num-nodes cql-port)
    (ccm/cql! (io/file "./test/resources/test-keyspace.cql"))
    (ccm/cql! (io/resource "schema/test-schema.cql") "testkeyspace")
    (ccm/cql! (io/file "./test/resources/test-data1.cql") "testkeyspace")
    (ccm/cql! (io/file "./test/resources/test-data2.cql") "testkeyspace"))
  (do
    (ccm/start! "testcluster")))
....
(ccm/remove! "testcluster")
```

## Savepoints (New to 1.0):

```clojure
(cql! "insert into testtable (id, data) values (99, 'tosurvive');")
(savepoint! "testsavepoint"))
(cql! "insert into testtable (id, data) values (100, 'torollback');")
(restore! "testsavepoint"))
```

## Notes

- This is NOT a CQL library it's for test cluster setup. Instead, use [`Alia`](https://github.com/mpenet/alia) or [`Cassaforte`](https://github.com/clojurewerkz/cassaforte).
- Similarly I don't intend to shadow every CCM function only to provide a useful integration API.
- I test new releases on the current CCM, so you should upgrade CCM-clj and CCM at the same time.
- Probably doesn't work on Windows. Patches welcome.
- IPs from 127.0.0.1 to 127.0.0.n will be used, this may require loopback aliasing see Installation.
- Ports from cql-port to cql-port+3 will be assigned to CQL, thrift, JMX and storage respectively.
- If you abort without cleanup, you may leave CassandraDaemon(s) running which you can stop from the repl `(ccm/stop!)` or in the shell `ccm stop`.
- Using older versions of Cassandra may require Snappy-Java to be an explicit dependency due to https://github.com/xerial/snappy-java/issues/6
- Error: Could not find or load main class org.apache.cassandra.service.CassandraDaemon might mean an interrupted download, try blowing away .ccm.

For more see the API in [`src/ccm_clj.clj`](src/ccm_clj.clj) or tests in [`test/ccm_clj_test.clj`](test/ccm_clj_test.clj).

## Tests Usage

```clojure
lein all expectations
```

## Contact

Colin Taylor at gmail.

## License

Copyright © 2015 SMX Ltd (http://smxemail.com) and Contributors.

Distributed under the Eclipse Public License.

