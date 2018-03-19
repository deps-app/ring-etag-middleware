# ring-etag-middleware

Calculates [ETags](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag) for [Ring](https://github.com/ring-clojure/ring) File responses using a CRC32 checksum. 

## Usage

Add this to your `project.clj` or `build.boot`:

```
[co.deps/ring-etag-middleware "0.5.0"]
```

Require the namespace and add it to your middleware stack:

```clojure
(ns my-app.core
  (:require [co.deps.ring-etag-middleware :as etag]))

(-> handler
    (etag/wrap-file-etag))
```

## Caching checksum calculations

Once a checksum for a file has been calculated once, it is unnecessary to calculate it again. If the files you are serving are immutable, then it would be possible to pre-calculate the checksum once. However if you are working in an environment where the files being served may change (say a ClojureScript compiler output directory), then you cannot store the checksum separately from the file (either in-memory or on-disk), as you don't have a 100% reliable method for detecting when the file has changed (without running a file watcher, which we won't do here).

Instead, ring-etag-middleware provides a way to store checksums in the [extended attributes](https://en.wikipedia.org/wiki/Extended_file_attributes) of the files being served. If this option is enabled, the middleware will check if the `java.io.File` in the Ring response has a checksum calculated. If so it will return it as the ETag, if not it will calculate the checksum and store it as an extended attribute on the `File`.The JDK doesn't support this on all platforms that have support for extended attributes (notably [macOS](https://bugs.openjdk.java.net/browse/JDK-8030048)), so it is recommended to check for support with the provided `supports-extended-attributes?` function.

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

[Checksums](https://en.wikipedia.org/wiki/Checksum) are faster to calculate than [cryptographic hash functions](https://en.wikipedia.org/wiki/Cryptographic_hash_function) like MD5 or SHA1. We don't need any of the cryptographic properties that the hash functions provide for an ETag, so using a checksum is a better choice. Pandect has some [benchmarks](https://github.com/xsc/pandect#benchmark-results) showing the speed differences between checksums and hashes.

We use CRC32 over Adler32 because it has a [lower risk](https://www.leviathansecurity.com/blog/analysis-of-adler32) of collisions at the cost of being slightly slower to calculate (10-20%). If you are at all concerned about performance, you should enable storing checksums in file extended attributes.

## License

Copyright © 2018 Daniel Compton

Distributed under the MIT license. 
