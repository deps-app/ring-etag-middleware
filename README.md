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
    (wrap-etag))
```

## Checksums or hashes?

Checksums are faster to 

## License

Copyright © 2018 Daniel Compton

Distributed under the MIT license. 
