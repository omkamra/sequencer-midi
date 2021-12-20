# omkamra.sequencer-midi

[![Clojars Project](https://img.shields.io/clojars/v/com.github.omkamra/sequencer-midi.svg)](https://clojars.org/com.github.omkamra/sequencer-midi)

An [omkamra.sequencer](https://github.com/omkamra/sequencer)
[target](https://github.com/omkamra/sequencer#targets) for MIDI
applications.

This target cannot be used on its own: its main purpose is to provide
a set of common helpers for higher level targets which want to
interface with actual MIDI devices.

It provides the following features:

- a dictionary of scales: `:chroma`, `:major`, `:minor`, etc.
- MIDI-specific pattern expressions: `:note`, `:degree`, `:program`,
  `:all-notes-off`, `:all-sounds-off`
- MIDI-specific bind expressions: `:degree->key`
- MIDI-specific binding keys: `:channel`, `:root`, `:scale`, `:vel`,
  `:oct`, `:mode`, `:semi`, `:dur`

These elements make it possible to encode musical phrases as [pattern
expressions](https://github.com/omkamra/sequencer#pattern-expressions):

```clojure
[:bind {:root :f#3
        :scale :major
        :channel 0
        :vel 100
        :dur 2
        :step 1/2}
 [:program 33]
 (for [i (range 7)]
   [:seq
    [:degree i]
    [:bind {:oct [:add 1] :vel [:sub 30]} [:degree i]]])
 [:degree 7]
 [:wait 4]
 [:all-sounds-off]]
```

This pattern would play the seven degrees of the F# major scale
starting in octave 3 on MIDI channel 0 using program 33, followed by
the root note of the scale one octave higher.

Each note has a duration of 2 beats, i.e. the `note-off` MIDI message
is scheduled two beats after the `note-on`. The `:step` value is 1/2,
this means each note will advance the pattern offset by 1/2 beats.

Each of the seven scale degrees is played twice: first without
transposition, then one octave higher and with a velocity decreased by
30.

After the scale and the finishing note the pattern waits 4 steps -
this equals 2 beats as the value of the `:step` binding is 1/2 - and
finally turns all sounds off.

## Music description language

To ease the encoding of musical phrases, this target provides a
compiler for a text-based music description language which can be
embedded into patterns as Clojure strings.

The language provides the following building blocks:

| Example input | Resulting parse expression | Effect |
| ------| -------------------------- | ------ |
| `p4`  | `[:program 4]` | set program of current channel to 4 |
| `m60` | `[:note 60]` | play MIDI note 60 |
| `c-5` | `[:note 60]` | play MIDI note 60 |
| `c#5` | `[:note 61]` | play MIDI note 61 |
| `b-4` | `[:note 59]` | play MIDI note 59 |
| `3`   | `[:degree 3]` | play 3rd degree of current scale |
| `-5`  | `[:degree -5]` | play 2nd degree of current scale one octave lower (assuming a 7 note scale) |
| `,`   | `[:wait 1]` | wait 1 step |
| `,4`  | `[:wait 4]` | wait 4 steps |
| `,1/2` | `[:wait 1/2]` | wait half step |
| `,/3` | `[:wait 1/3]` | wait 1/3 step |
| `%4` | `[:wait -4]` | wait until pattern offset reaches next multiple of 4 steps |

These building blocks can be aggregated into larger units using the following syntax:

| Example input | Resulting parse expression | Effect |
| ------| -------------------------- | ------ |
| `(0 2 4)` | `[:seq [:degree 0] [:degree 2] [:degree 4]]` | play the three degrees in succession |
| `{0 2 4}` | `[:mix1 [:degree 0] [:degree 2] [:degree 4]]` | play the three degrees in parallel, advance the pattern by the length of the first one |

Both simple building blocks and aggregates can be ornamented with
various binding modifiers:

| Example input | Resulting parse expression | Effect |
| ------| -------------------------- | ------ |
| `2c5` | `[:bind {:channel 5} [:degree 2]]` | play degree 2 on MIDI channel 5 |
| `{3 4}~2` | `[:bind {:dur 2} [:mix1 [:degree 3] [:degree 4]]]` | play degrees 3 and 4 simultaneously with duration 2 |
| `1~` | `[:bind {:dur nil} [:degree 1]]` | play degree 1 with no duration (i.e. without a note-off) |
| `0./4` | `[:bind {:step [:mul 1/4]} [:degree 0]` | play degree 0 and advance the pattern offset by 1/4th of the current step |
| `(5^ 5)c5` | `[:bind {:channel 5} [:seq [:bind {:oct [:add 1]} [:degree 5]] [:degree 5]]]` | play degree 5 first one octave higher, then without transposition on channel 5 |
| `(5_3 5)v70` | `[:bind {:vel 70} [:seq [:bind {:oct [:sub 3]} [:degree 5]] [:degree 5]]]` | play degree 5 first three octaves lower, then without transposition with velocity 70 |
| `6v-30` | `[:bind {:vel [:sub 30]} [:degree 6]]` | play degree 6 with velocity decreased by 30 |
| `0#1` | `[:bind {:semi [:add 1]} [:degree 0]]` | play degree 0 raised by one semitone |
| `0b2` | `[:bind {:semi [:sub 1]} [:degree 0]]` | play degree 0 lowered by two semitones |
| `{0 2 4}&(minor)` | `[:bind {:scale :minor} [:mix1 [:degree 0] [:degree 2] [:degree 4]]]` | play a minor triad |
| `{0 2 4}>2` | `[:bind {:mode 2} [:mix1 [:degree 0] [:degree 2] [:degree 4]]]` | play a triad in mode 2 (Phrygian if we are in a major scale) |
| `2@3` | `[:bind {:root [:degree->key 3]} [:degree 2]]]` | play the 2nd degree of the scale starting at the 3rd degree of the current scale |

Aggregate modifiers can be placed either after the aggregate (as shown
in the example above) or interspersed with the aggregate elements:

| Alternative 1 | Alternative 2 | Alternative 3 |
| ------------- | ------------- | ------------- |
| `(0 2 4)c3`   | `(c3 0 2 4)`  | `(0 c3 2 4)` |
| `{0 2 4}.2~3`   | `{0 2 4 .2 ~3}` | `{0 2 .2 4}~3` |

The location of the modifiers does not matter: they are all collected
and put into the map of the `:bind` expression which encloses the
group.

Pattern expressions parsed from a string are always wrapped in a
`:seq` form, so there is no need to use parentheses around the
top-level group.

## License

Copyright © 2021 Balázs Ruzsa

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
