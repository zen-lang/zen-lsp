(defproject zen-lang/zen-lsp "{{version}}"
  :description "Language server for clj-kondo."
  :url "https://github.com/zen-lang/zen-lsp"
  :scm {:name "git"
        :url "https://github.com/zen-lang/zen-lsp"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.eclipse.lsp4j/org.eclipse.lsp4j "0.12.0"]]
  ;; Oldest version JVM to support.
  ;; We don't compile Java classes in this project, but adding this just in case.
  :javac-options ["--release" "8"]
  :main zen-lang.lsp-server.main
  :profiles {:uberjar {:aot :all
                       :global-vars {*assert* false}
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.spec.skip-macros=true"]}}
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass
                                    :sign-releases false}]])
