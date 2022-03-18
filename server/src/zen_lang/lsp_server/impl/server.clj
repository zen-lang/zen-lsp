(ns zen-lang.lsp-server.impl.server
  {:no-doc true}
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [edamame.core :as e]
   [rewrite-clj.parser :as p]
   [zen-lang.lsp-server.impl.location :refer [get-location]]
   [zen.core :as zen]
   [zen.store :as store])
  (:import
   [java.util.concurrent CompletableFuture]
   [org.eclipse.lsp4j
    Diagnostic
    DiagnosticSeverity
    DidChangeConfigurationParams
    DidChangeTextDocumentParams
    DidChangeWatchedFilesParams
    DidCloseTextDocumentParams
    DidOpenTextDocumentParams
    DidSaveTextDocumentParams
    ExecuteCommandParams
    InitializeParams
    InitializeResult
    InitializedParams
    MessageParams
    MessageType
    Position
    PublishDiagnosticsParams
    Range
    ServerCapabilities
    TextDocumentIdentifier
    TextDocumentContentChangeEvent
    TextDocumentSyncKind
    TextDocumentSyncOptions]
   [org.eclipse.lsp4j.launch LSPLauncher]
   [org.eclipse.lsp4j.services LanguageServer TextDocumentService WorkspaceService LanguageClient]))






(set! *warn-on-reflection* true)

(defn new-context []
  (zen/new-context {:unsafe true}))

(defonce zen-ctx (new-context))

(defonce proxy-state (atom nil))

(defn log! [level & msg]
  (when-let [client @proxy-state]
    (let [msg (str/join " " msg)]
      (.logMessage ^LanguageClient client
                   (MessageParams. (case level
                                     :error MessageType/Error
                                     :warning MessageType/Warning
                                     :info MessageType/Info
                                     :debug MessageType/Log
                                     MessageType/Log) msg)))))

(defn error [& msgs]
  (apply log! :error msgs))

(defn warn [& msgs]
  (apply log! :warn msgs))

(defn info [& msgs]
  (apply log! :info msgs))

(def debug? true)

(defn debug [& msgs]
  (when debug?
    (apply log! :debug msgs)))

(defmacro do! [& body]
  `(try ~@body
        (catch Throwable e#
          (with-open [sw# (java.io.StringWriter.)
                      pw# (java.io.PrintWriter. sw#)]
            (let [_# (.printStackTrace e# pw#)
                  err# (str sw#)]
              (error err#))))))

(defn finding->Diagnostic [lines {:keys [:row :col :end-row :end-col :message :level]}]
  (when (and row col)
    (let [row (max 0 (dec row))
          col (max 0 (dec col))
          start-char (when-let [^String line
                                ;; don't use nth as to prevent index out of bounds
                                ;; exception, see #11
                                (get lines row)]
                       (try (.charAt line col)
                            (catch StringIndexOutOfBoundsException _ nil)))
          expression? (identical? \( start-char)
          end-row (cond expression? row
                        end-row (max 0 (dec end-row))
                        :else row)
          end-col (cond expression? (inc col)
                        end-col (max 0 (dec end-col))
                        :else col)]
      (Diagnostic. (Range. (Position. row col)
                           (Position. end-row end-col))
                   message
                   (case level
                     :info DiagnosticSeverity/Information
                     :warning DiagnosticSeverity/Warning
                     :error DiagnosticSeverity/Error)
                   "zen-lang"))))

(defn uri->lang [uri]
  (when-let [dot-idx (str/last-index-of uri ".")]
    (let [ext (subs uri (inc dot-idx))
          lang (keyword ext)]
      (if (contains? #{:clj :cljs :cljc :edn} lang)
        lang
        :clj))))

(defn error->finding [edn-node error]
  (let [message (:message error)
        ;; path (:path error)
        resource (:resource error)
        resource-path (some-> resource name symbol)
        path (cons resource-path (:path error))
        key? (str/includes? message "unknown key")
        loc (get-location edn-node path key?)
        finding (assoc loc :message message :level :warning)]
    (debug :error error)
    (debug :finding finding)
    finding))


(defn clear-errors! []
  (swap! zen-ctx assoc :errors []))

(defn file->findings [{:keys [text path]}]
  (if-let [edn (try (e/parse-string text)
                    (catch Exception _ nil))]
    (let [_ (store/load-ns zen-ctx edn {:zen/file path})
          errors (:errors @zen-ctx)
          edn-node (p/parse-string text)
          findings (map #(error->finding edn-node %) errors)]
      findings)
    (do (debug "Error parsing")
        nil)))

(defn lint! [text uri]
  ;; TODO: more checks if it's really a zen file
  (when (str/ends-with? uri ".edn")
    (let [path (-> (java.net.URI. uri)
                   (.getPath))
          findings (file->findings {:text text :path path})
          lines (str/split text #"\r?\n")
          diagnostics (vec (keep #(finding->Diagnostic lines %) findings))]
      (debug "publishing diagnostics")
      (.publishDiagnostics ^LanguageClient @proxy-state
                           (PublishDiagnosticsParams.
                            uri
                            diagnostics))
      ;; clear errors for next run
      (clear-errors!))))

(defn did-open-text-document-params->clj
  [^org.eclipse.lsp4j.DidOpenTextDocumentParams params]
  (let [text-document (.getTextDocument params)
        text (.getText text-document)
        uri (.getUri text-document)
        language-id (.getLanguageId text-document)
        version (.getVersion text-document)]
    {:type :open
     :text text
     :uri uri
     :language language-id
     :version version}))

(defn range->clj [^org.eclipse.lsp4j.Range range]
  (let [range-start (.getStart range)
        range-end (.getEnd range)]
    {:start {:line (.getLine range-start)
             :character (.getCharacter range-start)}
     :end {:line (.getLine range-end)
           :character (.getCharacter range-end)}}))

(defn text-document-content-change-event->clj
  [^org.eclipse.lsp4j.TextDocumentContentChangeEvent change]
  (let [range (.getRange change)
        range-length (.getRangeLength change)
        text (.getText change)]
    (cond-> {:text text}
      range (assoc :range (range->clj range))
      range-length (assoc :range-length range-length))))

(defn did-change-text-document-params->clj
  [^org.eclipse.lsp4j.DidChangeTextDocumentParams params]
  (let [text-document (.getTextDocument params)
        version (.getVersion text-document)
        uri (.getUri text-document)
        changes (.getContentChanges params)]
    {:type :change
     :version version
     :uri uri
     :changes (mapv text-document-content-change-event->clj changes)}))

(def completion-trigger-kind->clj
  {org.eclipse.lsp4j.CompletionTriggerKind/Invoked :invoked
   org.eclipse.lsp4j.CompletionTriggerKind/TriggerCharacter :trigger-character
   org.eclipse.lsp4j.CompletionTriggerKind/TriggerForIncompleteCompletions :trigger-for-incomplete-completions})


(defn completion-params->clj
  [^org.eclipse.lsp4j.CompletionParams params]
  (let [position (.getPosition params)
        line (.getLine position)
        character (.getCharacter position)
        uri (.getUri (.getTextDocument params))
        context (.getContext params)
        trigger-character (.getTriggerCharacter context)
        completion-trigger-kind (.getTriggerKind context)]
    {:type :completion
     :position {:line line
                :character character}
     :uri uri
     :context {:trigger-character trigger-character
               :trigger-kind (completion-trigger-kind->clj completion-trigger-kind)}}))

(defn completions [message]
  (let [namespaces (keys (:ns @zen-ctx))
        symbols (keys (:symbols @zen-ctx))
        completions (map #(org.eclipse.lsp4j.CompletionItem. %)
                         (map str (concat namespaces symbols)))]
    (CompletableFuture/completedFuture
     (vec completions))))

(defmulti handle-message
  (fn [message] (:type message)))

(defn set-document [uri content]
  (swap! zen-ctx assoc-in [:file uri :text] content)
  (swap! zen-ctx assoc-in [:file uri :lines] (str/split-lines content)))

(defn load-document [message]
  (if (:text message)
    (set-document (:uri message) (:text message))
    (let [f (slurp (:uri message))]
      (set-document (:uri message) f))))


(defn is-token-char? [chr]
  ; FIXME: incorrect boundaries
  (prn chr)
  (or (<= (int \a) (int chr) (int \z))
      (<= (int \A) (int chr) (int \Z))
      (<= (int \0) (int chr) (int \9))
      (= \: chr) (= \/ chr)))


(defn- extract-token* [line-content pos]
  (let [left-boundary (loop [i (dec pos)]
                        (if (< i 0)
                          0
                          (if (is-token-char? (get line-content i))
                            (recur (dec i))
                            i)))]
    (subs line-content left-boundary pos)))


(defn extract-token [url line pos]
  (-> @zen-ctx
      (get-in [:file url :lines line])
      (extract-token* pos)))


(comment
  ; FIXME: move to tests
  (extract-token* "asdasdadaa" 2)
  )


(defn apply-change [uri change]
  (if-let [_range (:range change)]
    (debug "Error: partial change not supported")
    (set-document uri (:text change))))

(defn update-document [message]
  (doseq [change (:changes message)]
    (apply-change (:uri message) change)))

(defmethod handle-message :open [message]
  (load-document message)
  (lint! (:text message) (:uri message)))

(defmethod handle-message :change [message]
  (update-document message)
  (lint! (:text (first (:changes message))) (:uri message)))

(defmethod handle-message :completion [message]
  (completions message))

(deftype LSPTextDocumentService []
  TextDocumentService
  (^void didOpen [_ ^DidOpenTextDocumentParams params]
   (handle-message (did-open-text-document-params->clj params)))

  (^void didChange [_ ^DidChangeTextDocumentParams params]
   (handle-message (did-change-text-document-params->clj params)))

  (^void didSave [_ ^DidSaveTextDocumentParams _params])

  (^void didClose [_ ^DidCloseTextDocumentParams _params])

  (^CompletableFuture completion [_ ^org.eclipse.lsp4j.CompletionParams params]
   (handle-message (completion-params->clj params))))

(deftype LSPWorkspaceService []
  WorkspaceService
  (^CompletableFuture executeCommand [_ ^ExecuteCommandParams _params])
  (^void didChangeConfiguration [_ ^DidChangeConfigurationParams _params])
  (^void didChangeWatchedFiles [_ ^DidChangeWatchedFilesParams _params]))

(defn edn-files-in-dir [root dir]
  (map fs/file (fs/glob (fs/file root dir) "*.edn")))

(defn initialize-paths [{:keys [root]}]
  (when root
    (info "Initializing paths in" root)
    (let [config-file (fs/file root "zen.edn")]
      (when (fs/exists? config-file)
        (let [config (edn/read-string (slurp config-file))]
          (when-let [paths (:paths config)]
            (swap! zen-ctx assoc :paths (mapv #(str (fs/file root %)) paths))))))))

(def server
  (proxy [LanguageServer] []
    (^CompletableFuture initialize [^InitializeParams params]
     (initialize-paths {:root (.getRootPath params)})
     (CompletableFuture/completedFuture
      (InitializeResult. (doto (ServerCapabilities.)
                           (.setTextDocumentSync (doto (TextDocumentSyncOptions.)
                                                   (.setOpenClose true)
                                                   (.setChange TextDocumentSyncKind/Full)))
                           (.setCompletionProvider (org.eclipse.lsp4j.CompletionOptions. false [":" "/"]))))))
    (^CompletableFuture initialized [^InitializedParams params]
     (info "zen-lsp language server loaded."))
    (^CompletableFuture shutdown []
     (info "zen-lsp language server shutting down.")
     (CompletableFuture/completedFuture 0))

    (^void exit []
     (debug "trying to exit clj-kondo")
     (shutdown-agents)
     (debug "agents down, exiting with status zero")
     (System/exit 0))

    (getTextDocumentService []
      (LSPTextDocumentService.))

    (getWorkspaceService []
      (LSPWorkspaceService.))))

(defn run-server! []
  (let [launcher (LSPLauncher/createServerLauncher server System/in System/out)
        proxy ^LanguageClient (.getRemoteProxy launcher)]
    (reset! proxy-state proxy)
    (.startListening launcher)
    (debug "started")))

(Thread/setDefaultUncaughtExceptionHandler
 (proxy [Thread$UncaughtExceptionHandler] []
   (uncaughtException [t ^Exception e]
     (debug "Throwable: " (.getMessage e)))))
