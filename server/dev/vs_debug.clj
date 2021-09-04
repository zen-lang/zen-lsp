(ns vs-debug
  (:require [zen-lang.lsp-server.impl.server :as lsp]
            [nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]))


(defn -main  [& _]
  (let [nrepl-srv (nrepl-server/start-server :handler cider-nrepl-handler)
        lsp-srv (lsp/run-server!)]
    (spit ".nrepl-port" (:port nrepl-srv))
    (lsp/info (str "nrepl started at " (:port nrepl-srv)))))

(comment

  (lsp/info "Hi from cider")

  )
