(ns omkamra.sequencer.targets.midi
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [omkamra.sequencer :as sequencer :refer [pfn beats->ticks]]
   [omkamra.sequencer.protocols.MidiDevice :as MidiDevice]
   [instaparse.core :as insta]))

(def scale-steps
  {:chroma [1 1 1 1 1 1 1 1 1 1 1]
   :major [2 2 1 2 2 2]
   :harmonic-major [2 2 1 2 1 3]
   :double-harmonic-major [1 3 1 2 1 3]
   :minor [2 1 2 2 1 2]
   :harmonic-minor [2 1 2 2 1 3]
   :melodic-minor [2 1 2 2 2 2]})

(defn steps->offsets
  ([steps offsets last-offset]
   (if (empty? steps)
     offsets
     (let [next-offset (+ last-offset (first steps))]
       (recur (rest steps)
              (conj offsets next-offset)
              next-offset))))
  ([steps]
   (steps->offsets steps [0] 0)))

(def scales
  (into {} (for [[name steps] scale-steps]
             [name (steps->offsets steps)])))

(def note-offsets
  {\c 0
   \d 2
   \e 4
   \f 5
   \g 7
   \a 9
   \b 11})

;; NAO = Note And Octave
(def re-nao #"([cdefgabCDEFGAB])([-#])([0-9])$")

(defn nao?
  [x]
  (or (and (keyword? x)
           (re-matches re-nao (name x)))
      false))

(defn nao->midi-note
  [nao]
  (let [[_ note sep oct] (->> (name nao)
                                 str/lower-case
                                 (re-matches re-nao)
                                 (map first))]
    (+ (* 12 (- (int oct) 0x30))
       (note-offsets note)
       (if (= \# sep) 1 0))))

(defn midi-note?
  [x]
  (and (integer? x) (<= 0 x 127)))

(defn resolve-midi-note
  [x]
  (cond (midi-note? x) x
        (nao? x) (nao->midi-note x)
        :else (throw (ex-info "invalid note" {:value x}))))

(defn resolve-scale
  [x]
  (cond (vector? x) x
        (keyword? x) (or (scales x)
                         (throw (ex-info "unknown scale" {:name x})))
        :else (throw (ex-info "invalid scale" {:value x}))))

(defn resolve-binding
  [k v]
  (case k
    :root (resolve-midi-note v)
    :scale (resolve-scale v)
    nil))

(defmulti compile-pattern-expr first)

(defmethod compile-pattern-expr :default [_] nil)

(def parse-string (insta/parser (jio/resource "omkamra/sequencer/targets/midi.bnf")))

(defn parse
  ([s start]
   (parse-string s :start start))
  ([s]
   (parse s :expr)))

(defn group?
  [x]
  (and (vector x)
       (#{:seq :mix1} (first x))))

(defn simplify
  [x]
  (if (and (group? x)
           (= (count x) 2))
    (second x)
    x))

(def binding-modifiers
  #{:channel
    :dur
    :step
    :oct
    :semi
    :vel
    :scale
    :mode
    :root})

(defn binding-modifier?
  [x]
  (and (vector x)
       (binding-modifiers (first x))))

(defn extract-bindings-from-stem
  ([stem bindings]
   (if (group? stem)
     (loop [items (next stem)
            new-stem [(first stem)]
            new-bindings bindings]
       (if (seq items)
         (let [head (first items)]
           (if (binding-modifier? head)
             (recur (next items)
                    new-stem
                    (conj new-bindings head))
             (recur (next items)
                    (conj new-stem head)
                    new-bindings)))
         [(simplify new-stem) new-bindings]))
     [stem bindings]))
  ([stem]
   (extract-bindings-from-stem stem (sorted-map))))

(defn wrap-in-seq-if-binding-modifier
  [x]
  (if (binding-modifier? x)
    [:seq x]
    x))

(defn postprocess
  [[tag & rest]]
  (case tag
    :expr (let [[stem & mods] rest
                stem (-> stem postprocess wrap-in-seq-if-binding-modifier)
                [stem bindings] (extract-bindings-from-stem stem)
                bindings (into bindings (map postprocess mods))]
            (if (seq bindings)
              [:bind bindings stem]
              stem))
    (:seq :mix1) (simplify (apply vector tag (map postprocess rest)))
    (:uint :int) (Integer/parseInt (first rest))
    (:uratio :ratio) (let [[num denom] rest]
                       (/ (if (empty? num) 1 (Integer/parseInt num))
                          (Integer/parseInt denom)))
    :bank [:bank (postprocess (first rest))]
    :program [:program (postprocess (first rest))]
    :clear [:clear]
    :midi-note [:note (postprocess (first rest))]
    :scale-degree [:degree (postprocess (first rest))]
    :nao [:note (nao->midi-note (first rest))]
    :rest (let [[length] rest]
            [:wait (if length (postprocess length) 1)])
    :align [:wait (- (postprocess (first rest)))]
    :channel [:channel (postprocess (first rest))]
    :dur (let [[beats] rest]
           [:dur (if beats (postprocess beats) nil)])
    :step [:step [:mul (postprocess (first rest))]]
    :oct (let [[op amount] rest
               cmd (if (= op "^") :add :sub)
               amount (if amount (postprocess amount) 1)]
           [:oct [cmd amount]])
    :semi (let [[op amount] rest
                cmd (if (= op "#") :add :sub)
                amount (if amount (postprocess amount) 1)]
            [:semi [cmd amount]])
    :vel (if (string? (first rest))
           (let [[op amount] rest
                 cmd (case (first op)
                       \+ :add
                       \- :sub
                       \* :mul
                       \/ :div)
                 amount (postprocess amount)]
             [:vel [cmd amount]])
           [:vel (postprocess (first rest))])
    :scale [:scale (keyword (first rest))]
    :mode (let [[op amount] rest
                cmd (if (= op ">") :add :sub)
                amount (if amount (postprocess amount) 1)]
            [:mode [cmd amount]])
    :root (let [note (postprocess (first rest))]
            (case (first note)
              :note [:root (second note)]
              :degree [:root [:degree->key (second note)]]))))

(defn compile-string
  [s]
  (postprocess (parse (str "(" s ")"))))

(defn compile-pattern-form
  [form]
  (when (string? form)
    (compile-string form)))

(defn advance
  [pattern beats tpb]
  (update pattern :offset + (beats->ticks beats tpb)))

(defn compile-note
  [tag note-descriptor descriptor->key]
  (cond
    (vector? note-descriptor)
    (apply vector :seq (map #(vector tag %) note-descriptor))

    (set? note-descriptor)
    [:seq
     (apply vector :mix (map #(vector tag %) note-descriptor))
     [:wait 1]]

    (keyword? note-descriptor)
    [:note (resolve-midi-note note-descriptor)]

    (integer? note-descriptor)
    (pfn [pattern {:keys [target channel vel dur step sequencer] :as bindings}]
      (assert target "target is unbound")
      (let [key (descriptor->key bindings note-descriptor)
            {:keys [tpb]} sequencer]
        (-> pattern
            (sequencer/add-callback
             #(MidiDevice/note-on target channel key vel))
            (sequencer/add-callback-after
             ;; we decrease dur by one tick to ensure that a
             ;; successive note at the same pitch isn't cut
             (and dur (pos? dur) (dec (beats->ticks dur tpb)))
             #(MidiDevice/note-off target channel key))
            (advance step tpb))))

    :else (throw (ex-info "invalid note descriptor"
                          {:note-descriptor note-descriptor}))))

(defn note->key
  [bindings key]
  key)

(defmethod compile-pattern-expr :note
  [[_ key]]
  (compile-note :note key note->key))

(defn degree->key
  [{:keys [root scale mode oct semi] :as bindings} degree]
  (let [index (+ degree mode)
        scale-size (count scale)]
    (+ root
       (* 12 oct)
       (* 12 (if (neg? index)
               (- (inc (quot (dec (- index)) scale-size)))
               (quot index scale-size)))
       (scale (mod index scale-size))
       semi)))

(defmethod compile-pattern-expr :degree
  [[_ degree]]
  (compile-note :degree degree degree->key))

(defn compile-control-change
  [ctrl value]
  (pfn [pattern {:keys [target channel] :as bindings}]
    (assert target "target is unbound")
    (sequencer/add-callback pattern #(MidiDevice/control-change target channel ctrl value))))

(defmethod compile-pattern-expr :control-change
  [[_ ctrl value]]
  (compile-control-change ctrl value))

(defmethod compile-pattern-expr :cc
  [[_ ctrl value]]
  (compile-control-change ctrl value))

(defmethod compile-pattern-expr :bank-select
  [[_ value]]
  [:cc 0 value])

(defmethod compile-pattern-expr :bank
  [[_ value]]
  [:cc 0 value])

(defmethod compile-pattern-expr :mod-wheel
  [[_ value]]
  [:cc 1 value])

(defmethod compile-pattern-expr :volume
  [[_ value]]
  [:cc 7 value])

(defmethod compile-pattern-expr :balance
  [[_ value]]
  [:cc 8 value])

(defmethod compile-pattern-expr :pan
  [[_ value]]
  [:cc 10 value])

(defmethod compile-pattern-expr :all-sounds-off
  [[_]]
  [:cc 120 0])

(defmethod compile-pattern-expr :all-notes-off
  [[_]]
  [:cc 123 0])

(defn compile-program-change
  [program]
  (pfn [pattern {:keys [target channel] :as bindings}]
    (assert target "target is unbound")
    (-> pattern
        (sequencer/add-callback #(MidiDevice/program-change target channel program)))))

(defmethod compile-pattern-expr :program-change
  [[_ program]]
  (compile-program-change program))

(defmethod compile-pattern-expr :program
  [[_ program]]
  (compile-program-change program))

(defmethod compile-pattern-expr :channel-pressure
  [[_ pressure]]
  (pfn [pattern {:keys [target channel] :as bindings}]
    (assert target "target is unbound")
    (-> pattern
        (sequencer/add-callback #(MidiDevice/channel-pressure target channel pressure)))))

(defn compile-pitch-wheel
  [value]
  (pfn [pattern {:keys [target channel] :as bindings}]
    (assert target "target is unbound")
    (sequencer/add-callback pattern #(MidiDevice/pitch-wheel target channel value))))

(defmethod compile-pattern-expr :pitch-wheel
  [[_ value]]
  (compile-pitch-wheel value))

(defmethod compile-pattern-expr :pitch-bend
  [[_ value]]
  (compile-pitch-wheel value))

(defmulti compile-bind-expr
  (fn [k expr]
    (first expr)))

(defmethod compile-bind-expr :default [k expr] nil)

(defmethod compile-bind-expr :degree->key
  [k [_ degree]]
  (let [degree (sequencer/compile-bind-spec k degree)]
    (fn [bindings]
      (let [degree (degree bindings)]
        (degree->key bindings degree)))))

(def default-bindings
  {:channel 0
   :root (nao->midi-note :c-5)
   :scale (scales :chroma)
   :vel 96
   :oct 0
   :mode 0
   :semi 0
   :step 1})
