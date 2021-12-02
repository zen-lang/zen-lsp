(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'my/lib1)
;; (def version (format "1.2.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
;; (def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))
;; (def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [opts]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj (merge opts
                        {:basis basis
                         :src-dirs ["src"]
                         :class-dir class-dir}))
  (b/uber (merge opts {:class-dir class-dir
                       :basis basis
                       :main 'zen-lang.lsp-server.main})))
