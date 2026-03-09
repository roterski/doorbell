(ns examples.autocomplete
  (:require [roterski.doorbell :as doorbell]
            [clojure.string :as str]))

(def animals
  ["cat" "dog" "elephant" "tiger" "dolphin" "eagle" "wolf" "bear" "fox" "owl"
   "lion" "zebra" "giraffe" "penguin" "koala" "panda" "jaguar" "cheetah" "hawk" "salmon"
   "octopus" "whale" "shark" "parrot" "turtle" "rabbit" "deer" "moose" "otter" "seal"
   "flamingo" "gorilla" "leopard" "cobra" "bison" "raccoon" "badger" "lynx" "coyote" "crane"])

(println "Your chosen animal is: "
         (doorbell/autocomplete (fn [query]
                                  ;; (Thread/sleep 2000) you may add blocking functions here, it won't break
                                  (->> animals
                                       (filter #(str/starts-with? % (str/lower-case query)))))))
