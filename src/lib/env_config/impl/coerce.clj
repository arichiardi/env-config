(ns env-config.impl.coerce
  (:require [clojure.string :as string]))

(deftype Coerced [val])

; -- standard coercers ------------------------------------------------------------------------------------------------------

(defn nil-coercer [_path val]
  (if (= (string/lower-case val) "nil")
    (->Coerced nil)))

(defn boolean-coercer [_path val]
  (condp = (string/lower-case val)
    "true" (->Coerced true)
    "false" (->Coerced false)))

(defn integer-coercer [_path val]
  (try
    (->Coerced (Integer/parseInt val))
    (catch NumberFormatException e)))

(defn double-coercer [_path val]
  (try
    (->Coerced (Double/parseDouble val))
    (catch NumberFormatException e)))

(defn keyword-coercer [_path val]
  (if (string/starts-with? val ":")
    (->Coerced (keyword (.substring val 1)))))

(defn symbol-coercer [_path val]
  (if (string/starts-with? val "'")
    (->Coerced (symbol (.substring val 1)))))

(defn code-coercer [_path val]
  (if (string/starts-with? val "~")
    (try
      (->Coerced (read-string (.substring val 1)))                                                                            ; TODO: should we rather use edn/read-string here?
      (catch Throwable e))))

; -- default coercers -------------------------------------------------------------------------------------------------------

(def default-coercers
  [nil-coercer
   boolean-coercer
   integer-coercer                                                                                                            ; must go before double-coercer
   double-coercer
   keyword-coercer
   symbol-coercer
   code-coercer])

; -- coercer machinery ------------------------------------------------------------------------------------------------------

(defn coerce [path val coercer]
  (try
    (when-let [result (coercer path val)]
      (assert (instance? Coerced result))
      result)
    (catch Throwable e)))                                                                                                     ; TODO: we should not swallow exceptions, but report it somehow

(defn apply-coercers [coercers path val]
  (if-let [result (some (partial coerce path val) coercers)]
    (.val result)
    val))

(defn push-key [state key]
  (update state :keys conj key))

(defn store-keys [state]
  (:keys state))

(defn restore-keys [state keys]
  (assoc state :keys keys))

(defn coercer-worker [coercers state key val]
  (if (map? val)
    (let [current-keys (store-keys state)
          new-state (reduce-kv (:reducer state) (push-key state key) val)]
      (restore-keys new-state current-keys))
    (let [path (conj (:keys state) key)
          coerced-val (apply-coercers coercers path val)]
      (update state :config assoc-in path coerced-val))))

; -- coercer ----------------------------------------------------------------------------------------------------------------

(defn coerce-config [config coercers]
  (let [reducer (partial coercer-worker coercers)
        init {:keys    []
              :reducer reducer
              :config  {}}]
    (:config (reduce-kv reducer init config))))
