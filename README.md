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
(e.g. `test-resources/test-project/zrc/foo.edn`) and you will get diagnostic feedback.

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


### Connect to zen-lsp repl

Prerequisites:

1) Install VSCode

2) Clone zen-lsp repo: `git clone https://github.com/zen-lang/zen-lsp.git`

We will open `extension.ts` in first instance of VSCode, VSCode1:

3) In VSCode1 open the dir `/home/ier/src/hs/zen-lsp/vscode-extension`

4) In VSCode1 open the file `/home/ier/src/hs/zen-lsp/vscode-extension/src/extension.ts`

5) Run in VSCode1 Terminal the command: `npm install`

6) In VSCode1 start debugging process using menu: `Run -> Start Debugging`

The second VSCode instance will be opened after that automatically. Next steps we will perform in VSCode2:

7) In VSCode2 open project `/home/ier/src/hs/sansara/box/zrc/`

8) Open any .edn file from current project, e.g. `/home/ier/src/hs/sansara/box/zrc/box/cluster.edn`

9) Open VSCode Terminal and switch to Output tab.

10) Select zen-lsp output in Output tab.

11) You will get following log output there:

```
started
[Info  - 2:59:34 PM] nrepl started at 38047
[Info  - 2:59:34 PM] Initializing paths in /home/ier/src/hs/sansara/box/zrc
[Info  - 2:59:34 PM] zen-lsp language server loaded.
opened file, linting: file:///home/ier/src/hs/sansara/box/zrc/box/cluster.edn
```

We will eval our form in Doom Emacs:

10) In Emacs open `/home/ier/src/hs/zen-lsp/server/dev/vs_debug.clj`
11) Connect to repl with `SPC m c`, choose `localhost`, and your `server:port`
12) Eval whole buffer with `SPC m e b`,
13) Go to ln:15 and put your cursor after `(lsp/info "Hi from cider")` form. Eval this form with `SPC m e e`

14) Open VSCode2 and check out the VSCode Output window
```
[Info  - 3:02:10 PM] Hi from cider
```

If you see the same output you have made it!


## License

TODO
