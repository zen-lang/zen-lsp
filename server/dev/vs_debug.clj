(ns vs-debug
  (:require [zen-lang.lsp-server.impl.server :as lsp]
            [zen-lang.lsp-server.impl.log :as log]
            [nrepl.server :as nrepl-server]
            [cider.nrepl :refer (cider-nrepl-handler)]))


(defn -main  [& _]
  (let [nrepl-srv (nrepl-server/start-server :handler cider-nrepl-handler)
        lsp-srv (lsp/run-server!)]
    (spit ".nrepl-port" (:port nrepl-srv))
    (log/info (str "nrepl started at " (:port nrepl-srv)))))

(comment

  (log/info "Hi from cider")

  )
