#!/usr/bin/env bb

(require '[babashka.deps :as deps]
         '[babashka.process :as p]
         '[clojure.java.io :as io]
         '[cheshire.core :as json])

(defn ^:private content-length [json]
  (+ 1 (.length json)))

(let [proc (deps/clojure ["-M" "-m" "zen-lang.lsp-server.main"]
                         {:in nil
                          :out nil
                          :err :inherit
                          :shutdown p/destroy})
      out (io/reader (:out proc))
      in (io/writer (:in proc))
      _content-length (binding [*in* out]
                        (read-line))
      {:keys [id method] :as json} (json/parse-stream out true)]
  (println :response json)
  (let [request (json/generate-string
                 {:jsonrpc "2.0"
                  :method "textDocument/completion"
                  :params {:textDocument {:uri "foobar"}
                           :position {:line 1 :character 2}}})
        cl (content-length request)]
    (println :request request)
    (binding [*out* in]
      (println (str "Content-Length: " cl))
      (println "")
      (println request)))
  (loop []
    (println "Waiting for reply")
    (let [_content-length (binding [*in* out]
                            (read-line))
          {:keys [id method] :as json} (json/parse-stream out true)]
      (println :response json)
      (if (= "window/logMessage" method)
        (recur)
        (println "The end")))))
