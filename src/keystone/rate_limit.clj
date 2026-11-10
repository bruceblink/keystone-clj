(ns keystone.rate-limit)

(defn make-limiter []
  (atom {}))

(defn allowed? [limiter key window-ms max-count]
  (let [now (System/currentTimeMillis)
        allowed (atom false)]
    (swap! limiter
           (fn [state]
             (let [{:keys [started count]} (get state key)
                   active? (and started (< (- now started) window-ms))
                   next-state (if active?
                                (if (< count max-count)
                                  (do (reset! allowed true)
                                      (assoc state key {:started started :count (inc count)}))
                                  state)
                                (do (reset! allowed true)
                                    (assoc state key {:started now :count 1})))]
               next-state)))
    @allowed))

(defn wrap [handler limiter window-ms max-count key-fn]
  (fn [request]
    (if (allowed? limiter (key-fn request) window-ms max-count)
      (handler request)
      {:status 429
       :headers {"content-type" "application/json; charset=utf-8"}
       :body "{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}"})))
