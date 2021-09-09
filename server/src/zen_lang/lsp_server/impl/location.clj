(ns zen-lang.lsp-server.impl.location
  (:refer-clojure :exclude [get get-in])
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]))

(defn get-in [zloc path]
  (reduce z/get zloc path))

(defn get-location
  ([edn-node path] (get-location edn-node path false))
  ([edn-node path key?]
   (when-let [zloc (some-> edn-node z/edn (get-in path))]
     (let [zloc (if key? (z/left zloc) zloc)]
       (-> zloc z/node meta)))))

(comment
  (get-location (p/parse-string "{:a 1}") [:a])
  ,)


