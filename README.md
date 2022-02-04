# zen-lsp

This repo contains the code for:

- the zen-lsp-server
- the VSCode extension which bundles the server and client.

## Install

### Manual

To install the zen-lsp server on Unix-like systems, you can run the `install`
script from this repository:

```
$ curl -sLO https://raw.githubusercontent.com/zen-lang/zen-lsp/main/install
$ chmod +x install
$ ./install
```

This will download the zen-lsp uberjar and place it in `~/.zen-lsp`. It will
also create a `zen-lsp` script inside `/usr/local/bin`.

### Emacs

First run the manual installation of the zen-lsp uberjar.  Ensure that the
`lsp-mode` package is installed. Then place this in your `init.el`:

``` elisp
(require 'lsp-mode)
(lsp-register-client
 (make-lsp-client :new-connection (lsp-stdio-connection "zen-lsp")
                  :major-modes '(clojure-mode)
                  :add-on? t
                  :activation-fn (lsp-activate-on "clojure")
                  :server-id 'zen-lang))
```

### VSCode

Download the `.vsix` file from
[releases](https://github.com/zen-lang/zen-lsp/releases) and install it in
VSCode:

    Extensions > Install from VSIX...

## Build

To run build tasks in this project, use [babashka](https://babashka.org/). You
will also need [lein](https://leiningen.org/) (although we could port this
project to the new clojure CLI).

Run `bb tasks` to get a full overview of how to build this project.

### Server

To test the server, run:

    bb server:test

To build the server uberjar, run:

    bb server:build

### VSCode extension

#### Local development

Enter the extension directory:

     cd vscode-extension

To intall the dependencies, run:

     npm install

Open VSCode:

    code .

Then hit `F5 (Start Debugging)` to run the extension. Edit a zen-lang file
(`.edn`) and you will get diagnostic feedback.

#### VSIX package

To build the vscode extension, e.g. to distribute to colleagues for testing,
run:

    bb vscode-package

This will create a `.vsix` file in the `vscode-extension` directory which you
can install in VSCode.


#### start with cider nrepl

Extension will be started with vscode-extension/debug-srv script
This script start LSP server + nrepl with cider mw
Then you can connect to nrepl (see .nrepl-port) from emacs

## License

TODO
