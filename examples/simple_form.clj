(ns examples.simple-form
  (:require [roterski.doorbell :as doorbell]))

(println (str (doorbell/cli->data [:map
                                   [:name {:default "foo"} [:string {:min 3}]]])))
