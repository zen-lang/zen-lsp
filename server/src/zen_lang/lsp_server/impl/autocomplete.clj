(ns zen-lang.lsp-server.impl.autocomplete
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.core.match :as match]
            [rewrite-clj.node :as n]
            [zen.core]))


(defn get-zen-ns [zen-ns-map]
  (or (get zen-ns-map :ns)
      (get zen-ns-map 'ns)))


(defn gather-confirming-keys
  [ztx confirmings]
  (when (seq confirmings)
    (mapcat #(concat (keys (get-in @ztx [:symbols % :keys]))
                     (gather-confirming-keys ztx (:confirms (get-in @ztx [:symbols %]))))
            confirmings)))


(defn lvl-completions-dispatch [_ztx lvl _params] lvl)


(defmulti lvl-completions #'lvl-completions-dispatch)


(def style-completions
  {:kw #{:ns :import :alias}
   :sym #{'ns 'import 'alias}})


(def ns-keys
  (apply set/union (vals style-completions)))


(defmethod lvl-completions :namespace-keys [ztx _
                                            {:keys [cur-ns
                                                    cur-ns-map
                                                    uri struct-path]}]
  (let [style (if (contains? cur-ns-map 'ns)
                :sym
                :kw #_"NOTE: :kw is current preferred style")
        completions (set/difference (get style-completions style)
                                    (set (keys cur-ns-map)))]
    completions))


(defn get-other-namespaces [ztx {:keys [cur-ns]}]
  (disj (set (keys (:ns @ztx)))
        nil
        cur-ns))


(defn get-current-ns-name [ztx {:keys [uri]}]
  (when-let [[_ ns-path] (re-find #"zrc/(.+?).edn$" uri)]
    (symbol (str/replace ns-path \/ \.))))


(defmethod lvl-completions :namespace-values
  [ztx _ {:as params :keys [struct-path]}]
  (match/match struct-path
               [(:or 'ns :ns) & _]       [(get-current-ns-name ztx params)]

               [(:or 'import :import)]   [#{}]
               [(:or 'import :import) _] (get-other-namespaces ztx params)

               [(:or 'alias :alias)]     (get-other-namespaces ztx params)
               [(:or 'alias :alias) _]   (get-other-namespaces ztx params)
               :else []))


(defn get-props [ztx]
  (-> (set (zen.core/get-tag ztx 'zen/property))
      (disj 'zen/name 'zen/file 'zen/zen-path)))


(defmethod lvl-completions :top-level-symbol-definition
  [ztx _ {:as params :keys [struct-path cur-ns-map]}]
  (let [keys-from-tags (some->> (get-in cur-ns-map (conj struct-path :zen/tags))
                    (mapcat (fn [sym] (keys (:keys (zen.core/get-symbol ztx sym))))))]
    (into (->> (get-props ztx)
               (mapv keyword))
          keys-from-tags)))


(defmethod lvl-completions :top-level-symbol-definition-value
  [ztx _ {:as params :keys [struct-path]}]
  (when (and (qualified-keyword? (second struct-path))
             (= "zen" (namespace (second struct-path))))
    (case (second struct-path)
      :zen/tags (zen.core/get-tag ztx 'zen/tag)
      :zen/desc ["\"\""])
    #_(let [sch (zen.core/get-symbol ztx (symbol (last struct-path)))]
      (case (:type sch)
        zen/set     [#{}]
        zen/vector  [[]]
        zen/string  ["\"\""]
        zen/keyword [":"]))))


(defn deduce-lvl [{:keys [struct-path]}]
  (cond
    (contains? ns-keys (first struct-path))
    [:namespace-values]

    (or (empty? struct-path)
        (and (= 1 (count struct-path))
             (some #(str/starts-with? (first struct-path) %)
                   (map str ns-keys))))
    [:namespace-keys]

    (= 1 (count struct-path))
    [:top-level-symbol-definition]

    (or (= 3 (count struct-path))
        (= 2 (count struct-path)))
    [:top-level-symbol-definition-value]

    :else []))


(defn find-completions [ztx {:as params :keys [uri struct-path edn last-valid-edn]}]
  (let [ns-map (try (n/sexpr edn)
                    (catch Exception e
                      (n/sexpr last-valid-edn)))
        cur-ns (get-zen-ns ns-map)
        compl-params (assoc params
                            :cur-ns cur-ns
                            :cur-ns-map ns-map)
        lvl (deduce-lvl compl-params)
        completions (->> lvl
                         (mapcat #(lvl-completions ztx % compl-params))
                         (mapv str)
                         sort)]
    completions)
  #_(cond
      ;; :type symbol suggestion
      (= :type (last struct-path))
      (let [cur-ns-edn (get-in @ztx [:file uri :last-valid-edn])
            zen-symbols (keys (:symbols @ztx))
            cur-ns-symbols (-> cur-ns-edn (dissoc 'import 'ns) keys)
            imported-symbols (reduce (fn [acc ns']
                                       (into acc (keys (get-in @ztx [:ns ns']))))
                                     []
                                     (get cur-ns-edn 'import))]
        (->> (concat zen-symbols cur-ns-symbols imported-symbols)
             sort
             (mapv str)))

      ;; :keys symbol suggestion based on :confirms key
      (= :keys (last struct-path))
      (let [current-ns (get-in @ztx [:file uri :last-valid-edn 'ns])
            pos-ctx-struct (get-in @ztx (into [:file uri :last-valid-edn] (butlast struct-path)))
            confirms (map #(if (nil? (namespace %))
                             (symbol (str current-ns) (name %))
                             %)
                          (:confirms pos-ctx-struct))
            confirming-keys (gather-confirming-keys ztx confirms)]
        (vec (map str confirming-keys)))

      ;; schema :keys keyword suggestion
      :else
      (let [pos-ctx-struct (get-in @ztx (into [:file uri :last-valid-edn] struct-path))
            type-symbol (:type pos-ctx-struct)]
        (when type-symbol
          (->> (get-in @ztx [:symbols type-symbol :keys]) keys sort (mapv str))))))


(comment
  )
