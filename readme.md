# Doorbell
[![Clojars Project](https://img.shields.io/clojars/v/com.roterski/doorbell.svg)](https://clojars.org/com.roterski/doorbell)

An easy way to make your babashka scripts interactive, with two simple TUI components:
- `roterski.doorbell/cli->data`
- `roterski.doorbell/autocomplete`

add & mix them together in front of your bb tasks/scripts.


## Installation

Add to your `bb.edn` or `deps.edn`:

```clojure
{:deps {io.github.roterski/doorbell {:git/url "https://github.com/roterski/doorbell"
                                     :git/sha "9ffe55a486408b56220e7ad6468da064f5e297a8"}}}
```

## Usage

### `roterski.doorbell/cli->data`
 returns data coerced to `schema` that comes from combining `*command-line-args*` with user-input from interactive TUI.

#### Simple form

```clojure
(ns examples.simple-form
  (:require [roterski.doorbell :as doorbell]))

(println (str (doorbell/cli->data [:map
                                   [:name {:default "foo"} [:string {:min 3}]]])))
```
run it with:
````
bb examples/simple_form.clj
````
when any args are missing (or invalid) it will start the interactive TUI:
![simple-form](doc/simple-form.gif)

when you provide all args:
````
bb examples/simple_form.clj name bob
````

or for single key schemas, when you provide single arg:
````
bb examples/simple_form.clj bob
````

it will just coerce the data without starting the interactive TUI:

![non-interactive](doc/non-interactive.png)

but, again, if you provide invalid data, it'll start the TUI:

![invalid](doc/invalid.png)


#### Form with nested fields

```clojure
(println (doorbell/cli->data [:map
                              [:first-name :string]
                              [:last-name {:optional true} :string]
                              [:age [:int {:min 18}]]
                              [:looks
                               [:map
                                [:eye-color [:enum :brown :blue :green :hazel :gray]]
                                [:tattoos? :boolean]]]
                              [:mood {:default "good"} :string]]))
```

Run interactively (launches TUI when args are missing):

```sh
bb examples/person_form.clj
```
![demo](doc/demo.gif)

Run non-interactively by providing all required args:

```sh
bb examples/person_form.clj -first-name Bob --age 30 looks.eye-color blue "looks.tattoos?" true :mood okay "do-you-know-them?" true
```

- keys can be prefixed with `-`,  `--`, `:` or nothing - the lib treats them the same way, ignoring the prefix
- nested keys use dot notation on the command line (e.g. `looks.eye-color`)

### Limitations
- args must be key/value pair (unless it's a single arg for a single arg schema)
- attributes ending with `?` need to be wrapped in `" "` quotes
- no support for vectors/sequences values in schemas

### `roterski.doorbell/autocomplete`

A separate component only supported in tty terminals.

It allows selecting items from dynamic (including asynchronously fetched) sequences:

````clojure
(doorbell/autocomplete
 (fn [query]
   (->> animals
        (filter #(str/starts-with?
                  % (str/lower-case query))))))
````

![autocomplete](doc/autocomplete.gif)
