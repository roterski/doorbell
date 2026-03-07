(ns roterski.doorbell
  (:require [roterski.doorbell.cli :as cli]
            [roterski.doorbell.tui :as tui]
            [malli.core :as ma]
            [malli.util :as mu]
            [malli.transform :as mt]
            [babashka.terminal :refer [tty?]]))

(defn ->args
  [args schema]
  (let [schema-keys (mu/keys schema)
        single-arg? (= 1
                       (count args)
                       (count schema-keys))]
    (or
     (when single-arg? [(name (first schema-keys)) (first args)])
     args
     [])))

(defn cli->data
  "Decodes *command-line-args* using schema.
   If the result is invalid and stdout is a TTY,
   starts a TUI to interactively collect the missing data."
  ([schema]
   (cli->data schema *command-line-args*))
  ([schema args]
   (let [decode (ma/decoder schema mt/string-transformer)
         encode (ma/encoder schema mt/string-transformer)
         decode-with-defaults (ma/decoder schema (mt/transformer mt/string-transformer
                                                                 mt/default-value-transformer))
         coerce (ma/coercer schema mt/string-transformer)
         valid? (ma/validator schema)
         data   (cli/cli-args->map (->args args schema))]
     (coerce (if (and (tty? :stdout)
                      (not (valid? (decode data))))
               (tui/tui->result schema (encode (decode-with-defaults data)))
               data)))))
