(ns zen-lang.lsp-server.main
  {:no-doc true}
  (:gen-class)
  (:require
   [zen-lang.lsp-server.impl.server :as server]))

(set! *warn-on-reflection* true)

;;;; parse command line options

(defn -main [& _options]
  (server/run-server!))

;;;; Scratch

(comment
  )
