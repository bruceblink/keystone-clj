(ns keystone.files-test
  (:require [clojure.test :refer [deftest is testing]]
            [keystone.files :as files])
  (:import [java.io File FileOutputStream]))

(deftest upload-and-download-stay-within-root
  (let [directory (doto (File/createTempFile "keystone-upload" "")
                    (.delete)
                    (.mkdirs))
        source (File/createTempFile "avatar" ".png")]
    (with-open [out (FileOutputStream. source)]
      (.write out (byte-array [1 2 3])))
    (try
      (with-redefs [files/root (constantly directory)]
        (let [uploaded (files/upload! {:filename "avatar.png"
                                       :tempfile source
                                       :size 3})]
          (is (string? (:url uploaded)))
          (is (.isFile (files/download (:fileName uploaded))))
          (is (thrown? clojure.lang.ExceptionInfo
                       (files/upload! {:filename "../secret.png"
                                       :tempfile source
                                       :size 3})))))
      (finally
        (doseq [file (.listFiles directory)] (.delete ^File file))
        (.delete directory)
        (.delete source)))))
