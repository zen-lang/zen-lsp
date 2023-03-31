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


(defn get-symbol-coordinate [zen-ctx qsym]
  (when-let [file (get-zen-file zen-ctx (symbol (namespace qsym)))]
    (when-let [{:keys [row col end-row end-col] :as _loc}
               (some-> @zen-ctx :symbols (find qsym) first meta)]
      (when (and row col end-row end-col)
        {:uri (str (.toURI (io/file file)))
         :row (dec row)
         :col (dec col)
         :end-row (dec end-row)
         :end-col (dec end-col)}))) )

(defn find-definition [zen-ctx {:keys [uri position]}]
  (let [{:keys [line character]} position
        line (inc line) ;; lsp lines are 0-based, rewrite-clj lines are 1-based
        character (inc character)
        text (get-in @zen-ctx [:file uri :last-valid-text])
        parsed (p/parse-string text)
        {:keys [node sym path]}
        (try (when-let [zloc (some-> parsed (location->zloc line character))]
               {:node (loc/zloc->node zloc) ;; throws ex on nil
                :sym (:value (loc/zloc->node zloc))
                :path (loc/zloc->path zloc)
                :zloc zloc})
             (catch Exception e (debug (ex-message e))))]
    (when (symbol? sym)
      (cond
        (namespace sym)
        (get-symbol-coordinate zen-ctx sym)

        ;; inside import section: import #{|}
        (and (= 2 (count path)) (= 'import (first path)))
        (when-let [file (get-zen-file zen-ctx sym)]
          {:uri (str (.toURI (io/file file)))
           :row 0
           :col 0
           :end-row 0
           :end-col 0})

        ;; file namespace name or reserved symbols: ns, import
        ;; NOTE: If we return nil here - clojure-lsp will try find that symbol in clojure files
        (or (and (= path ['ns]) (not= sym 'ns))
            (and (= path ['ns]) (= sym 'ns))
            (= path ['import]))
        {:uri uri
         :row (:line position)
         :col (:character position)
         :end-row (:line position)
         :end-col (:character position)}


        ;; symbol in the same ns: (namepsaceless sym)
        (and parsed (-> (loc/get-in (z/edn parsed) ['ns]) first :value))
        (let [ns' (-> (loc/get-in (z/edn parsed) ['ns]) first :value)
              qsym (symbol (str ns') (str sym)) ]
          (get-symbol-coordinate zen-ctx qsym)
          )))))
