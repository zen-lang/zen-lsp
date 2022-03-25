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
                   (zloc->path)))

  (is (= '[schema :keys :bar]
         (-> (p/parse-string "{ns bar
 import #{foo}

 schema
 {:zen/tags #{zen/schema}
  :type zen/map
  :keys {:foo {:confirms #{foo/schema}} ;; No errors expected
         :bar {}}}}
"
                             )
             (location->zloc 8 16)
             (zloc->path))))
  (is (= '[schema :keys :bar :a]
         (-> (p/parse-string "{ns bar
 import #{foo}

 schema
 {:zen/tags #{zen/schema}
  :type zen/map
  :keys {:foo {:confirms #{foo/schema}} ;; No errors expected
         :bar {:a}}}}
"
                             )
             (location->zloc 8 18)
             (zloc->path)))))



