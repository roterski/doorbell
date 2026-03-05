(ns roterski.doorbell.cli
  (:require [malli.core :as ma]
            [clojure.string :as str]))

(defn ->key-path
  [v]
  (->> (-> (cond
             (str/starts-with? v ":") (subs v 1)
             (str/starts-with? v "--") (subs v 2)
             (str/starts-with? v "-") (subs v 1)
             :else v)
           (str/split #"\."))
       (mapv keyword)))

(defn cli-args->map
  [args]
  (->> (ma/parse [:* [:catn
                      [:key :string]
                      [:value :string]]]
                 args)
       (reduce (fn [acc {{:keys [key value]} :values}]
                 (assoc-in acc (->key-path key) value))
               {})))
