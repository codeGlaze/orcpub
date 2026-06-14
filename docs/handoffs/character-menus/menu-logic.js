/**
 * Framework-agnostic logic for the growable multi-select character menus.
 *
 * These are the three "hard" behaviors from the prototype, extracted as pure
 * functions so they drop into any stack (React/Vue/Svelte/etc). They have no
 * UI dependencies — feed them the option label array, render the result however
 * your component library prefers.
 *
 * All functions are sort-agnostic and length-agnostic: the menus are assumed to
 * be alphabetically sorted and to grow over time, and nothing here hardcodes a
 * known set of options.
 */

/**
 * Detect the DOMINANT leading wording shared by the MAJORITY of option labels.
 *
 * Unlike a strict "longest common prefix", this tolerates additions that don't
 * follow the pattern: a single off-spec option won't suppress the collapse — it
 * simply won't be counted as conforming (see `classifyOptions`).
 *
 * @param {string[]} labels   Full option labels.
 * @param {object}  [opts]
 * @param {number}  [opts.minWords=4]   Minimum words a prefix must have to count.
 * @param {number}  [opts.ratio=0.5]    Fraction of options that must share it.
 * @returns {string} The shared prefix INCLUDING its trailing space, or '' if none.
 */
export function dominantPrefix(labels, opts = {}) {
  const minWords = opts.minWords ?? 4;
  const ratio = opts.ratio ?? 0.5;
  const n = labels.length;
  if (n < 3) return '';

  const tokens = labels.map((l) => l.split(' '));
  const maxW = Math.max(...tokens.map((t) => t.length));
  const threshold = Math.max(2, Math.ceil(n * ratio));

  let best = '';
  for (let w = minWords; w <= maxW; w++) {
    const counts = Object.create(null);
    for (const t of tokens) {
      // require at least one word AFTER the prefix (the keyword slot)
      if (t.length >= w + 1) {
        const key = t.slice(0, w).join(' ');
        counts[key] = (counts[key] || 0) + 1;
      }
    }
    let bestKey = null;
    let bestCount = 0;
    for (const k in counts) {
      if (counts[k] > bestCount) {
        bestCount = counts[k];
        bestKey = k;
      }
    }
    if (bestKey && bestCount >= threshold) best = bestKey + ' ';
    else break; // longer prefixes can only shrink the count
  }
  return best;
}

/**
 * Classify every option against the detected prefix.
 *
 * @param {string[]} labels
 * @param {string}   prefix   Output of dominantPrefix (may be '').
 * @returns {Array<{label, display, conform, nonStandard, divergeAt}>}
 *   - display      keyword-only text when conforming, else the full label
 *   - nonStandard  true when a prefix exists but this label doesn't follow it
 *   - divergeAt    char index where this label stops matching the prefix
 *                  (use it to render the shared head muted + the tail highlighted)
 */
export function classifyOptions(labels, prefix) {
  return labels.map((label) => {
    const conform = !!prefix && label.startsWith(prefix);
    const nonStandard = !!prefix && !conform;
    let divergeAt = 0;
    if (nonStandard) {
      while (
        divergeAt < label.length &&
        divergeAt < prefix.length &&
        label[divergeAt] === prefix[divergeAt]
      ) {
        divergeAt++;
      }
    }
    return {
      label,
      display: conform ? label.slice(prefix.length) : label,
      conform,
      nonStandard,
      divergeAt,
    };
  });
}

/**
 * Bucket options into A–Z groups by their DISPLAY text (so collapsed keywords
 * group by their own first letter, not the shared prefix). Non-alphabetic
 * leads fall into '#'. Returns groups in alphabetical order.
 *
 * @param {Array<{display:string}>} items
 * @returns {Array<{letter:string, items:Array}>}
 */
export function groupByLetter(items) {
  const buckets = Object.create(null);
  for (const it of items) {
    let ch = (it.display[0] || '#').toUpperCase();
    if (!/[A-Z]/.test(ch)) ch = '#';
    (buckets[ch] = buckets[ch] || []).push(it);
  }
  return Object.keys(buckets)
    .sort()
    .map((letter) => ({ letter, items: buckets[letter] }));
}

/**
 * Case-insensitive filter that matches against BOTH the collapsed display text
 * and the full label, so searching the shared wording still finds options.
 */
export function filterOptions(items, query) {
  const q = (query || '').trim().toLowerCase();
  if (!q) return items;
  return items.filter(
    (it) =>
      it.display.toLowerCase().includes(q) ||
      it.label.toLowerCase().includes(q),
  );
}
