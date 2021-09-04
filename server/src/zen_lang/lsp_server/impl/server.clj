(ns zen-lang.lsp-server.impl.server
  {:no-doc true}
  (:import [java.util.concurrent CompletableFuture]
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
           [org.eclipse.lsp4j.services LanguageServer TextDocumentService WorkspaceService LanguageClient])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(set! *warn-on-reflection* true)

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

(def debug? false)

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

(defn lint! [text uri]
  (when-not (str/ends-with? uri ".calva/output-window/output.calva-repl")
    (let [_lang (uri->lang uri)
          _path (-> (java.net.URI. uri)
                   (.getPath))
          {:keys [:findings]} (with-in-str text
                                {:findings [{:row 1
                                             :col 1
                                             :end-row 2
                                             :end-col 2
                                             :message "zen-lang rules"
                                             :level :warning}]})
          lines (str/split text #"\r?\n")
          diagnostics (vec (keep #(finding->Diagnostic lines %) findings))]
      (debug "publishing diagnostics")
      (.publishDiagnostics ^LanguageClient @proxy-state
                           (PublishDiagnosticsParams.
                            uri
                            diagnostics)))))

(deftype LSPTextDocumentService []
  TextDocumentService
  (^void didOpen [_ ^DidOpenTextDocumentParams params]
   (do! (let [td (.getTextDocument params)
              text (.getText td)
              uri (.getUri td)]
          (debug "opened file, linting:" uri)
          (lint! text uri))))

  (^void didChange [_ ^DidChangeTextDocumentParams params]
   (do! (let [td ^TextDocumentIdentifier (.getTextDocument params)
              changes (.getContentChanges params)
              change (first changes)
              text (.getText ^TextDocumentContentChangeEvent change)
              uri (.getUri td)]
          (debug "changed file, linting:" uri)
          (lint! text uri))))

  (^void didSave [_ ^DidSaveTextDocumentParams _params])

  (^void didClose [_ ^DidCloseTextDocumentParams _params]))

(deftype LSPWorkspaceService []
  WorkspaceService
  (^CompletableFuture executeCommand [_ ^ExecuteCommandParams _params])
  (^void didChangeConfiguration [_ ^DidChangeConfigurationParams _params])
  (^void didChangeWatchedFiles [_ ^DidChangeWatchedFilesParams _params]))

(def server
  (proxy [LanguageServer] []
    (^CompletableFuture initialize [^InitializeParams params]
     (CompletableFuture/completedFuture
      (InitializeResult. (doto (ServerCapabilities.)
                           (.setTextDocumentSync (doto (TextDocumentSyncOptions.)
                                                   (.setOpenClose true)
                                                   (.setChange TextDocumentSyncKind/Full)))))))
    (^CompletableFuture initialized [^InitializedParams params]
     (info "Clj-kondo language server loaded. Please report any issues to https://github.com/borkdude/clj-kondo."))
    (^CompletableFuture shutdown []
     (info "Clj-kondo language server shutting down.")
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
