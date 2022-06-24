(ns zen-lsp.autocomplete-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [zen-lang.lsp-server.impl.server :as server]
            [zen-lang.lsp-server.impl.autocomplete :as zl]))

(defn file->message [f]
  {:text (slurp f) :uri f})

(def ztx
  (do (alter-var-root #'server/zen-ctx (constantly (server/new-context)))
      (server/initialize-paths {:root "test-resources/test-project"})
      (server/load-document (file->message "test-resources/test-project/zrc/baz.edn"))
      (server/load-document (file->message "test-resources/test-project/zrc/foo.edn"))
      (server/load-document (file->message "test-resources/test-project/zrc/qux.edn"))
      server/zen-ctx))


(deftest ^:todo find-completions-test
  (testing "`find-completion` should"

    (testing "find keys of schema under cursor (keywords)"

      (is (= [":every" ":maxItems" ":minItems" ":schema-index"]
             (let [f "test-resources/test-project/zrc/baz.edn"
                   path ['schema2 :keys :foo]]
               (zl/find-completions ztx {:uri f :struct-path path}))))

      (is (= [":maxLength" ":minLength" ":regex" ":tags"]
             (let [f "test-resources/test-project/zrc/baz.edn"
                   path ['schema2 :keys :baz]]
               (zl/find-completions ztx {:uri f :struct-path path})))))

    (testing "find `symbols` applied by given context"
      (is (= (conj (->> @ztx :symbols keys (mapv str) set) "schema" "schema2")
             (let [f "test-resources/test-project/zrc/baz.edn"
                   path ['schema2 :qux :type]]
               (set (zl/find-completions ztx {:uri f :struct-path path}))))))

    #_(testing "following hierarchy of symbols by the :confirm key gather all possible keys"
      (is (= [] #_(let [parent-keys       (get-in @ztx [:symbols 'qux/parent-schema :keys])
                   grand-parent-keys (get-in @ztx [:symbols 'qux/grand-parent-schema :keys])
                   imported-keys     (get-in @ztx [:symbols 'baz/schema2 :keys])]
               (->> (concat parent-keys grand-parent-keys imported-keys)
                    keys
                    set
                    (map str)
                    vec))
             (let [f "test-resources/test-project/zrc/qux.edn"]
               (zl/find-completions ztx {:uri f :struct-path ['schema :keys]})))))
    ))

(deftest complete-gathered-confirmed-keys
  (testing "following hierarchy of symbols by the :confirm key gather all possible keys"
    (is (= [":baz" ":foo" ":grand-parent-key" ":parent-key" ":qux"]
           (let [f "test-resources/test-project/zrc/qux.edn"]
             (zl/find-completions ztx {:uri f :struct-path ['schema :keys]}))))))

(deftest complete-models-to-confirm
  (let [f "test-resources/test-project/zrc/qux.edn"
        result (zl/find-completions ztx {:uri f :struct-path ['schema :confirms]})]
    (is (seq result))
    (is (= ["grand-parent-schema"
            "parent-schema"
            "schema"
            "bar/schema"
            "baz/schema"
            "baz/schema2"
            "foo/schema"
            "zen/any"]
           (take 8 result)))))

(deftest complete-tags
  (testing "should consider models from current, imported and zen core namespaces"
    (let [f "test-resources/test-project/zrc/baz.edn"
          result (zl/find-completions ztx {:uri f :struct-path ['schema2 :zen/tags]})]
      (is (= ["schema"
              "schema2"
              "foo/schema"
              "zen/any"
              "zen/vector"]
             (conj (vec (take 4 result)) (last result)))))))

;;; FIXME exploration tools, remove asap
(defn ctx [] (deref ztx))

(defn in-ctx [file-name]
  (let [file-path (str "test-resources/test-project/zrc/" file-name)]
    (get-in (ctx) [:file file-path])))

(defn edn [file-name]
  (-> (in-ctx file-name)
      :last-valid-edn))

(comment

  (every? symbol? [ 'foo])

  (-> @server/zen-ctx :file (get "test-resources/test-project/zrc/qux.edn") :last-valid-edn (get 'schema))
  (def qux-schema *1)

  (zl/gather-confirming-keys server/zen-ctx ['qux/parent-schema])

  (tap> (edn "baz.edn"))
  (tap> (edn "qux.edn"))
  (tap> (ctx))

  (-> (edn "baz.edn") (get 'schema))

  (get-in @ztx [:file "test-resources/test-project/zrc/baz.edn"])
  (get-in @ztx [:file "test-resources/test-project/zrc/qux.edn"])

;;
  )
