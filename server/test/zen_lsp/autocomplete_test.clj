(ns zen-lsp.autocomplete-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [zen-lang.lsp-server.impl.server :as server]
            [zen-lang.lsp-server.impl.autocomplete :as zl]))

#_(defn file->message [f]
  {:text (slurp f) :uri f})

#_(def ztx
  (do (alter-var-root #'server/zen-ctx (constantly (server/new-context)))
      (server/initialize-paths {:root "test-resources/test-project"})
      (server/load-document (file->message "test-resources/test-project/zrc/baz.edn"))
      (server/load-document (file->message "test-resources/test-project/zrc/foo.edn"))
      server/zen-ctx))

#_(deftest find-completions-test
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

    (testing "following hierarchy of symbols by the :confirm key gather all possible keys"
      (is (= (let [parent-keys       (get-in @ztx [:symbols 'qux/parent-schema :keys])
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
