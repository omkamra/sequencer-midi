# Change Log

## [0.4.0] - 2022-01-06

### Added

- MidiDevice protocol methods: `aftertouch`, `channel-pressure`
- new pattern expressions: `:cc`, `:bank`, `:mod-wheel`, `:volume`,
  `:balance`, `:pan`, `:channel-pressure`, `:pitch-bend`
- new string syntax: `b5` -> `[:bank 5]`

### Removed

- the following MidiDevice protocol methods (these are now implemented
  via `:cc`): `bank-select`, `all-notes-off`, `all-sounds-off`

### Changed

- names of the following MidiDevice protocol methods: `cc` ->
  `control-change`, `pitch-bend` -> `pitch-wheel`
- bumped `omkamra.sequencer` dependency to `0.4.0`

## [0.3.0] - 2021-12-24

### Changed

- bumped `omkamra.sequencer` dependency to `0.3.0`

## [0.2.0] - 2021-12-22

### Changed

- bumped `omkamra.sequencer` dependency to `0.2.0`

## [0.1.0] - 2021-12-20

Initial release.

[Unreleased]: https://github.com/omkamra/sequencer-midi/compare/0.3.0...HEAD
[0.3.0]: https://github.com/omkamra/sequencer-midi/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/omkamra/sequencer-midi/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/omkamra/sequencer-midi/tree/0.1.0
