(ns zen-lang.lsp-server.impl.definition
  {:no-doc true}
  (:require [zen.core :as zen]
            [clojure.java.io :as io]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]
            [zen-lang.lsp-server.impl.location :as loc
             :refer [location->zloc]]
            [zen-lang.lsp-server.impl.log :refer [debug]]))


(defn get-zen-file [zen-ctx ns]
  (or (get-in @zen-ctx [:ns ns :zen/file])
      (do (zen/read-ns zen-ctx ns)
          (get-in @zen-ctx [:ns ns :zen/file]))))


(defn find-definition [zen-ctx {:keys [uri position]}]
  (let [{:keys [line character]} position
        line (inc line) ;; lsp lines are 0-based, rewrite-clj lines are 1-based
        character (inc character)
        text (get-in @zen-ctx [:file uri :last-valid-text])
        parsed (p/parse-string text)
        zloc (try (some-> parsed (location->zloc line character))
                  (catch Exception e (debug (ex-message e))))
        node (try (some-> zloc loc/zloc->node)
                  (catch Exception e (debug (ex-message e))))
        path (try (some-> zloc loc/zloc->path)
                  (catch Exception e (debug (ex-message e))))
        sym (:value node)]
    (when (symbol? sym)
      (cond
        (namespace sym)
        (when-let [file (get-zen-file zen-ctx (symbol (namespace sym)))]
          (when-let [{:keys [row col end-row end-col] :as _loc}
                     (some-> @zen-ctx :symbols (find sym) first meta)]
            (when (and row col end-row end-col)
              {:uri (str (.toURI (io/file file)))
               :row (dec row)
               :col (dec col)
               :end-row (dec end-row)
               :end-col (dec end-col)})))

        (and (= 2 (count path)) (= 'import (first path)))
        (when-let [file (get-zen-file zen-ctx sym)]
          {:uri (str (.toURI (io/file file)))
           :row 0
           :col 0
           :end-row 0
           :end-col 0})

        (-> (loc/get-in (z/edn parsed) ['ns]) first :value)
        (let [ns (-> (loc/get-in (z/edn parsed) ['ns]) first :value)
              sym (symbol (str ns) (str sym))]
          (when-let [file (get-zen-file zen-ctx ns)]
            (when-let [{:keys [row col end-row end-col] :as _loc}
                       (some-> @zen-ctx :symbols (find sym) first meta)]
              (when (and row col end-row end-col)
                {:uri (str (.toURI (io/file file)))
                 :row (dec row)
                 :col (dec col)
                 :end-row (dec end-row)
                 :end-col (dec end-col)}))))))))
