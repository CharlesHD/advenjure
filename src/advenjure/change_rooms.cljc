(ns advenjure.change-rooms
  (:require [advenjure.rooms :as rooms]
            [advenjure.hooks :as hooks]
            [advenjure.utils :refer [say]]))

(defn change-rooms
  "Change room, say description, set visited."
  [game-state new-room]
  (let [room-spec (get-in game-state [:room-map new-room])
        previous (:current-room game-state)
        new-state (-> game-state
                    (assoc :previous-room previous)
                    (assoc :current-room new-room)
                    (hooks/execute :before-change-room)
                    (assoc-in [:room-map new-room :visited] true))]
    (say (rooms/describe room-spec))
    (hooks/execute new-state :after-change-room)))

