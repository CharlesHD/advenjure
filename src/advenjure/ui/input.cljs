(ns advenjure.ui.input
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.reader :refer [read-string]]
            [clojure.string :as string]
            [advenjure.utils :refer [direction-mappings current-room]]
            [advenjure.items :refer [all-item-names]]
            [cljs.core.async :refer [<! >! chan]]))

(def term #(.terminal (.$ js/window "#terminal")))
(def input-chan (chan))

(def exit #(.pause (term)))

(defn read-value []
  (let [key-chan (chan)]
    (go
      (.pause (term))
      (aset js/window "onkeydown"
        (fn [ev]
          (do
            (aset js/window "onkeydown" nil)
            (go (>! key-chan (aget ev "key")))
            (.resume (term)))))
      (read-string (<! key-chan)))))

(def read-key read-value)

(defn load-file [file]
  (read-string (aget js/localStorage file)))


(defn process-command
  "Write command to the input channel"
  [command term]
  (go (>! input-chan command)))


(defn get-suggested-token
  "
  Compare the verb tokens with the complete input tokens and if they match,
  return the next verb token to be suggested. If no match returns nil.
  "
  [verb-tokens input-tokens]
  (loop [[verb & next-verbs] verb-tokens
         [input & next-inputs] input-tokens]
    (cond
      (nil? input) (str verb " ") ; all input matched, suggest current verb token
      (nil? verb) nil
      (= (string/trim input) (string/trim verb)) (recur next-verbs next-inputs)
      ;FIXME this doesnt work for multiword items
      (string/starts-with? verb "(?<") (recur next-verbs next-inputs))))

(defn expand-suggestion
  [token items dirs]
  (cond
    (#{"(?<item>.*) " "(?<item1>.*) " "(?<item2>.*) "} token) (map #(str % " ") items)
    (= token "(?<dir>.*) ") (map #(str % " ") dirs)
    :else [token]))

(defn tokenize-verb
  [verb]
  (-> verb
      (string/replace #"\$" "")
      (string/replace #"\^" "")
      (string/split #" "))) ;; considers weird jquery &nbsp

(defn tokenize-input
  "
  Get the finished tokens (partial tokens are ingnored, since that part of the
  completion is handled by jquery terminal).
  Encodes/decodes item and dir names to avoid breaking them in separate tokens.
  "
  [input items dirs]
  (let [encode #(string/replace %1 (re-pattern %2) (string/replace %2 #" " "%%W%%"))
        input (string/replace input #"[\s|\u00A0]" " ")
        input (reduce encode input (concat items dirs))
        tokens (string/split input #" ")
        tokens (map #(string/replace %1 #"%%W%%" " ") tokens)]
    (if (= (last input) \space)
      tokens
      (butlast tokens))))

(defn get-full-input []
  (.text (.next (.$ js/window ".prompt"))))

(defn get-completion
  [game-state verb-map]
  (let [verb-tokens (map tokenize-verb (keys verb-map))
        room (current-room game-state)
        items (all-item-names (into (:inventory game-state) (:items room)))
        dirs (keys direction-mappings)]
    (fn [term input cb]
      (let [input (get-full-input)
            input-tokens (tokenize-input input items dirs)
            suggested1 (distinct (map #(get-suggested-token % input-tokens) verb-tokens))
            suggested (remove string/blank? (mapcat #(expand-suggestion % items dirs) suggested1))]
        (cb (apply array suggested))))))

(defn set-interpreter
  [gs verb-map]
  (let [room (:name (current-room gs))
        moves (:moves gs)
        prompt (str "\n@" room " [" moves "] > ")]
    (if (> (.level (term)) 1) (.pop (term))) ; if there's a previous interpreter, pop it
    (.push (term)
           process-command
           (js-obj "prompt" prompt
                   "completion" (get-completion gs verb-map)))))

(defn get-input
  "Wait for input to be written in the input channel"
  [state verb-map]
  (go
    (set-interpreter state verb-map)
    (.echo (term) " ")
    (<! input-chan)))
