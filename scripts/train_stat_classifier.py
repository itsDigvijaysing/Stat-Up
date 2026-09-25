#!/usr/bin/env python3
"""Train the Stat Up task->stat classifier.

Hashed character n-grams + multinomial logistic regression (the fastText shape:
a linear model over n-gram features). Pure numpy - no sklearn, no torch.

Exports an int8-quantised weight blob that the Android app loads from assets.
Keep the constants below in sync with the Kotlin reader.

    python3 scripts/train_stat_classifier.py
    python3 scripts/train_stat_classifier.py --export app/src/main/assets/classifier/stat_clf_v1.bin
"""
import argparse, csv, struct, sys, zlib
from pathlib import Path
import numpy as np

STATS = ["STR", "INT", "WIS", "DEX", "CHA", "VIT"]
BUCKETS = 16384         # must match the Kotlin side
NGRAM_MIN, NGRAM_MAX = 3, 6
WORD_FEATURES = True    # word uni+bigrams alongside the char n-grams
THRESH_UI, THRESH_SYNC = 0.55, 0.65
SEED = 0


def featurise(text: str) -> np.ndarray:
    """char_wb-style n-grams: each word padded with spaces, hashed into BUCKETS."""
    v = np.zeros(BUCKETS, dtype=np.float32)
    toks = text.lower().split()
    for word in toks:
        padded = f" {word} "
        for n in range(NGRAM_MIN, NGRAM_MAX + 1):
            for i in range(len(padded) - n + 1):
                v[zlib.crc32(padded[i:i + n].encode()) % BUCKETS] += 1.0
    if WORD_FEATURES:
        for w in toks:
            v[zlib.crc32(("W#" + w).encode()) % BUCKETS] += 2.0
        for a, b in zip(toks, toks[1:]):
            v[zlib.crc32(("W#" + a + "_" + b).encode()) % BUCKETS] += 2.0
    norm = np.linalg.norm(v)
    return v / norm if norm > 0 else v


def load(path):
    rows = list(csv.DictReader(open(path, encoding="utf-8")))
    X = np.stack([featurise(r["task"]) for r in rows])
    y = np.array([STATS.index(r["category"]) for r in rows])
    return X, y, [r["task"] for r in rows]


def softmax(z):
    z = z - z.max(axis=1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=1, keepdims=True)


def fit(X, y, epochs=500, lr=0.5, l2=1e-5):
    n, d = X.shape
    k = len(STATS)
    W = np.zeros((d, k), dtype=np.float32)
    b = np.zeros(k, dtype=np.float32)
    Y = np.eye(k, dtype=np.float32)[y]
    mW = vW = np.zeros_like(W); mb = vb = np.zeros_like(b)
    for t in range(1, epochs + 1):
        P = softmax(X @ W + b)
        gW = X.T @ (P - Y) / n + l2 * W
        gb = (P - Y).mean(axis=0)
        mW = 0.9 * mW + 0.1 * gW; vW = 0.999 * vW + 0.001 * gW ** 2
        mb = 0.9 * mb + 0.1 * gb; vb = 0.999 * vb + 0.001 * gb ** 2
        W -= lr * (mW / (1 - 0.9 ** t)) / (np.sqrt(vW / (1 - 0.999 ** t)) + 1e-8)
        b -= lr * (mb / (1 - 0.9 ** t)) / (np.sqrt(vb / (1 - 0.999 ** t)) + 1e-8)
    return W, b


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="database/train.csv")
    ap.add_argument("--test", default="database/test.csv")
    ap.add_argument("--folds", type=int, default=5)
    ap.add_argument("--export")
    a = ap.parse_args()

    X, y, texts = load(a.data)
    print(f"{len(y)} rows, {X.shape[1]} features\n")

    rng = np.random.default_rng(SEED)
    order = rng.permutation(len(y))
    folds = np.array_split(order, a.folds)

    accs, cm = [], np.zeros((len(STATS), len(STATS)), dtype=int)
    misses = []
    allP = np.zeros((len(y), len(STATS)))
    for i, test in enumerate(folds):
        train = np.concatenate([f for j, f in enumerate(folds) if j != i])
        W, b = fit(X[train], y[train])
        allP[test] = softmax(X[test] @ W + b)
        pred = allP[test].argmax(axis=1)
        accs.append((pred == y[test]).mean())
        for t, p in zip(y[test], pred):
            cm[t, p] += 1
        misses += [(texts[ix], STATS[y[ix]], STATS[p])
                   for ix, p in zip(test, pred) if p != y[ix]]

    print(f"{a.folds}-fold accuracy: {np.mean(accs)*100:.1f}%  (±{np.std(accs)*100:.1f})\n")
    print("confusion (row = true, col = predicted)")
    print("      " + "".join(f"{s:>6}" for s in STATS))
    for i, s in enumerate(STATS):
        print(f"{s:>5} " + "".join(f"{c:>6}" for c in cm[i]) + f"   {cm[i,i]/cm[i].sum()*100:5.1f}%")

    conf = allP.max(axis=1); pr = allP.argmax(axis=1)
    print("\nprecision vs coverage (below threshold the app falls back to the picker)")
    print(f"{'threshold':>10}{'coverage':>11}{'precision':>11}")
    for th in (0.0, 0.45, THRESH_UI, THRESH_SYNC, 0.75):
        m = conf >= th
        if m.sum() == 0:
            continue
        tag = "  <- mission dialog" if th == THRESH_UI else ("  <- todoist sync" if th == THRESH_SYNC else "")
        print(f"{th:>10.2f}{m.mean()*100:>10.1f}%{(pr[m] == y[m]).mean()*100:>10.1f}%{tag}")

    print(f"\n{len(misses)} misclassified. Sample:")
    for t, a_, p in misses[:15]:
        print(f"  [{a_} -> {p}]  {t}")

    import os
    if os.path.exists(a.test):
        Xt, yt, _ = load(a.test)
        W, b = fit(X, y)
        Pt = softmax(Xt @ W + b); pt = Pt.argmax(axis=1); ct = Pt.max(axis=1)
        print(f"\n*** HELD-OUT TEST ({len(yt)} rows): {(pt == yt).mean()*100:.1f}% ***")
        print(f"{'threshold':>10}{'coverage':>11}{'precision':>11}")
        for th in (0.0, 0.45, THRESH_UI, THRESH_SYNC, 0.75):
            m = ct >= th
            if m.sum():
                print(f"{th:>10.2f}{m.mean()*100:>10.1f}%{(pt[m] == yt[m]).mean()*100:>10.1f}%")

    if a.export:
        W, b = fit(X, y)
        scale = np.abs(W).max() / 127.0
        q = np.clip(np.round(W / scale), -127, 127).astype(np.int8)
        out = Path(a.export); out.parent.mkdir(parents=True, exist_ok=True)
        with open(out, "wb") as fh:
            fh.write(b"STCL")
            fh.write(struct.pack("<HHH", 1, BUCKETS, len(STATS)))
            fh.write(struct.pack("<f", float(scale)))
            fh.write(b.astype("<f4").tobytes())
            fh.write(q.T.tobytes())          # [class][bucket]
        print(f"\nexported {out}  ({out.stat().st_size/1024:.1f} KB)")


if __name__ == "__main__":
    main()
