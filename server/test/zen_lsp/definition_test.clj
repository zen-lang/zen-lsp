(ns zen-lsp.definition-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [zen-lang.lsp-server.impl.state :as state]
            [zen-lang.lsp-server.impl.server :as server]
            [zen-lang.lsp-server.impl.definition :as zl]
            [clojure.java.io :as io]))

(defn file->message [f]
  {:text (slurp f) :uri f})

(defn absolute-path [relative-path]
  (->> (io/file relative-path)
       (.toURI)
       (str)))


(def ztx
  (do (server/initialize-paths {:root "test-resources/test-project"})
      (server/load-document (file->message (absolute-path "test-resources/test-project/zrc/foo.edn")))
      (server/load-document (file->message (absolute-path "test-resources/test-project/zrc/bar.edn")))
      state/zen-ctx))


(deftest find-definition-test
  (testing "`find-defintition` should find `symbol` definition under cursor"

    (testing "when the `symbol` is fully-qualified"
      (is (= {:uri (absolute-path "test-resources/test-project/zrc/bar.edn")
              :row 3
              :col 1
              :end-row 3
              :end-col 7}
             (zl/find-definition ztx
                                 {:type :definition
                                  :position {:line 6, :character 30}
                                  :uri (absolute-path "test-resources/test-project/zrc/foo.edn")}))))

    (testing "when the `symbol` is not fully qualified and corresponds to definition in current ns "
      (is (= {:uri (absolute-path "test-resources/test-project/zrc/foo.edn")
              :row 3
              :col 1
              :end-row 3
              :end-col 7}
             (zl/find-definition ztx
                                 {:type :definition
                                  :position {:line 14, :character 15}
                                  :uri (absolute-path "test-resources/test-project/zrc/foo.edn")}))))

    (testing "when the `symbol` is not fully qualified and located in import section"
      (is (= {:uri (absolute-path "test-resources/test-project/zrc/bar.edn")
              :row 0
              :col 0
              :end-row 0
              :end-col 0}
             (zl/find-definition ztx
                                 {:type :definition
                                  :position {:line 1, :character 10}
                                  :uri (absolute-path "test-resources/test-project/zrc/foo.edn")}))))


    (testing "when the `symbol` is reserved - 'ns"
      (is (= {:uri (absolute-path "test-resources/test-project/zrc/foo.edn")
              :row 0
              :col 2
              :end-row 0
              :end-col 2}
             (zl/find-definition ztx
                                 {:type :definition
                                  :position {:line 0, :character 2}
                                  :uri (absolute-path "test-resources/test-project/zrc/foo.edn")}))))

    (testing "when the `symbol` is reserved - 'import"
      (is (= {:uri (absolute-path "test-resources/test-project/zrc/foo.edn")
              :row 1
              :col 2
              :end-row 1
              :end-col 2}
             (zl/find-definition ztx
                                 {:type :definition
                                  :position {:line 1, :character 2}
                                  :uri (absolute-path "test-resources/test-project/zrc/foo.edn")}))))

    (testing "when the `symbol` is a zen namespace name"
      (is (= {:uri (absolute-path "test-resources/test-project/zrc/foo.edn")
              :row 0
              :col 4
              :end-row 0
              :end-col 4}
             (zl/find-definition ztx
                                 {:type :definition
                                  :position {:line 0, :character 4}
                                  :uri (absolute-path "test-resources/test-project/zrc/foo.edn")}) )))

    ))
