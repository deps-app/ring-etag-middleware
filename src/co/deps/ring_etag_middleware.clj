(ns co.deps.ring-etag-middleware
  (:require [clojure.java.io :as io]
            [ring.util.response :as response])
  (:import (java.util.zip CRC32)
           (java.io File)
           (java.nio.file Path Files LinkOption FileSystemException)
           (java.nio.file.attribute UserDefinedFileAttributeView)
           (java.nio ByteBuffer)
           (java.nio.charset Charset)))

(defn checksum-file
  "Calculate a CRC32 checksum for a File."
  ;; Copied from code generated by Pandect
  ;; https://github.com/xsc/pandect
  [^File file]
  (with-open [is (io/input-stream file)]
    (let [buffer-size (int 2048)
          ba (byte-array buffer-size)
          crc-32 (new CRC32)]
      (loop []
        (let [num-bytes-read (.read is ba 0 buffer-size)]
          (when-not (= num-bytes-read -1)
            (.update crc-32 ba 0 num-bytes-read)
            (recur))))
      (.getValue crc-32))))

(defn ^UserDefinedFileAttributeView get-user-defined-attribute-view [path]
  (Files/getFileAttributeView
    path
    UserDefinedFileAttributeView
    (into-array LinkOption [])))

(def checksum-attribute-name "user.ring-etag-middleware.crc32-checksum")

(defn get-attribute [path attribute]
  (try
    (let [view (get-user-defined-attribute-view path)
          name attribute
          size (.size view name)
          attr-buf (ByteBuffer/allocate size)]
      (.read view name attr-buf)
      (.flip attr-buf)
      (str (.decode (Charset/defaultCharset) attr-buf)))
    (catch FileSystemException e
      nil)))

(defn set-attribute [path attribute ^String value]
  (let [view (get-user-defined-attribute-view path)]
    (.write view attribute (.encode (Charset/defaultCharset) value))))

;; Public API

(defn supports-extended-attributes?
  "The JDK doesn't support UserDefinedFileAttributes (a.k.a. extended attributes)
  on all platforms.

  Notably, HFS+ and APFS on macOS do not support extended attributes on JDK 16 and
  below. Support was added in JDK 17: https://bugs.openjdk.java.net/browse/JDK-8030048."
  [^Path path]
  (.supportsFileAttributeView
    (Files/getFileStore path)
    ^Class UserDefinedFileAttributeView))

(defn add-file-etag
  [response extended-attributes?]
  (let [file (:body response)]
    (if (instance? File file)
      (let [path (.toPath ^File file)]
        (if extended-attributes?
          (if-let [checksum (get-attribute path checksum-attribute-name)]
            (response/header response "ETag" checksum)
            (let [checksum (checksum-file file)]
              (set-attribute path checksum-attribute-name (str checksum))
              (response/header response "ETag" checksum)))
          (response/header response "ETag" (checksum-file file))))
      response)))

(defn wrap-file-etag
  "Calculates an ETag for a Ring response which contains a File as the body.

  If extended-attributes? is true, then the File is first checked for a
  checksum in it's extended attributes, if it doesn't exist then it is
  calculated and added to the file, and returned in the ETag. This is
  much faster than calculating the checksum each time (which is already
  fast), but isn't supported on all platforms, notably macOS.

  If you wish to store the checksum in extended attributes, it is
  recommended that you first check if the Path that you are wanting
  to serve files from supports it. You can use the provided
  supports-extended-attributes? function for this."
  ([handler]
    (wrap-file-etag handler {}))
  ([handler {:keys [extended-attributes?] :as options}]
   (fn
     ([req]
      (add-file-etag (handler req) extended-attributes?))
     ([req respond raise]
      (handler req
               #(respond (add-file-etag % extended-attributes?))
               raise)))))
