(ns omkamra.sequencer.protocols.MidiDevice)

(defprotocol protocol
  (note-off [this channel key])
  (note-on [this channel key vel])
  (aftertouch [this channel key pressure])
  (control-change [this channel ctrl value])
  (program-change [this channel program])
  (channel-pressure [this channel pressure])
  (pitch-wheel [this channel value]))
