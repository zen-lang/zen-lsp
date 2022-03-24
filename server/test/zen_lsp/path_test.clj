(ns zen-lsp.path-test
  (:require [clojure.test :as test :refer [deftest is]]
            [rewrite-clj.parser :as p]
            [zen-lang.lsp-server.impl.location :as loc
             :refer [location->zloc zloc->path]]))

(deftest path-test
  (is (= [:a :b :c] (-> (location->zloc (p/parse-string "{:a {:b {:c 3}}}") 1 13)
                        ;; at value 3 of key :c
                        (zloc->path))))
  (is (= [:a :b :c] (-> (location->zloc (p/parse-string "{:a {:b {:c 3}}}") 1 10)
                        ;; at key :c
                        (zloc->path))))
  (is (= [:a :b 1] (-> (location->zloc (p/parse-string "{:a {:b [1 10]}}") 1 12)
                       ;; at value 10 in vector
                       (zloc->path))))
  (is [0 :b 1] (-> (location->zloc (p/parse-string "[{:b [1 10]}]") 1 9)
                   ;; at value 10 in vector
                   (zloc->path))))



