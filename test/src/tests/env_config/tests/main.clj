(ns env-config.tests.main
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [env-config.core :refer :all]
            [env-config.impl.coerce :refer [->Coerced]]))

(deftest test-reading
  (testing "basic configs"
    (let [vars {"MY-PROJECT/A"     "s"
                "MY_PROJECT/B"     42
                "MY_PROJECT/C_Cx"  "normalize key"
                "MY_PROJECT/D"     :keyword-val
                "MY_PROJECT/A1/B1" :nested-key
                "my_project/A1/B2" "someval"
                "My_Project/A1/B2" "overwrite"
                "My_project/A1"    "should-not-destroy-subkeys"}
          expected-config {:a    "s"
                           :b    "42"
                           :c-cx "normalize key"
                           :d    ":keyword-val"
                           :a1   {:b1 ":nested-key"
                                  :b2 "overwrite"}}]
      (is (= expected-config (read-config "my-project" vars)))))
  (testing "nested prefix"
    (is (= {:v "1"} (read-config "project/sub-project/x/y" {"project/sub-project/x/y/v" "1"
                                                            "project/sub-project/x/y"   "should ignore"
                                                            "project/sub-project/x"     "outside"})))))

(deftest test-coercion
  (testing "basic coercion"
    (let [config {:a  "string"
                  :b  "42"
                  :c  "4.2"
                  :d  ":key"
                  :e  "~{:m {:x \"xxx\"}}"
                  :f  "nil"
                  :g  "true"
                  :h  "false"
                  :i  "True"
                  :j  "FALSE"
                  :k  "'sym"
                  :l  "#!@#xxx"
                  :m  "\\\\x"
                  :n  "~#!@#xxx"
                  :o  "~\"true\""
                  :z1 {:z2 {:z3 "nested"}}}
          expected-coercion {:a  "string"
                             :b  42
                             :c  4.2
                             :d  :key
                             :e  {:m {:x "xxx"}}
                             :f  nil
                             :g  true
                             :h  false
                             :i  true
                             :j  false
                             :k  'sym
                             :l  "#!@#xxx"
                             :m  "\\\\x"
                             :n  "~#!@#xxx"
                             :o  "true"
                             :z1 {:z2 {:z3 "nested"}}}]
      (is (= expected-coercion (coerce-config config))))))

(deftest test-to-level-api
  (testing "make-config"
    (are [vars config] (= config (make-config "project" vars))
      {"project/var" "42"} {:var 42}
      {"project/x/y/z" "nil"} {:x {:y {:z nil}}}))
  (testing "make-config with empty coercions"
    (are [vars config] (= config (make-config "project" vars []))
      {"project/var" "42"} {:var "42"}
      {"project/x/y/z" "nil"} {:x {:y {:z "nil"}}}))
  (testing "make-config with custom coercers"
    (let [my-path-based-coercer (fn [path val]
                                  (if (= (first path) :coerce-me)
                                    (->Coerced (str "!" val))))]
      (are [vars config] (= config (make-config "project" vars [my-path-based-coercer]))
        {"project/dont-coerce-me" "42"} {:dont-coerce-me "42"}
        {"project/coerce-me" "42"} {:coerce-me "!42"}
        {"project/coerce-me/x" "1"} {:coerce-me {:x "!1"}}
        {"project/coerce-me/x/y" "s"} {:coerce-me {:x {:y "!s"}}}))))