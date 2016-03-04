(ns advenjure.verbs
  (:require [clojure.string :as str]
            [clojure.set :as clset]
            [advenjure.items :refer [iname describe-container get-from remove-from replace-from]]
            [clojure.test :refer [function?]]
            [advenjure.rooms :as rooms]))


;;; UTILITY functions

(defn current-room
  "Get the current room spec from game state."
  [game-state]
  (get-in game-state [:room-map (:current-room game-state)]))

(def direction-mappings {"north" :north, "n" :north
                         "northeast" :northeast, "ne" :northeast
                         "east" :east, "e" :east
                         "southeast" :southeast, "se" :southeast
                         "south" :south, "s" :south
                         "southwest" :southwest, "sw" :southwest
                         "west" :west, "w" :west
                         "northwest" :northwest, "nw" :northwest})

(defn find-item
    "Try to find the given item name either in the inventory or the current room."
    [game-state token]
    (or (get-from (:inventory game-state) token)
        (get-from (:items (current-room game-state)) token)))


(defn remove-item
  [game-state item]
  (let [room-kw (:current-room game-state)
        room (current-room game-state)
        inventory (:inventory game-state)]
    (-> game-state
        (assoc :inventory (remove-from inventory item))
        (assoc-in [:room-map room-kw :items]
                  (remove-from (:items room) item)))))

(defn replace-item
  [game-state old-item new-item]
  (let [room-kw (:current-room game-state)
        room (current-room game-state)
        inventory (:inventory game-state)]
    (-> game-state
        (assoc :inventory (replace-from inventory old-item new-item))
        (assoc-in [:room-map room-kw :items]
                  (replace-from (:items room) old-item new-item)))))


(defn say
  ([speech] (println (str/capitalize speech))))

; FIXME remove this, replaced by function below
(defn item-or-message
  [game-state item]
  (if-let [item-spec (find-item game-state item)]
    item-spec
    (say "I don't see that.")))

;;; VERB HANDLER FUNCTIONS
;;; Every handler takes the game state and the command tokens.
;;; If game state changes returns the new state, otherwise nil

(defn noop [& args])

; TODO maybe a similar one for compund handlers?
(defn make-item-handler
  "TODO EXPLAIN"
  ; this one uses a noop handler, solely based on post/preconditions (i.e. read)
  ([verb-name verb-kw] (make-item-handler verb-name verb-kw noop))
  ([verb-name verb-kw handler &
     {:keys [kw-required] :or {kw-required true}}]
   (fn
     ([game-state] (say (str verb-name " what?")))
     ([game-state item-name]
      (let [item (find-item game-state item-name)
            verb-spec (verb-kw item)
            ; if function -> execute to get precond result:
            value (if (function? verb-spec) (verb-spec game-state) verb-spec)]
        (cond
          (nil? item) (say "I don't see that.")
          (string? value) (say value)
          (and kw-required (nil? value)) (say (str "I can't " verb-name " that."))
          :else (handler game-state item)))))))


(defn go
  "Change the location if direction is valid"
  ([game-state] (say "Go where?"))
  ([game-state direction]
   (defn change-rooms
     "Change room, say description, set visited."
     [new-room]
     (let [room-spec (get-in game-state [:room-map new-room])]
       (say (rooms/describe room-spec))
       (-> game-state
           (assoc :current-room new-room)
           (assoc-in [:room-map new-room :visited] true))))

   (if-let [dir (get direction-mappings direction)]
     (if-let [new-room (dir (current-room game-state))]
       (change-rooms new-room)
       (say "Can't go in that direction"))
     (say "Go where?"))))

(defn look
  "Look around (describe room). If tokens is defined, show error phrase."
  [game-state]
  (say (rooms/describe (current-room game-state))))

(def look-at
  (make-item-handler
    "look at" :look-at
    (fn [game-state item] (say (:description item)))
    :kw-required false))

(def look-inside
  (make-item-handler
    "look inside" :look-in
    (fn [game-state item]
      (if (:items item)
       (say (describe-container item))
       (say (str "I can't look inside a " (iname item) "."))))
    :kw-required false))

(def take_
  (make-item-handler
    "take" :take
    (fn [game-state item]
      "Try to take an item from the current room or from a container object in the inventory.
      Won't allow taking an object already in the inventory (i.e. not in a container)."
      (if (contains? (:inventory game-state) item)
        (say "I already got that.")
        (let [new-state (remove-item game-state item)
              new-inventory (conj (:inventory new-state) item)]
          (say "Taken.")
          (assoc new-state :inventory new-inventory))))))

(def open
  (make-item-handler
    "open" :closed
    (fn [game-state item]
      (cond
        (not (:closed item)) (say "It's already open.")
        (:locked item) (say "It's locked.")
        :else (let [open-item (assoc item :closed false)]
                (say "Opened.")
                (replace-item game-state item open-item))))))

(def close
  (make-item-handler
    "close" :closed
    (fn [game-state item]
      (if (:closed item)
        (say "It's already closed.")
        (let [closed-item (assoc item :closed true)]
             (say "Closed.")
             (replace-item game-state item closed-item))))))


(defn unlock
  ([game-state] (say "Unlock what?"))
  ([game-state locked] (say (str "Unlock " locked " with what?")))
  ([game-state locked-name key-name]
   (if-let [locked (item-or-message game-state locked-name)]
     (if-let [key-item (item-or-message game-state key-name)]
       (cond
         (not (:locked locked)) (say "It's not locked.")
         (not (= locked (:unlocks key-item))) (say "That doesn't work.")
         :else (let [unlocked (assoc locked :locked false)]
                 (say "Unlocked.")
                 (-> game-state
                     (remove-item key-item)
                     (replace-item locked unlocked))))))))


;;; BUILD VERB MAP
(defn add-verb
  "Adds the given function as the handler for every verb in the list."
  [verb-map verbs handler]
  (merge verb-map (zipmap verbs (repeat handler))))

(defn add-go-shortcuts
  "Allow commands like 'north' and 'n' instead of 'go north'"
  [vmap]
  (loop [new-map vmap
         [dir & remain] (keys direction-mappings)]
    (if (nil? dir)
      new-map
      (let [regexp (str "^" dir "$")]
        (recur (add-verb new-map [regexp] #(go % dir)) remain)))))

(def verb-map (-> {}
                  add-go-shortcuts
                  (add-verb ["^go (.*)" "^go$"] go)
                  (add-verb ["^look$" "^look around$"] look)
                  (add-verb ["^look at (.*)" "^look at$" "^describe (.*)" "^describe$"] look-at)
                  (add-verb ["^take (.*)" "^take$" "^get (.*)" "^get$"
                             "^pick (.*)" "^pick$" "^pick up (.*)" "^pick (.*) up$" "^pick up$"] take_)
                  (add-verb ["^inventory$" "^i$"] identity)
                  (add-verb ["^read (.*)" "^read$"] identity)
                  (add-verb ["^open (.*)" "^open$"] open)
                  (add-verb ["^close (.*)" "^close$"] close)
                  (add-verb ["^turn on (.*)" "^turn on$" "^turn (.*) on"] identity)
                  (add-verb ["^turn off (.*)" "^turn off$" "^turn (.*) off"] identity)
                  (add-verb ["^put (.*) in (.*)" "^put$" "^put (.*)$" "^put (.*) in$"
                             "^put (.*) inside (.*)" "^put (.*) inside$"] identity)
                  (add-verb ["^unlock (.*) with (.*)" "^unlock (.*)"
                             "^unlock (.*) with$" "^unlock$"] unlock) ;FIXME open X with Y should work too
                  (add-verb ["^save$"] identity)
                  (add-verb ["^restore$" "^load$"] identity)
                  (add-verb ["^help$"] identity)))

;keep a sorted version to extract the longest possible form first
(def sorted-verbs (reverse (sort-by count (keys verb-map))))

(defn find-verb
  "Return [verb remaining] if there's a proper verb at the beginning of text."
  [text]
  (defn match-verb [verb]
    (let [[head & tokens :as full] (re-find (re-pattern verb) text)]
      (cond
        (and (not (nil? full)) (not (coll? full))) [verb '()] ;single match, no params
        (not-empty head) [verb tokens]))) ; match with params

  (some match-verb sorted-verbs))

(defn process-input
  "Take an input comand, find the verb in it and execute its action handler."
  [game-state input]
  (let [clean (str/trim (str/lower-case input))
        [verb tokens] (find-verb clean)
        handler (get verb-map verb)]
   (if handler
     (or (apply handler game-state tokens) game-state)
     (do (say "I don't know how to do that.") game-state))))
