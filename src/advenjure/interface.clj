(ns advenjure.interface
  (:require [advenjure.items :refer [all-item-names]])
  (:import [jline.console ConsoleReader]
           [jline.console.completer StringsCompleter ArgumentCompleter NullCompleter AggregateCompleter]))

;; TODO separate jline wrapper methods from logic that takes the state and updates the interface
;; should have one init and one refresh, plus input methods probably

(def console (ConsoleReader.))

(defn init []
  (.clearScreen console))

(defn read-key []
  (str (char (.readCharacter console))))

(defn read-value
  "read a single key and eval its value. Return nil if no value entered."
  []
  (let [input (read-key)]
    (try
      (read-string input)
      (catch RuntimeException e nil))))

(def print-line println)

(defn verb-to-completer
  "Take a verb regexp and an available items completer, and return an
  ArgumentCompleter that respects the regexp."
  [verb items-completer]
  (let [verb (clojure.string/replace (subs verb 1) #"\$" "")
        tokens (clojure.string/split verb #" ")
        mapper (fn [token] (if (= token "(.*)") items-completer
                             (StringsCompleter. [token])))]
    (ArgumentCompleter. (concat (map mapper tokens) [(NullCompleter.)]))))

(defn update-completer
  [verbs items]
  (let [current (first (.getCompleters console))
        items (StringsCompleter. items)
        arguments (map #(verb-to-completer % items) verbs)
        aggregate (AggregateCompleter. arguments)]
    (.removeCompleter console current)
    (.addCompleter console aggregate)))

(defn clean-verb [verb]
  (let [is-alpha #(or (Character/isLetter %)
                      (Character/isWhitespace %))]
    (clojure.string/trim (apply str (filter is-alpha verb)))))

(defn get-input
  ([game-state verb-map]
   (let [verbs (keys verb-map)
         room (get-in game-state [:room-map (:current-room game-state)]) ;; FIXME duplicated from utils
         all-items (into (:inventory game-state) (:items room))
         item-names (all-item-names all-items)]
     (update-completer verbs item-names)
     (.readLine console ">")))
  ([]
   (.readLine console ">")))

