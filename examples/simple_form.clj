(ns examples.simple-form
  (:require [roterski.doorbell :as doorbell]))

(println (doorbell/cli->data [:map
                             [:name {:default "foo"} :string]]))
