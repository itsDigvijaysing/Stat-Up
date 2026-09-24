# Stat Up classifier database

Two files. Both have columns `task,category` with category in
`STR | INT | WIS | DEX | CHA | VIT`.

| file | rows | purpose |
|---|---|---|
| `train.csv` | 1800 | training set, 300 per stat |
| `test.csv` | 300 | held-out evaluation, 50 per stat, zero overlap with train |

## Rules every row must follow

- **Minimum 3 words.** 1-2 word entries measured at 35-42% accuracy and were removed.
- **ASCII only** - no emoji or icons.
- **No commas** (keeps the CSV trivially parseable).
- **No real personal names** - generic names and honorifics only.
- Voice matches the author's own Todoist: simple everyday words, plain leading verb,
  mixed capitalisation, common abbreviations (`imp`, `WA`, `msg`, `hrs`, `appt`),
  occasional realistic typos, Indian context.
- No duplicate or near-duplicate rows, within or across categories or splits.

## Stat definitions

| stat | meaning |
|---|---|
| STR | physical exertion - gym, running, sport, heavy labour. Pushing the body. |
| INT | learning and problem solving - study, exams, coding, research, technical reading. |
| WIS | staying on top of life - ~60% admin/tracking (bills, renewals, records, refunds), ~40% reflection (journaling, review, planning, meditation). |
| DEX | skill and precision built by repetition - instruments, art, craft, technique drills. |
| CHA | connecting with people - calls, messages, meetings, networking, referrals, presenting. |
| VIT | maintaining the body - sleep, food, water, rest, medication, health appointments. |

## Retrain and evaluate

```bash
python3 scripts/train_stat_classifier.py
python3 scripts/train_stat_classifier.py --export app/src/main/assets/classifier/stat_clf_v1.bin
```

## Known limitations (accepted, to improve later)

- Held-out accuracy **85.0%**; STR is weakest at 64% - manual-labour vocabulary
  (firewood, plough, wheelbarrow) is thin, and a few STR test rows are arguably DEX.
- Scored 5/8 against the author's own labelled Todoist tasks. His real labels and these
  definitions disagree in places (he files `CA confirmation` as INT, this spec says WIS).
  Labelling ~100 real tasks would resolve it.
- Real inboxes contain 1-2 word tasks that this training set deliberately excludes.
