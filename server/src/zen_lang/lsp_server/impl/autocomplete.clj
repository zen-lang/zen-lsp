(ns zen-lang.lsp-server.impl.autocomplete)

(defn gather-confirming-keys
  [ztx confirmings]
  (when (seq confirmings)
    (->> confirmings
         (mapcat (fn [schema-sym]
                   (let [schema-keys (keys (get-in @ztx [:symbols schema-sym :keys]))
                         schema-confirms (get-in @ztx [:symbols schema-sym :confirms])]
                     (concat schema-keys
                             (gather-confirming-keys ztx schema-confirms))))))))

(defn uri->edn
  "Returns the appropriate loaded namespace for given file `uri` as EDN"
  [ztx uri]
  (-> (get-in ztx [:file uri])
      :last-valid-edn))

(defn ns->edn [ztx ns-sym]
  (get-in ztx [:ns ns-sym]))

(defn namespaced? [sym]
  (-> sym namespace seq boolean))

(defn fqns-sym
  "Returns namespaced symbol `sym` as is or namespaces `sym` with `ns-str`"
  [ns-str sym]
  (if (namespaced? sym)
    sym
    (symbol ns-str (str sym))))

(def reserved-symbols
  #{'import 'ns})

(defn reserved? [sym]
  (contains? reserved-symbols sym))

(defn model-sym?
  "Is `x` a valid model identifier?"
  [x]
  (and (symbol? x)
       (not (reserved? x))))

(defn model-name
  "Returns model name symbol as is or as namespaced symbol"
  [from-ns model-ns model-sym]
  (if (= model-ns from-ns)
    model-sym
    (symbol (str model-ns) (str model-sym))))

(defn make-list-models [ns-sym]
  (fn list-models [[zns content]]
    (->> (keys content)
         (filter model-sym?)
         (map (fn local-or-external-model-name [sym]
                (if (= ns-sym zns)
                  sym
                  (symbol (str zns) (str sym))))))))

(defn model-keys [m]
  (->> (keys m)
       (filter model-sym?)))

(defn models [namespace-map]
  (select-keys namespace-map (model-keys namespace-map)))

(defn tagged-model?
  "Returns true if given model has all `tags`"
  [tags [_model-sym model-attrs]]
  (clojure.set/subset? (set tags) (:zen/tags model-attrs)))

;; TODO: remove asap
(defn collect-models-from
  [{:keys [name-fn tags]} namespace-map]
  (let [ns* (get namespace-map 'ns)]
    (->> namespace-map
         (keep (fn [[k v]]
                 (when (and (model-sym? k)
                            (clojure.set/subset? (set tags) (:zen/tags v)))
                   (name-fn ns* k)))))))

;; TODO: remove asap
(defn collect-models
  [{:keys [name-fn tags]} namespace-map]
  (some->> (models namespace-map)
           (filter (partial tagged-model? tags))
           (map (fn [[model-sym _]]
                  (name-fn (get namespace-map 'ns) model-sym)))
           set))

(defn current-ns [ztx uri]
  (get (uri->edn ztx uri) 'ns))

(defn collect-models-recur
  "Collects tagged models from `ns` down the imports tree.

  NOTE: Circular imports are not allowed."
  [{:keys [get-ns _tags] :as opts} ns]
  (loop [models (collect-models opts ns)
         imports (get ns 'import)]
    (if (empty? imports)
      models
      (recur (->> (collect-models-recur opts (get-ns (first imports)))
                  (clojure.set/union models))
             (rest imports)))))

(defn find-models-to-confirm
  "Traverses all loaded namespaces and collects declared models.
  Returns sorted collection of namespaced model symbols."
  [ztx uri struct-path]
  (let [curr-ns (current-ns ztx uri)
        focused-model (first struct-path)
        opts {:name-fn (partial model-name curr-ns)
              :get-ns #(get-in ztx [:ns %])
              :tags #{'zen/schema}}
        ;; TODO: why zen models are tagged with 'schema instead of 'zen/schema?
        zen-models (collect-models (assoc opts :tags #{'schema}) (get-in ztx [:ns 'zen]))]
    (->> (disj (collect-models-recur opts (ns->edn ztx curr-ns)) focused-model)
         (clojure.set/union zen-models)
         sort
         (mapv str)))
  #_(let [curr-ns (current-ns ztx uri)
          collect-models (make-list-models curr-ns)]
      (->> (:ns ztx)
           (mapcat collect-models)
           sort
           (mapv str))))

;; TODO: remove asap
(defn tagged? [ztx sym tags]
  (let [sym-tags (get-in ztx [:symbols sym :zen/tags])]
    (and (seq sym-tags)
         (clojure.set/subset? (set tags) (set sym-tags)))))

(defn find-models-to-complete-tags
  "Traverses imported namespaces (incl. 'zen) and collects models tagged with 'zen/tag.
  Returns sorted collection of namespaced model symbols."
  [ztx uri]
  (let [curr-ns (current-ns ztx uri)
        opts {:name-fn (partial model-name curr-ns)
              :get-ns #(get-in ztx [:ns %])
              :tags #{'zen/tag}}
        ;; TODO: why zen models are tagged with 'tag instead of 'zen/tag?
        zen-models (collect-models (assoc opts :tags #{'tag}) (get-in ztx [:ns 'zen]))]
    (->> (collect-models-recur opts (ns->edn ztx curr-ns))
         (clojure.set/union zen-models)
         sort
         (mapv str)))
  #_(let [curr-ns (current-ns ztx uri)
          curr-ns-data (ns->edn ztx curr-ns)
          collect-models (make-list-models curr-ns)
          relevant-nsx (->> (get curr-ns-data 'import)
                            (into #{'zen curr-ns})
                            vec)]
      (->> (select-keys (:ns ztx) relevant-nsx)
           (mapcat collect-models)
           (filter (fn [sym]
                     (tagged? ztx sym #{'zen/tag})))
           sort
           (mapv str))))

(defn find-completions [ztx {:keys [uri struct-path]}]
  (cond
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
      (->> confirming-keys
           (map str)
           sort
           vec))

    ;; suggest models to confirm to
    (= :confirms (last struct-path))
    (find-models-to-confirm @ztx uri struct-path)

    (= :zen/tags (last struct-path))
    (find-models-to-complete-tags @ztx uri)

    ;; schema :keys keyword suggestion
    :else
    (let [pos-ctx-struct (get-in @ztx (into [:file uri :last-valid-edn] struct-path))
          type-symbol (:type pos-ctx-struct)]
      (when type-symbol
        (->> (get-in @ztx [:symbols type-symbol :keys]) keys sort (mapv str))))))


