(ns env-config.version)

(def current-version "0.0.1-SNAPSHOT")                                                                                        ; this should match our project.clj

(defmacro get-current-version []
  current-version)