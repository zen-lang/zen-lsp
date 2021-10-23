(ns zen-lsp.test-utils
  (:require
   [clojure.string :as str]
   [clojure.test :refer [is]]))

(defn normalize-filename [s]
  (str/replace s "\\" "/"))

(defn submap?
  "Is m1 a subset of m2? Taken from
  https://github.com/clojure/spec-alpha2, clojure.test-clojure.spec"
  [m1 m2]
  (cond
    (and (map? m1) (map? m2))
    (every? (fn [[k v]] (and (contains? m2 k)
                             (if (or (identical? k :filename)
                                     (identical? k :file))
                               (= (normalize-filename v)
                                  (normalize-filename (get m2 k)))
                               (submap? v (get m2 k)))))
            m1)
    (instance? java.util.regex.Pattern m1)
    (re-find m1 m2)
    :else (= m1 m2)))

(defmacro assert-submap [m r]
  `(is (submap? ~m ~r)))

(defmacro assert-submaps
  "Asserts that maps are submaps of result in corresponding order and
  that the number of maps corresponds to the number of
  results. Returns true if all assertions passed (useful for REPL)."
  [maps result]
  `(let [maps# ~maps
         res# ~result]
     (and
      (is (= (count maps#) (count res#))
          (format "Expected %s results, but got: %s"
                  (count maps#) (count res#)))
      (doseq [m# maps#]
        (is (some #(submap? m# %) res#) (str "No superset of " m# " found"))))))
