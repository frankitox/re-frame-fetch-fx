(ns superstructor.re-frame.fetch-fx-test
  (:require
    [clojure.test :refer [deftest is testing async use-fixtures]]
    [clojure.spec.alpha :as s]
    [goog.object :as obj]
    [re-frame.core :as re-frame]
    [superstructor.re-frame.fetch-fx :as fetch-fx]))

;; Utilities
;; =============================================================================

(deftest ->seq-test
  (is (= [{}]
         (fetch-fx/->seq {})))
  (is (= [{}]
         (fetch-fx/->seq [{}])))
  (is (= [nil]
         (fetch-fx/->seq nil))))

(deftest ->str-test
  (is (= ""
         (fetch-fx/->str nil)))
  (is (= "42"
         (fetch-fx/->str 42)))
  (is (= "salient"
         (fetch-fx/->str :salient)))
  (is (= "symbolic"
         (fetch-fx/->str 'symbolic))))

(deftest ->params->str-test
  (is (= ""
         (fetch-fx/params->str nil)))
  (is (= ""
         (fetch-fx/params->str {})))
  (is (= "?sort=desc&start=0"
         (fetch-fx/params->str {:sort :desc :start 0})))
  (is (= "?fq=Expect%20nothing%2C%20%5Ba-z%26%26%5B%5Eaeiou%5D%5D&debug=timing"
         (fetch-fx/params->str {:fq         "Expect nothing, [a-z&&[^aeiou]]"
                                :debug 'timing}))))

(deftest headers->js-test
  (let [js-headers (fetch-fx/headers->js {:content-type "application/json"})]
    (is (instance? js/Headers js-headers))
    (is (= "application/json"
           (.get js-headers "content-type")))))

(deftest request->js-init-test
  (let [js-abort-controller (js/AbortController.)
        js-init             (fetch-fx/request->js-init
                              {:method "GET"}
                              js-abort-controller)]
    (is (= "{\"signal\":{},\"method\":\"GET\",\"mode\":\"same-origin\",\"credentials\":\"include\",\"redirect\":\"follow\"}"
           (js/JSON.stringify js-init)))
    (is (= (.-signal js-abort-controller)
           (.-signal js-init)))))

(deftest js-headers->clj-test
  (let [headers {:content-type "application/json"
                 :server       "nginx"}]
    (is (= headers
           (fetch-fx/js-headers->clj (fetch-fx/headers->js headers))))))

(deftest js-response->clj
  (is (= {:url ""
          :ok? true
          :redirected? false
          :status 200
          :status-text ""
          :type "default"
          :final-uri? nil
          :headers {}}
         (fetch-fx/js-response->clj (js/Response.)))))

(deftest response->reader-test
  (is (= :text
         (fetch-fx/response->reader-kw
           {}
           {:headers {:content-type "application/json"}})))
  (is (= :blob
         (fetch-fx/response->reader-kw
           {:response-content-types {"text/plain" :blob}}
           {:headers {}})))
  (is (= :json
         (fetch-fx/response->reader-kw
           {:response-content-types {#"(?i)application/.*json" :json}}
           {:headers {:content-type "application/json"}}))))

(deftest timeout-race-test
  (async done
    (-> (fetch-fx/timeout-race
          (js/Promise.
            (fn [_ reject]
              (js/setTimeout #(reject :winner) 16)))
          32)
        (.catch (fn [value]
                  (is (= :winner value))
                  (done)))))
  (async done
    (-> (fetch-fx/timeout-race
          (js/Promise.
            (fn [_ reject]
              (js/setTimeout #(reject :winner) 32)))
          16)
        (.catch (fn [value]
                  (is (= :timeout value))
                  (done))))))
