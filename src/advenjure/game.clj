(ns advenjure.game
  (:require [advenjure.rooms :as room]
            [advenjure.change-rooms :refer [change-rooms]]
            [advenjure.verb-map :refer [find-verb default-map]]
            [advenjure.ui.input :refer [get-input]]
            [advenjure.ui.output :refer [print-line init]]
            [gettext.core :refer [_]]))

(defn make
  "Make a new game state based on a room map and an optional initial inventory set."
  ([room-map start-room] (make room-map start-room #{}))
  ([room-map start-room inventory]
   {:room-map room-map
    :current-room start-room
    :inventory inventory
    :events #{}
    :executed-dialogs #{}
    :points 0
    :moves 0}))

(defn process-input
  "Take an input comand, find the verb in it and execute its action handler."
  [verb-map game-state input]
  (let [clean (clojure.string/trim (clojure.string/lower-case input))
        [verb tokens] (find-verb verb-map clean)
        handler (get verb-map verb)]
    (if handler
      (let [new-state (update-in game-state [:moves] inc)]
        (or (apply handler new-state tokens) new-state))
      (do (print-line (_ "I didn't know how to do that.")) game-state))))

(defn run
  "Run the game loop. Requires a finished? function to decide when to terminate the loop."
  ([game-state finished?] (run game-state finished? ""))
  ([game-state finished? initial-msg]
   (run game-state finished? initial-msg default-map))
  ([game-state finished? initial-msg verb-map]
   (init)
   (print-line initial-msg)
   (loop [state (change-rooms game-state (:current-room game-state))]
     (let [input (get-input state verb-map)
           new-state (process-input verb-map state input)]
       (if-not (finished? new-state)
         (recur new-state))))
   (print-line (_ "\nThe End."))))
