(ns zen-lsp.core-test
  (:require
   [clojure.test :refer [deftest is]]
   [zen-lang.lsp-server.impl.server :as server]
   [zen-lsp.test-utils :refer [assert-submaps]]))

(deftest multi-file-project-test
  (alter-var-root #'server/zen-ctx (constantly (server/new-context)))
  (server/initialize-paths {:root "test-resources/test-project"})
  (assert-submaps
   '(;; FIXME: the first warning doesn't have a location
     {:message "Could not resolve symbol 'baz/schema in foo/schema", :level :warning}
     {:row 8, :col 28, :end-row 8, :end-col 38,
      :message "Expected symbol 'baz/schema tagged with '#{zen/schema}, but only #{}", :level :warning})
   (server/file->findings {:text (slurp "test-resources/test-project/zrc/foo.edn")
                           :path "test-resources/test-project/zrc/foo.edn"}))
  (server/clear-errors!)
  (is (empty?
       (server/file->findings
        {:text (slurp "test-resources/test-project/zrc/bar.edn")
         :path "test-resources/test-project/zrc/bar.edn"}))))


