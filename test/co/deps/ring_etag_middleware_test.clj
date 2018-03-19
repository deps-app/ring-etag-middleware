(ns co.deps.ring-etag-middleware-test
  (:require [clojure.test :refer :all]
            [co.deps.ring-etag-middleware :refer :all]
            [ring.util.response :refer [get-header]])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.io File)))

(def ^{:dynamic true :tag File} *temp-file* nil)

(defn ^File create-temp-file []
  (let [path (Files/createTempFile "temp-figwheel-test-file" ".js" (into-array FileAttribute []))
        file (.toFile path)]
    (.deleteOnExit file)
    file))

(defn- constant-handler [body]
  (constantly
    {:status  200
     :headers {}
     :body    body}))

(def js-string "// Compiled by ClojureScript 1.9.908 {}
goog.provide('myapp.core');
goog.require('cljs.core');
")

(use-fixtures
  :each
  (fn [f]
    (let [file (create-temp-file)]
      (spit file js-string)
      (.deleteOnExit file)
      (binding [*temp-file* file]
        (f)))))

(deftest add-file-etag-test
  (is (= (get-header (add-file-etag {:body *temp-file*} false)
                     "ETag")
         "4146274220")))

(deftest add-file-etag-extended-attributes-test
  (when (supports-extended-attributes? (.toPath *temp-file*))
    (let [mod-date (.lastModified *temp-file*)]
      (is (nil? (get-attribute (.toPath *temp-file*) checksum-attribute-name)))
      (is (= (get-header (add-file-etag {:body *temp-file*} true) "ETag")
             "4146274220"))
      (is (= (get-attribute (.toPath *temp-file*) checksum-attribute-name)
             "4146274220"))
      (testing "adding attributes to file doesn't update last modified date"
        (is (= mod-date (.lastModified *temp-file*))))
      (is (= (get-header (add-file-etag {:body *temp-file*} true) "ETag")
             "4146274220")))))

(deftest wrap-file-etag-test
  (testing "file response"
    (is (= (dissoc ((wrap-file-etag (constant-handler *temp-file*)) {:request-method :get, :uri "/"})
                   :body)
           {:headers {"ETag" "4146274220"}
            :status  200})))
  (testing "string response has no etag"
    (is (= ((wrap-file-etag (constant-handler "test")) {:request-method :get, :uri "/"})
           {:status  200
            :headers {}
            :body    "test"})))
  (testing "map response has no etag"
    (is (= ((wrap-file-etag (constant-handler {:test 1 :time 2})) {:request-method :get, :uri "/"})
           {:status  200
            :headers {}
            :body    {:test 1 :time 2}}))))

#_(deftest speed-test
    (let [temp-file (create-temp-file)
          temp-file-ext (create-temp-file)]
      (println "Calculate checksum every time")
      (dotimes [_ 10]
        (time
          (dotimes [_ 1000]
            (add-file-etag {:body temp-file} false))))
      (println "Calculate checksum once and store it as an extended attribute")
      (when (supports-extended-attributes? (.toPath temp-file-ext))
        (dotimes [_ 10]
          (time
            (dotimes [_ 1000]
              (add-file-etag {:body temp-file-ext} true)))))))
