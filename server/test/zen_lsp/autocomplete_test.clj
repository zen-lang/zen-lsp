(ns zen-lsp.autocomplete-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [zen.store :as store]
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

      ;; load actual data into current context
      (store/read-ns server/zen-ctx 'foo)
      (store/read-ns server/zen-ctx 'baz)
      (store/read-ns server/zen-ctx 'qux)

      ;; fixtures without circular dependencies
      (server/load-document (file->message "test-resources/test-project/zrc/a.edn"))
      (server/load-document (file->message "test-resources/test-project/zrc/b.edn"))
      (server/load-document (file->message "test-resources/test-project/zrc/c.edn"))
      (store/read-ns server/zen-ctx 'a)
      (store/read-ns server/zen-ctx 'b)
      (store/read-ns server/zen-ctx 'c)

      server/zen-ctx))


(deftest find-completions-test
  (testing "`find-completion` should"

    (testing "find keys of schema under cursor (keywords)"

      (is (= [":every" ":maxItems" ":minItems" ":schema-index"]
             (let [f "test-resources/test-project/zrc/baz.edn"
                   path ['schema2 :keys :foo]]
               (zl/find-completions ztx {:uri f :struct-path path}))))

      (is (= [":maxLength" ":minLength" ":regex" ":tags"]
             (let [f "test-resources/test-project/zrc/baz.edn"
                   path ['schema2 :keys :baz]]
               (zl/find-completions ztx {:uri f :struct-path path})))))))

;; FIXME: provide separate fixtures
(deftest ^:skip ^:fixme complete-type-symbols
  (testing "find `symbols` applied by given context"
    (is (= [] #_(conj (->> @ztx :symbols keys (mapv str) set) "schema" "schema2")
           (let [f "test-resources/test-project/zrc/baz.edn"
                 path ['schema2 :qux :type]]
             (set (zl/find-completions ztx {:uri f :struct-path path})))))))

;; FIXME: provide separate fixtures
(deftest complete-gathered-confirmed-keys
  (testing "following hierarchy of symbols by the :confirm key gather all possible keys"
    (is (= [":baz" ":foo" ":grand-parent-key" ":parent-key" ":qux"]
           (let [f "test-resources/test-project/zrc/qux.edn"]
             (zl/find-completions ztx {:uri f :struct-path ['schema :keys]}))))))

(deftest complete-models-to-confirm
  (let [f "test-resources/test-project/zrc/a.edn"
        result (zl/find-completions ztx {:uri f :struct-path ['schema :confirms]})]
    (is (seq result))
    (is (= ["b/schema"
            "c/schema"
            "zen/any"
            "zen/vector"]
           (conj (vec (take 3 result)) (last result))))))

(deftest complete-tags
  (testing "should consider tagged models from current, imported and zen core namespaces"
    (let [f "test-resources/test-project/zrc/a.edn"
          result (zl/find-completions ztx {:uri f :struct-path ['schema :zen/tags]})]
      (is (= ["c/schema"
              "zen/coll"
              "zen/fn"
              "zen/primitive"
              "zen/schema"
              "zen/schema-fx"
              "zen/tag"
              "zen/validation-fn"]
             result)))))


(comment

  (tap> @ztx)

  (zl/tagged? @ztx 'zen/string #{'zen/type 'zen/primitive 'zen/schema})

  (let [opts {:name-fn (partial zl/model-name 'user)
              :tags #{'zen/schema}}]
    (zl/collect-models-from opts (get-in @ztx [:ns 'baz])))

  (let [opts {:name-fn (partial zl/model-name 'user)
              :tags #{'zen/schema}}]
    (zl/collect-models opts (get-in @ztx [:ns 'baz])))

  (let [f "test-resources/test-project/zrc/a.edn"]
    (zl/find-completions ztx {:uri f :struct-path ['schema :zen/tags]}))

  ;;
  )
