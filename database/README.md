# Stat Up classifier database

Two files. Both have columns `task,category` with category in
`STR | INT | WIS | DEX | CHA | VIT`.

| file | rows | purpose |
|---|---|---|
| `train.csv` | 2127 | training set: 300 per stat, plus 327 rows in the author's real-inbox style (see below) |
| `test.csv` | 300 | held-out evaluation, 50 per stat, zero overlap with train |

## Rules every row must follow

- **Minimum 2 words.** Single words carry too little signal. Two-word rows are allowed only when
  the words are unambiguous (`DSA practice`, `Flight booking`), since ~23% of a real inbox is 1-2 words.
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

## Real-inbox rows (added 2026-10-15)

The last 327 rows of `train.csv` were written after scoring the author's 253 completed Todoist
tasks (Apr-Oct 2026). The shipped model was confident on only 59% of them, and right on only 61% of
those. The rows are paraphrases covering the gaps it showed, never the real tasks: the repo is public,
so no names, employers or amounts. The real tasks stay a local-only eval set, not committed.

- AI/ML research and dev side projects (papers, fine-tuning, agents, deploy, GitHub, app releases) -> INT
- money, bookings, subscriptions, job applications and portals, phone/laptop admin -> WIS
- mails and calls to people, referrals, interviews, meetups -> CHA
- making videos and design work -> DEX
- chores keep the existing convention: heavy (mopping, hand washing) -> STR, hygiene (bedsheets) -> VIT

| | before | after |
|---|---|---|
| held-out `test.csv`, int8 | 84.7% | 85.7% |
| real tasks: confident (>= 0.55) | 58.6% | 73.3% |
| real tasks: right when confident | 61.0% | 89.0% |

The real-task numbers are optimistic (the rows were written after seeing those tasks); with the 5 rows
that match a real task word for word excluded, it is 72.1% / 88.3%. The honest check is re-scoring
tasks completed after 2026-10-15.

## Known limitations (accepted, to improve later)

- STR is weakest on the held-out set (62%) - manual-labour vocabulary
  (firewood, plough, wheelbarrow) is thin, and a few STR test rows are arguably DEX.
- Project codenames and 1-word tasks (`Phoenix`, `Zeta stuff`, `Again`) can't be classified; the model stays
  silent on about half of them, which is the right outcome.
