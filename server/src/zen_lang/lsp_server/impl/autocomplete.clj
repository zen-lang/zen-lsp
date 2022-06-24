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

(defn make-list-models [ns-sym]
  (fn list-models [[zns content]]
    (->> (keys content)
         (filter model-sym?)
         (map (fn local-or-external-model-name [sym]
                (if (= ns-sym zns)
                  sym
                  (symbol (str zns) (str sym))))))))

(defn current-ns [ztx uri]
  (get (uri->edn ztx uri) 'ns))

(defn find-models-to-confirm
  "Traverses all loaded namespaces and collects declared models.
  Returns sorted collection of namespaced model symbols."
  [ztx uri]
  (let [curr-ns (current-ns ztx uri)
        collect-models (make-list-models curr-ns)]
    (->> (:ns ztx)
         (mapcat collect-models)
         sort
         (mapv str))))


(defn find-models-to-complete-tags
  "Traverses imported namespaces (incl. 'zen) and collects declared models.
  Returns sorted collection of namespaced model symbols."
  [ztx uri]
  (let [curr-ns (current-ns ztx uri)
        curr-ns-data (ns->edn ztx curr-ns)
        collect-models (make-list-models curr-ns)
        imported-nsx (->> (get curr-ns-data 'import)
                          (into #{'zen})
                          vec)]
    (->> (select-keys (:ns ztx) imported-nsx)
         (mapcat collect-models)
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
    (find-models-to-confirm @ztx uri)

    (= :zen/tags (last struct-path))
    (find-models-to-complete-tags @ztx uri)

    ;; schema :keys keyword suggestion
    :else
    (let [pos-ctx-struct (get-in @ztx (into [:file uri :last-valid-edn] struct-path))
          type-symbol (:type pos-ctx-struct)]
      (when type-symbol
        (->> (get-in @ztx [:symbols type-symbol :keys]) keys sort (mapv str))))))


