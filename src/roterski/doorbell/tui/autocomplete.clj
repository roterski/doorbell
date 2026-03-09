(ns roterski.doorbell.tui.autocomplete
  (:require [roterski.doorbell.tui.utils :refer [clear]]
            [charm.core :as charm]
            [malli.core :as ma]
            [malli.transform :as mt]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Schemas
;; ---------------------------------------------------------------------------

(def Options
  [:map
   [:prompt {:default "Search: "} :string]
   [:placeholder {:default "type to search..."} :string]
   [:height {:default 10} [:int {:min 1}]]
   [:no-results-text {:default "No results"} :string]])

(def coerce-options
  (ma/coercer Options (mt/transformer mt/default-value-transformer)))

;; ---------------------------------------------------------------------------
;; Styles
;; ---------------------------------------------------------------------------

(def prompt-style  (charm/style :fg charm/cyan :bold true))
(def selected-style (charm/style :fg charm/cyan :bold true))
(def normal-style  (charm/style :fg charm/white))
(def hint-style    (charm/style :fg 240))
(def no-results-style (charm/style :fg 240 :italic true))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- item-title [item]
  (cond
    (string? item) item
    (map? item)    (or (:title item) (:name item) (str item))
    :else          (str item)))

(defn- render-item [item selected?]
  (let [title (item-title item)]
    (if selected?
      (charm/render selected-style (str "▸ " title))
      (str "  " (charm/render normal-style title)))))

;; ---------------------------------------------------------------------------
;; TEA: helpers
;; ---------------------------------------------------------------------------

(defn- clamp [n lo hi]
  (max lo (min n hi)))

(defn- trigger-search
  "Returns [new-state cmd]. Dispatches an async search command;
   results arrive as a :search-results msg."
  [state]
  (let [query (charm/text-input-value (:input state))
        [spinner spinner-cmd] (charm/spinner-init (:spinner state))
        search-cmd (let [search-fn (:search-fn state)
                         q query]
                     (charm/cmd (fn []
                                  {:type :search-results
                                   :query q
                                   :items (search-fn q)})))]
    [(assoc state :query query :loading? true :spinner spinner :cursor 0)
     (charm/batch search-cmd spinner-cmd)]))

;; ---------------------------------------------------------------------------
;; TEA: init
;; ---------------------------------------------------------------------------

(defn- make-init [search-fn opts]
  (let [{:keys [prompt placeholder]} opts]
    (fn []
      (let [state {:input      (charm/text-input-focus
                                (charm/text-input :prompt (charm/render prompt-style prompt)
                                                  :placeholder placeholder
                                                  :focused true))
                   :search-fn  search-fn
                   :query      ""
                   :items      []
                   :loading?   false
                   :spinner    (charm/spinner :dots)
                   :cursor     0
                   :mode       :typing   ; :typing | :selecting
                   :result     nil
                   :opts       opts}]
        ;; trigger initial search so blank-query results show immediately
        (trigger-search state)))))

;; ---------------------------------------------------------------------------
;; TEA: update
;; ---------------------------------------------------------------------------

(defn- update-fn [state msg]
  (cond
    ;; quit
    (or (charm/key-match? msg "ctrl+c")
        (charm/key-match? msg "esc"))
    [(assoc state :result nil) charm/quit-cmd]

    ;; already submitted
    (:result state)
    [state charm/quit-cmd]

    ;; async search results arrived
    (= :search-results (:type msg))
    (if (= (:query msg) (:query state)) ;; ignore stale results
      [(assoc state :items (vec (or (:items msg) [])) :message nil) nil]
      [state nil])

    ;; enter — submit selected item
    (charm/key-match? msg "enter")
    (if (and (= :selecting (:mode state))
             (seq (:items state)))
      (let [item (nth (:items state) (:cursor state))]
        [(assoc state :result item) charm/quit-cmd])
      [state nil])

    ;; tab or down-arrow — switch to selecting mode (or move cursor)
    (or (charm/key-match? msg "tab")
        (charm/key-match? msg :down))
    (if (seq (:items state))
      (if (= :typing (:mode state))
        [(assoc state :mode :selecting :cursor 0) nil]
        [(update state :cursor
                 #(clamp (inc %) 0 (dec (count (:items state)))))
         nil])
      [state nil])

    ;; up-arrow — move cursor up or switch back to typing
    (or (charm/key-match? msg "shift+tab")
        (charm/key-match? msg :up))
    (if (= :selecting (:mode state))
      (if (zero? (:cursor state))
        [(assoc state :mode :typing) nil]
        [(update state :cursor dec) nil])
      [state nil])

    ;; in selecting mode, ignore regular key presses
    (= :selecting (:mode state))
    (if (charm/key-press? msg)
      ;; switch back to typing mode and forward the key
      (let [[new-input _] (charm/text-input-update (:input state) msg)
            [new-state search-cmd] (trigger-search (assoc state :input new-input :mode :typing))]
        [new-state search-cmd])
      [state nil])

    ;; typing mode — delegate to text-input
    :else
    (let [[new-input _] (charm/text-input-update (:input state) msg)
          [new-state search-cmd] (trigger-search (assoc state :input new-input))]
      [new-state search-cmd])))

;; ---------------------------------------------------------------------------
;; TEA: view
;; ---------------------------------------------------------------------------

(defn- view [state]
  (let [{:keys [input items message cursor mode opts]} state
        {:keys [height no-results-text]} opts
        query (charm/text-input-value input)
        total (count items)
        ;; scroll window so cursor is always visible
        offset (if (< cursor height)
                 0
                 (- cursor (dec height)))
        visible-items (->> items (drop offset) (take height))
        remaining (- total (+ offset (count visible-items)))
        has-query? (not (str/blank? query))]
    (str
     (charm/text-input-view input) "\n"
     (when message (str (charm/render normal-style message) "\n"))
     (cond
       (and has-query? (empty? items))
       (str (charm/render no-results-style no-results-text) "\n")

       (seq visible-items)
       (str (when (pos? offset)
              (str (charm/render hint-style
                                 (str "  +" offset " above")) "\n"))
            (str/join "\n"
                      (map-indexed
                       (fn [idx item]
                         (render-item item (and (= :selecting mode)
                                                (= (+ offset idx) cursor))))
                       visible-items))
            (when (pos? remaining)
              (str "\n" (charm/render hint-style
                                      (str "  +" remaining " more"))))
            "\n")

       :else "")
     (charm/render hint-style
                   (if (= :selecting mode)
                     "↑/↓ navigate  enter select  esc quit"
                     "type to search  ↓/tab select  esc quit")))))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(defn autocomplete
  "Run an interactive autocomplete TUI.
  
     `search-fn` — (fn [query] items), called asynchronously on each keystroke.
     `opts`      — optional map validated against `Options` schema.
  
     Returns the selected item, or nil if the user cancelled."
  ([search-fn] (autocomplete search-fn {}))
  ([search-fn opts]
   (let [opts   (coerce-options (merge {} opts))
         result (charm/run {:init       (make-init search-fn opts)
                            :update     update-fn
                            :view       view
                            :alt-screen true})]
     (clear)
     (:result result))))
