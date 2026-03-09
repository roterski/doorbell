(ns roterski.doorbell.tui.utils)

(defn clear []
  (print "\033[2J\033[H")
  (flush))
