(ns zen-lang.lsp-server.impl.autocomplete)

(defn gather-confirming-keys
  [ztx confirmings]
  (when (seq confirmings)
    (mapcat #(concat (keys (get-in @ztx [:symbols % :keys]))
                     (gather-confirming-keys ztx (:confirms (get-in @ztx [:symbols %]))))
            confirmings)))

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
          (vec (map str confirming-keys)))

    ;; schema :keys keyword suggestion
    :else
    (let [pos-ctx-struct (get-in @ztx (into [:file uri :last-valid-edn] struct-path))
          type-symbol (:type pos-ctx-struct)]
      (when type-symbol
        (->> (get-in @ztx [:symbols type-symbol :keys]) keys sort (mapv str))))))


(comment
  )
