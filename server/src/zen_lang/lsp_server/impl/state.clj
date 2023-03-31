(ns zen-lang.lsp-server.impl.state
  (:require [zen.core :as zen]))


(defn new-context []
  (zen/new-context {:unsafe true}))

(defonce zen-ctx (new-context))

(defonce proxy-state (atom nil))
