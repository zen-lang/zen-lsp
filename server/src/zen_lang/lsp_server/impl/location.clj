(ns zen-lang.lsp-server.impl.location
  (:refer-clojure :exclude [get get-in])
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))

(defn get-in [zloc path]
  (reduce (fn [zloc k]
            (if zloc
              (z/get zloc k)
              (reduced nil))) zloc path))

(defn get-location
  ([edn-node path] (get-location edn-node path false))
  ([edn-node path key?]
   (when-let [zloc (some-> edn-node z/edn (get-in path))]
     (let [zloc (if key? (z/left zloc) zloc)]
       (-> zloc z/node meta)))))

(defn container? [zloc]
  (case (z/tag zloc)
    (:map :vector :list :set) true
    false))

(defn location->zloc
  [edn-node row col]
  (when-let [zloc (some-> edn-node z/edn)]
    (loop [wrapping-zloc nil
           zloc zloc]
      (let [{node-row :row
             node-col :col
             node-end-row :end-row
             node-end-col :end-col} (meta (z/node zloc))]
        (if (and (>= row node-row)
                 (>= col node-col)
                 (<= row node-end-row)
                 (<= col node-end-col))
          (if (container? zloc)
            (recur zloc (z/down zloc))
            zloc)
          (if (z/end? zloc)
            wrapping-zloc
            (recur wrapping-zloc (z/next zloc))))))))

(defn left-count [zloc]
  (count (take-while identity (rest (iterate z/left zloc)))))

(defn at-key? [zloc]
  (even? (left-count zloc)))

(defn zloc->path [zloc]
  (loop [zloc zloc
         path []]
    (let [parent (z/up zloc)
          parent-tag (z/tag parent)]
      (cond
        (= :map parent-tag)
            ;; current zloc is entry in key
        (recur (-> parent) (conj path
                                 (if (at-key? zloc)
                                   (z/sexpr zloc)
                                   (z/sexpr (z/left zloc)))))
        (= :vector parent-tag)
        (recur (-> parent) (conj path (left-count zloc)))
        (or
         (not parent)
         (= :forms parent-tag))
        (vec (reverse path))
        :else (recur parent (conj path (z/sexpr zloc)))))))

(comment
  (get-location (p/parse-string "{:a 1}") [:a])
  (location->zloc (p/parse-string "{:a {:b [1 2 3]}}") 1 1) {:a ...}
  (location->zloc (p/parse-string "{:a {:b [1 2 3]}}") 1 2) :a
  (location->zloc (p/parse-string "{:a {:b [1 2 3]}}") 1 3) :a
  (location->zloc (p/parse-string "{:a {:b [1 2 3]}}") 1 4) :a
  (location->zloc (p/parse-string "{:a {:b [1 2 3]}}") 1 5) :b
  (location->zloc (p/parse-string "{:a [1 2 3]}") 1 6) 1
  (location->zloc (p/parse-string "{:a [1 2 3]}") 1 8)

  ;; how does a path describe navigating to a key in a map, rather than a value
  ;; maybe [:a :b :c] :key? true

  (-> (location->zloc (p/parse-string "{:a {:b {:c 3}}}") 1 13)
      ;; at value 3 of key :c
      (zloc->path)) ;;=> [:a :b :c]

  (-> (location->zloc (p/parse-string "{:a {:b {:c 3}}}") 1 10)
      ;; at key :c
      (zloc->path)) ;;=> [:a :b :c]

  (-> (location->zloc (p/parse-string "{:a {:b [1 10]}}") 1 12)
      ;; at value 10 in vector
      (zloc->path)) ;;=> [:a :b 1]

  (-> (location->zloc (p/parse-string "[{:b [1 10]}]") 1 9)
      ;; at value 10 in vector
      (zloc->path)) ;;=> [0 b 1]

  ,)
