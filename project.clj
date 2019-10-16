(defproject co.deps/ring-etag-middleware "0.2.1"
  :description "Ring middleware to set an ETag on responses"
  :url "https://github.com/deps-app/ring-etag-middleware"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :global-vars {*warn-on-reflection* true}
  :deploy-repositories {"releases" :clojars
                        "snapshots" :clojars}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.6.3"]])
