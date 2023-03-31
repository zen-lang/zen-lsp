(ns zen-lang.lsp-server.impl.log
  {:no-doc true}
  (:require
   [clojure.string :as str]
   [zen-lang.lsp-server.impl.state :refer [proxy-state]])
  (:import
   [org.eclipse.lsp4j
    MessageParams
    MessageType]
   [org.eclipse.lsp4j.services LanguageClient]))


(defn log! [level & msg]
  (when-let [client @proxy-state]
    (let [msg (str/join " " msg)]
      (.logMessage ^LanguageClient client
                   (MessageParams. (case level
                                     :error MessageType/Error
                                     :warning MessageType/Warning
                                     :info MessageType/Info
                                     :debug MessageType/Log
                                     MessageType/Log)
                                   msg)))))

(defn error [& msgs]
  (apply log! :error msgs))

(defn warn [& msgs]
  (apply log! :warn msgs))

(defn info [& msgs]
  (apply log! :info msgs))

(def debug? (= "true" (System/getenv "ZEN_LSP_DEBUG")))

(defn debug [& msgs]
  (when debug?
    (apply log! :debug msgs)))
