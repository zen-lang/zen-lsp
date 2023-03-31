(ns zen-lsp.definition-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [zen-lang.lsp-server.impl.state :as state]
            [zen-lang.lsp-server.impl.server :as server]
            [zen-lang.lsp-server.impl.definition :as zl]
            [clojure.java.io :as io]))

(defn file->message [f]
  {:text (slurp f) :uri f})

(defn absolute-uri [relative-path]
  (->> (io/file relative-path)
       (.getAbsolutePath)))

(def ztx
  (do (server/initialize-paths {:root "test-resources/test-project"})
      (server/load-document (file->message (absolute-uri "test-resources/test-project/zrc/foo.edn")))
      (server/load-document (file->message (absolute-uri "test-resources/test-project/zrc/bar.edn")))
      state/zen-ctx))


(deftest find-completions-test
  (testing "`find-completion` should"

    (testing "find `symbol` definition under cursor"

      (is (= {:uri (str "file:" (absolute-uri "test-resources/test-project/zrc/bar.edn"))
              :row 3
              :col 1
              :end-row 3
              :end-col 7}
             (zl/find-definition ztx
                                 {:type :definition
                                  :position {:line 6, :character 30}
                                  :uri (absolute-uri "test-resources/test-project/zrc/foo.edn")})))

      )

    ))
