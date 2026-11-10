(ns keystone.files
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.nio.file Files StandardCopyOption]
           [java.util UUID]))

(defn root []
  (io/file (or (System/getenv "FILE_UPLOAD_DIR") "/tmp/keystone-upload")))

(defn- extension [filename]
  (some->> (re-find #"(?i)(\.[a-z0-9]{1,10})$" (str filename)) second str/lower-case))

(defn- valid-name? [filename]
  (and (string? filename)
       (not (str/blank? filename))
       (not (re-find #"[/\\]|\.\." filename))))

(defn upload! [part]
  (let [filename (:filename part)
        tempfile (:tempfile part)
        size (or (:size part) (when tempfile (.length ^java.io.File tempfile)))
        allowed #{".jpg" ".jpeg" ".png" ".gif" ".webp" ".txt" ".pdf" ".xlsx" ".xls"}]
    (when-not (and tempfile (valid-name? filename))
      (throw (ex-info "文件为空或文件名非法" {:type :validation :code 10405})))
    (when (> (long size) (* 10 1024 1024))
      (throw (ex-info "文件大小超过 10 MB" {:type :validation :code 10403})))
    (when-not (contains? allowed (extension filename))
      (throw (ex-info "不允许的文件类型" {:type :validation :code 10401})))
    (let [directory (root)
          new-name (str (UUID/randomUUID) (extension filename))
          target (io/file directory new-name)]
      (.mkdirs directory)
      (Files/copy (.toPath ^java.io.File tempfile) (.toPath target)
                  (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING]))
      {:url (str "/file/download?fileName=" new-name)
       :fileName new-name
       :newFileName new-name
       :originalFilename filename})))

(defn download [filename]
  (when-not (valid-name? filename)
    (throw (ex-info "文件名称非法" {:type :validation :code 10004})))
  (let [file (io/file (root) filename)]
    (when-not (.isFile file)
      (throw (ex-info "文件不存在" {:type :not-found})))
    file))
