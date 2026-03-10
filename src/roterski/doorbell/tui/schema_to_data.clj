(ns roterski.doorbell.tui.schema-to-data
  (:require [roterski.doorbell.tui.utils :refer [clear]]
            [roterski.doorbell.tui.styles :as styles]
            [charm.core :as charm]
            [malli.core :as ma]
            [malli.transform :as mt]
            [malli.error :as me]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Schema
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; Schema introspection
;; ---------------------------------------------------------------------------

(defn schema-fields
  "Recursively extract field descriptors from a malli :map schema.
   Flattens nested maps into a flat list with paths and depth.
   Nested maps emit a :group header followed by their children."
  ([schema] (schema-fields schema [] 0))
  ([schema prefix depth]
   (vec
    (mapcat
     (fn [[k props vs]]
       (let [path    (conj prefix k)
             vs-type (ma/type (ma/schema vs))]
         (if (= :map vs-type)
           (into [{:type :group :key k :depth depth}]
                 (schema-fields vs path (inc depth)))
           [(cond-> {:path        path
                     :key         k
                     :type        vs-type
                     :optional?   (boolean (:optional props))
                     :default     (:default props)
                     :value-schema vs
                     :depth       depth}
              (= :enum vs-type)
              (assoc :options (vec (ma/children (ma/schema vs)))))])))
     (ma/children (ma/schema schema))))))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- option->str [o]
  (if (keyword? o) (name o) (str o)))

(defn focus-at
  "Focus the input at `idx`, blur all others."
  [inputs idx]
  (vec (map-indexed
        (fn [i input]
          (let [focus? (= i idx)]
            (if (= (:type input) :boolean)
              (assoc input :focused focus?)
              ((if focus? charm/text-input-focus charm/text-input-blur) input))))
        inputs)))

(defn move-focus [state delta]
  (let [n   (count (:input-fields state))
        idx (mod (+ (:focused state) delta) n)]
    (-> state
        (assoc :focused idx)
        (update :inputs focus-at idx))))

(defn collect-values
  "Gather input values into a nested map using field paths."
  [fields inputs]
  (reduce (fn [m [field input]]
            (let [v (if (= (:type input) :boolean)
                      (:value input)
                      (let [s (charm/text-input-value input)]
                        (when-not (str/blank? s) s)))]
              (if (some? v)
                (assoc-in m (:path field) v)
                m)))
          {}
          (map vector fields inputs)))

(defn submit
  "Validate collected values against the schema."
  [state]
  (let [{:keys [input-fields inputs schema]} state
        raw    (collect-values input-fields inputs)
        values (ma/decode schema raw (mt/string-transformer))
        expl   (ma/explain schema values)]
    (if expl
      (assoc state :errors (me/humanize expl))
      (assoc state :errors nil :submitted true :result values))))

;; ---------------------------------------------------------------------------
;; Init / Update / View
;; ---------------------------------------------------------------------------

(defn make-input
  "Create an input component for a field based on its type.
   When `initial` is provided, it prefills the input."
  [{:keys [type default options]} initial]
  (case type
    :boolean
    {:type :boolean
     :value (boolean (if (some? initial) initial (or default false)))
     :focused false}

    :enum
    (let [str-opts (mapv option->str options)
          init-val (or (some-> initial str)
                       (some-> default str)
                       (first str-opts))]
      (cond-> (charm/text-input
               :prompt ""
               :placeholder (str/join " | " str-opts)
               :focused false)
        init-val (charm/text-input-set-value init-val)))

    ;; default: text-input
    (let [init-val (or (some-> initial str) nil)]
      (cond-> (charm/text-input
               :prompt ""
               :placeholder (or (some-> default str) "")
               :focused false)
        init-val (charm/text-input-set-value init-val)))))

(defn make-init
  "Return an init fn that builds form state from a malli map schema."
  [schema data]
  (fn []
    (let [items       (schema-fields schema)
          input-fields (vec (remove #(= :group (:type %)) items))
          inputs      (mapv (fn [{:keys [path] :as field}]
                              (make-input field (get-in data path)))
                            input-fields)]
      [{:schema       schema
        :items        items
        :input-fields input-fields
        :inputs       (focus-at inputs 0)
        :focused      0
        :errors       nil
        :submitted    false
        :result       nil}
       nil])))

(defn update-fn [state msg]
  (cond
    (charm/key-match? msg "ctrl+c")
    [state charm/quit-cmd]

    (:submitted state)
    [state charm/quit-cmd]

    (:submitted state)
    [state nil]

    (charm/key-match? msg :escape)
    [state charm/quit-cmd]

    ;; navigate fields
    (or (charm/key-match? msg "tab")
        (charm/key-match? msg :down))
    [(move-focus state 1) nil]

    (or (charm/key-match? msg "shift+tab")
        (charm/key-match? msg :up))
    [(move-focus state -1) nil]

    ;; submit form
    (charm/key-match? msg "enter")
    [(submit state) nil]

    ;; delegate to focused input
    :else
    (let [idx   (:focused state)
          field (nth (:input-fields state) idx)
          input (nth (:inputs state) idx)]
      (case (:type field)
        :boolean
        [(-> state
             (update-in [:inputs idx :value] not)
             (assoc :errors nil))
         nil]

        :enum
        (if (or (charm/key-match? msg :left)
                (charm/key-match? msg :right))
          ;; cycle through enum options
          (let [str-opts (mapv option->str (:options field))
                current  (charm/text-input-value input)
                cur-idx  (or (first (keep-indexed #(when (= %2 current) %1) str-opts)) 0)
                delta    (if (charm/key-match? msg :right) 1 -1)
                new-idx  (mod (+ cur-idx delta) (count str-opts))]
            [(-> state
                 (assoc-in [:inputs idx]
                           (charm/text-input-set-value input (nth str-opts new-idx)))
                 (assoc :errors nil))
             nil])
          ;; regular typing
          (let [[new-input cmd] (charm/text-input-update input msg)]
            [(-> state
                 (assoc-in [:inputs idx] new-input)
                 (assoc :errors nil))
             cmd]))

        ;; default: text input
        (let [[new-input cmd] (charm/text-input-update input msg)]
          [(-> state
               (assoc-in [:inputs idx] new-input)
               (assoc :errors nil))
           cmd])))))

(defn render-bool [input]
  (let [v     (:value input)
        label (if v "true" "false")
        style (if v styles/bool-on-style styles/bool-off-style)]
    (if (:focused input)
      (charm/render (charm/style :reverse true) label)
      (charm/render style label))))

(defn view [state]
  (let [{:keys [items input-fields inputs focused errors submitted result]} state
        max-w (+ 2 (apply max (map #(count (name (:key %))) input-fields)))]
    (str
     (charm/render styles/title-style "Doorbell") "\n\n"

     ;; field rows — walk items, tracking input index for non-group entries
     (str/join
      "\n"
      (first
       (reduce
        (fn [[out input-idx] {:keys [key depth type]}]
          (if (= type :group)
            ;; group header line
            (let [indent (apply str (repeat (* 2 depth) " "))]
              [(conj out (str indent "  "
                              (charm/render styles/label-style (str (name key) ":"))))
               input-idx])
            ;; input field line
            (let [input    (nth inputs input-idx)
                  focused? (= input-idx focused)
                  indent   (apply str (repeat (* 2 depth) " "))
                  arrow    (if focused? "▸ " "  ")
                  label    (charm/render styles/label-style
                                         (format (str "%-" max-w "s") (name key)))
                  value    (case type
                             :boolean (render-bool input)
                             :enum    (if focused?
                                        (str "◂ " (charm/text-input-view input) " ▸")
                                        (charm/text-input-view input))
                             (charm/text-input-view input))]
              [(conj out (str indent arrow label value))
               (inc input-idx)])))
        [[] 0]
        items)))
     "\n\n"

     ;; errors
     (when (seq errors)
       (str (str/join "\n"
                      (map #(charm/render styles/error-style (str "✗ " %)) errors))
            "\n\n"))

     ;; success
     (when submitted
       (str (charm/render styles/success-style "✓ Valid!") "\n"
            (charm/render styles/result-style (pr-str result)) "\n\n"))

     ;; hints
     (if submitted
       (charm/render styles/hint-style "press any key to continue")
       (charm/render styles/hint-style
                     "↑/↓ navigate  enter submit  ctrl+c quit")))))

(defn tui->result
  [schema data]
  (let [{:keys [result]} (charm/run {:init       (make-init schema data)
                                     :update     update-fn
                                     :view       view
                                     :alt-screen true})]
    (clear)
    result))
