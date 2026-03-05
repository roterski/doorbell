(ns examples.person-form
  (:require [roterski.doorbell :as doorbell]))

(println (doorbell/cli->data [:map
                             [:first-name :string]
                             [:last-name {:optional true} :string]
                             [:age [:int {:min 18}]]
                             [:looks
                              [:map
                               [:eye-color [:enum :brown :blue :green :hazel :gray]]
                               [:tattoos? :boolean]]]
                             [:mood {:default "good"} :string]]))

;; you can chain multiple forms
(println (doorbell/cli->data [:map
                             [:do-you-know-them? :boolean]]))
