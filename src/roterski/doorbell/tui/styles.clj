(ns roterski.doorbell.tui.styles
  (:require [charm.core :as charm]))

(def prompt-style    (charm/style :fg charm/cyan :bold true))
(def label-style     (charm/style :fg charm/cyan :bold true))
(def selected-style  (charm/style :fg charm/cyan :bold true))
(def normal-style    (charm/style :fg charm/white))
(def hint-style      (charm/style :fg 240))
(def no-results-style (charm/style :fg 240 :italic true))
(def error-style     (charm/style :fg charm/red))
(def success-style   (charm/style :fg charm/green :bold true))
(def bool-on-style   (charm/style :fg charm/green :bold true))
(def bool-off-style  (charm/style :fg charm/red))
(def title-style     (charm/style :fg charm/magenta :bold true))
(def result-style    (charm/style :fg charm/white
                                  :border charm/rounded-border
                                  :padding [0 1]))
