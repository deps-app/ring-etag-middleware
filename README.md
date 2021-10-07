# ring-etag-middleware

[![Clojars Project](https://img.shields.io/clojars/v/co.deps/ring-etag-middleware.svg)](https://clojars.org/co.deps/ring-etag-middleware) [![CircleCI](https://circleci.com/gh/danielcompton/ring-ip-whitelist.svg?style=svg)](https://circleci.com/gh/danielcompton/ring-ip-whitelist) [![Dependencies Status](https://versions.deps.co/deps-app/ring-etag-middleware/status.svg)](https://versions.deps.co/deps-app/ring-etag-middleware)

Calculates [ETags](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag) for [Ring](https://github.com/ring-clojure/ring) File responses using a CRC32 checksum.

## Usage

Add this to your `project.clj` or `build.boot`:

```
[co.deps/ring-etag-middleware "0.2.1"]
```

Require the namespace and add `wrap-file-etag` to your middleware stack:

```clojure
(ns my-app.core
  (:require [co.deps.ring-etag-middleware :as etag]))

(-> handler
    (etag/wrap-file-etag))
```

### Returning 304 Not Modified responses

This middleware only calculates checksums, it doesn't make any decisions about the status code returned to the client. If the User Agent has provided an Etag in an [If-None-Match](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-None-Match) header that matches what is calculated by the server, then you probably want to return a [304 Not Modified](https://httpstatuses.com/304) response. I recommend using the middleware built-in to Ring, [`wrap-not-modified`](http://ring-clojure.github.io/ring/ring.middleware.not-modified.html).

```clojure
(ns my-app.core
  (:require [co.deps.ring-etag-middleware :as etag]
            [ring.middleware.not-modified :as not-modified]))

(-> handler
    (etag/wrap-file-etag)
    (not-modified/wrap-not-modified))
```

For a more complete example, you can see the [middleware configuration](https://github.com/bhauman/lein-figwheel/blob/v0.5.17/sidecar/src/figwheel_sidecar/components/figwheel_server.clj#L261-L263) that Figwheel uses.

## Caching checksum calculations

Once a checksum for a file has been calculated once, it is unnecessary to calculate it again. If the files you are serving are immutable, then it would be possible to pre-calculate the checksum once and store the checksum in a local atom. However if you are working in an environment where the files being served may change (say a ClojureScript compiler output directory), then you cannot store the checksum separately from the file (either in-memory or on-disk), as you don't have a 100% reliable method for detecting when to recalculate the checksum (without running a file watcher, which introduces its own problems).

Instead, ring-etag-middleware provides a way to store checksums in the [extended attributes](https://en.wikipedia.org/wiki/Extended_file_attributes) of the files being served. If this option is enabled, the middleware will check if the `java.io.File` in the Ring response has a checksum already calculated. If so it will return it as the ETag; if not it will calculate the checksum and store it as an extended attribute on the `File`. The JDK doesn't support this on all platforms that have support for extended attributes (notably JDK 16 and below don't support extended attributes on [macOS](https://bugs.openjdk.java.net/browse/JDK-8030048)), so it is recommended to check for support with the provided `supports-extended-attributes?` function.

```clojure
(ns my-app.web
  (:require [co.deps.ring-etag-middleware :as etag]
            [clojure.java.io :as io]))

(def file-path
  (.toPath (io/file "./public/files")
  ;; or (Paths/get "./public/files" (into-array String []))
  ))

(-> handler
    (etag/wrap-file-etag
      {:extended-attributes?
       (etag/supports-extended-attributes? file-path)}))
```

## Checksums or hashes?

[Checksums](https://en.wikipedia.org/wiki/Checksum) are faster to calculate than [cryptographic hash functions](https://en.wikipedia.org/wiki/Cryptographic_hash_function) like MD5 or SHA1. An ETag doesn't need any of the cryptographic properties that hash functions provide, so using a checksum is a better choice. Pandect has some [benchmarks](https://github.com/xsc/pandect#benchmark-results) showing the speed differences between checksums and hashes.

We use CRC32 over Adler32 because it has a [lower risk](https://www.leviathansecurity.com/blog/analysis-of-adler32) of collisions at the cost of being slightly slower to calculate (10-20%). If you are at all concerned about performance, you should enable storing checksums in file extended attributes.

**Order of magnitude performance notes**

All benchmarks taken on an unloaded M1 MacBook Air. Note that these are best possible case numbers as the benchmark ensures all files are in memory.

* Reading an extended attribute at various file sizes: ~20 µs
* CRC32 checksum of 50 kB file: ~30 µs
* CRC32 checksum of 2.7 MB file: ~500 µs
* CRC32 checksum of 13 MB file: ~3 ms

## Serving ClojureScript files

I've written a [blog post](https://danielcompton.net/2018/03/21/how-to-serve-clojurescript) detailing how this is used to serve ClojureScript files and avoid caching inconsistencies.

## License

Copyright © 2018 Daniel Compton

Distributed under the MIT license.
