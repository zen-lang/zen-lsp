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
      (if zloc
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
              (recur wrapping-zloc (z/next zloc)))))
        wrapping-zloc))))

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

(defn zloc->node [zloc]
  (z/node zloc))

(comment
  (location->zloc (p/parse-string "{:a {:b [1 2 3]}}") 1 1) {:a ...}

  ;; how does a path describe navigating to a key in a map, rather than a value
  ;; maybe [:a :b :c] :key? true

  ,)
