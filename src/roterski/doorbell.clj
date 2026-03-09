(ns roterski.doorbell
  (:require [roterski.doorbell.cli :as cli]
            [roterski.doorbell.tui.autocomplete :as ac]
            [roterski.doorbell.tui.schema-to-data :as schema-to-data]
            [malli.core :as ma]
            [malli.transform :as mt]
            [babashka.terminal :refer [tty?]]))

(defn cli->data
  "Decodes *command-line-args* using schema.
   If the result is invalid and stdout is a TTY,
   starts a TUI to interactively collect the missing data."
  ([schema]
   (cli->data schema *command-line-args*))
  ([schema args]
   (let [encode (ma/encoder schema mt/string-transformer)
         decode-with-defaults (ma/decoder schema (mt/transformer mt/string-transformer
                                                                 mt/default-value-transformer))
         coerce (ma/coercer schema mt/string-transformer)
         data   (cli/cli-args->map args schema)
         valid? (ma/validate schema data)]
     (coerce (if (and (tty? :stdout)
                      (not valid?))
               (->> data
                    decode-with-defaults
                    encode
                    (schema-to-data/tui->result schema))
               data)))))

(defn autocomplete
  "Run an interactive autocomplete TUI.

   `search-fn` — (fn [query] items), called asynchronously on each keystroke.
   `opts`      — optional map validated against `Options` schema.

   Returns the selected item, or nil if the user cancelled."
  ([search-fn] (autocomplete search-fn {}))
  ([search-fn opts]
   (if-not (tty? :stdout)
     (println `autocomplete "needs tty support")
     (ac/autocomplete search-fn opts))))
