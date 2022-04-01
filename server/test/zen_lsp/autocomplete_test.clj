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
               (zl/find-completions ztx {:uri f :struct-path path})))))

    (testing "find `symbols` applied by given context"
      (is (= (conj (->> @ztx :symbols keys (mapv str) set) "schema" "schema2")
             (let [f "test-resources/test-project/zrc/baz.edn"
                   path ['schema2 :qux :type]]
               (set (zl/find-completions ztx {:uri f :struct-path path}))))) )
    ))
