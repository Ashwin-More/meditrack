// js/pdf.js
// Tiny dependency-free PDF writer (A4, built-in Helvetica fonts) — enough for clinical reports:
// title band, section headings, key/value grids, wrapped paragraphs and tables that
// paginate with a repeating header row, plus "Page X of Y" footers.
// Works offline (no CDN). Built-in fonts are Latin-1 only, so characters outside it print as "?".

const PAGE_W = 595.28, PAGE_H = 841.89, MARGIN = 40;
const CONTENT_W = PAGE_W - MARGIN * 2;
const ACCENT = [0.008, 0.533, 0.82];   // #0288D1
const INK = [0.04, 0.06, 0.08];
const MUTED = [0.4, 0.45, 0.5];
const RULE = [0.85, 0.87, 0.9];
const HEAD_FILL = [0.93, 0.96, 0.99];

// Helvetica / Helvetica-Bold advance widths for ASCII 32..126 (per 1000 em).
const W_REG = [278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500,334,260,334,584];
const W_BOLD = [278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,611,611,389,556,333,611,556,778,556,556,500,389,280,389,584];

const REPLACE = { "\u2014": "-", "\u2013": "-", "\u2212": "-", "\u2018": "'", "\u2019": "'", "\u201C": '"', "\u201D": '"',
  "\u2026": "...", "\u00B7": "|", "\u2022": "\u0095", "\u2192": "->", "\u2190": "<-", "\u2265": ">=", "\u2264": "<=",
  "\u00A0": " ", "\u202F": " ", "\u2009": " ", "\u200A": " ", "\t": " " };

/** Latin-1-safe single-line-friendly text. */
export function clean(value) {
  if (value === null || value === undefined) return "";
  let out = "";
  for (const ch of String(value)) {
    if (ch === "\n") { out += "\n"; continue; }
    if (REPLACE[ch] !== undefined) { out += REPLACE[ch]; continue; }
    const c = ch.codePointAt(0);
    if (c < 32) continue;
    out += (c <= 255) ? ch : "?";
  }
  return out;
}

function charW(ch, bold) {
  const c = ch.charCodeAt(0);
  if (c >= 32 && c <= 126) return (bold ? W_BOLD : W_REG)[c - 32];
  return 556;
}
function textW(text, size, bold) {
  let w = 0;
  for (const ch of text) w += charW(ch, bold);
  return (w * size) / 1000;
}

/** Greedy word wrap into lines no wider than maxW (long words are hard-broken). */
export function wrap(text, size, maxW, bold = false) {
  const lines = [];
  for (const para of clean(text).split("\n")) {
    let line = "";
    for (const word of para.split(" ")) {
      if (word === "") continue;
      let w = word;
      while (textW(w, size, bold) > maxW) {                 // hard-break an over-long word
        let cut = w.length;
        while (cut > 1 && textW(w.slice(0, cut), size, bold) > maxW) cut--;
        if (line) { lines.push(line); line = ""; }
        lines.push(w.slice(0, cut));
        w = w.slice(cut);
      }
      const candidate = line ? `${line} ${w}` : w;
      if (textW(candidate, size, bold) <= maxW) line = candidate;
      else { lines.push(line); line = w; }
    }
    lines.push(line);
  }
  return lines.length ? lines : [""];
}

const num = (n) => (Math.round(n * 100) / 100).toString();
const rgb = (c, stroke = false) => `${c.map(num).join(" ")} ${stroke ? "RG" : "rg"}`;
function esc(s) {
  let out = "";
  for (const ch of s) out += (ch === "(" || ch === ")" || ch === "\\") ? "\\" + ch : ch;
  return out;
}

export class PdfDoc {
  constructor({ title = "Report", footerText = "" } = {}) {
    this.title = clean(title);
    this.footerText = clean(footerText);
    this.pages = [];
    this.y = 0;
    this._newPage();
  }

  _newPage() {
    this.pages.push([]);
    this.y = PAGE_H - MARGIN;
  }
  get _ops() { return this.pages[this.pages.length - 1]; }
  _ensure(h) { if (this.y - h < MARGIN + 24) this._newPage(); }

  _text(x, y, str, size, bold, color = INK) {
    this._ops.push(`BT ${rgb(color)} /${bold ? "F2" : "F1"} ${size} Tf ${num(x)} ${num(y)} Td (${esc(str)}) Tj ET`);
  }
  _rect(x, y, w, h, fill) { this._ops.push(`${rgb(fill)} ${num(x)} ${num(y)} ${num(w)} ${num(h)} re f`); }
  _line(x1, y1, x2, y2, color = RULE, width = 0.6) {
    this._ops.push(`${rgb(color, true)} ${width} w ${num(x1)} ${num(y1)} m ${num(x2)} ${num(y2)} l S`);
  }

  /** Coloured title band at the top of the document. */
  banner(title, subtitle = "") {
    const h = subtitle ? 62 : 46;
    this._rect(MARGIN, this.y - h, CONTENT_W, h, ACCENT);
    this._text(MARGIN + 14, this.y - 26, clean(title), 18, true, [1, 1, 1]);
    if (subtitle) this._text(MARGIN + 14, this.y - 46, wrap(subtitle, 10, CONTENT_W - 28)[0], 10, false, [1, 1, 1]);
    this.y -= h + 16;
  }

  section(title) {
    this._ensure(40);
    this.y -= 8;
    this._text(MARGIN, this.y - 12, clean(title).toUpperCase(), 11, true, ACCENT);
    this._line(MARGIN, this.y - 18, MARGIN + CONTENT_W, this.y - 18, ACCENT, 1);
    this.y -= 28;
  }

  paragraph(text, { size = 10, bold = false, color = INK } = {}) {
    for (const line of wrap(text, size, CONTENT_W, bold)) {
      this._ensure(size + 4);
      this._text(MARGIN, this.y - size, line, size, bold, color);
      this.y -= size + 4;
    }
    this.y -= 2;
  }

  muted(text) { this.paragraph(text, { size: 9.5, color: MUTED }); }
  gap(h = 8) { this.y -= h; }

  /** Label/value pairs laid out in `cols` columns. */
  keyValues(pairs, cols = 2) {
    const colW = CONTENT_W / cols;
    for (let i = 0; i < pairs.length; i += cols) {
      const row = pairs.slice(i, i + cols).map(([label, value]) => ({
        label: clean(label).toUpperCase(),
        lines: wrap(value === null || value === undefined || value === "" ? "-" : value, 10, colW - 12),
      }));
      const h = 12 + Math.max(...row.map((c) => c.lines.length)) * 13 + 6;
      this._ensure(h);
      row.forEach((c, idx) => {
        const x = MARGIN + idx * colW;
        this._text(x, this.y - 9, c.label, 7.5, true, MUTED);
        c.lines.forEach((ln, k) => this._text(x, this.y - 22 - k * 13, ln, 10, false));
      });
      this.y -= h;
    }
    this.y -= 4;
  }

  /** widths = fractions summing to ~1. Header row repeats on each new page. */
  table(headers, rows, widths) {
    const total = widths.reduce((a, b) => a + b, 0);
    const colW = widths.map((w) => (w / total) * CONTENT_W);
    const size = 8.5, lead = 11, padX = 5, padY = 5;

    const drawHeader = () => {
      this._ensure(24 + 20);
      this._rect(MARGIN, this.y - 20, CONTENT_W, 20, HEAD_FILL);
      let x = MARGIN;
      headers.forEach((h, i) => {
        this._text(x + padX, this.y - 13.5, wrap(h, 8, colW[i] - padX * 2, true)[0].toUpperCase(), 8, true, MUTED);
        x += colW[i];
      });
      this.y -= 20;
    };

    drawHeader();
    if (rows.length === 0) {
      this._ensure(22);
      this._text(MARGIN + padX, this.y - 15, "No records.", 9, false, MUTED);
      this.y -= 22;
      return;
    }
    const maxLines = Math.floor((PAGE_H - MARGIN * 2 - 80 - padY * 2) / lead);
    rows.forEach((row) => {
      const cells = row.map((cell, i) => wrap(cell === null || cell === undefined || cell === "" ? "-" : cell, size, colW[i] - padX * 2).slice(0, maxLines));
      const h = Math.max(...cells.map((c) => c.length)) * lead + padY * 2;
      if (this.y - h < MARGIN + 24) { this._newPage(); drawHeader(); }
      let x = MARGIN;
      cells.forEach((lines, i) => {
        lines.forEach((ln, k) => this._text(x + padX, this.y - padY - size - k * lead + 1, ln, size, false));
        x += colW[i];
      });
      this.y -= h;
      this._line(MARGIN, this.y, MARGIN + CONTENT_W, this.y);
    });
    this.y -= 8;
  }

  /** Serialises to a Uint8Array (a valid PDF 1.4 file). */
  toBytes() {
    const total = this.pages.length;
    const contents = this.pages.map((ops, i) => {
      const footer = [
        `${rgb(RULE, true)} 0.6 w ${num(MARGIN)} ${MARGIN} m ${num(MARGIN + CONTENT_W)} ${MARGIN} l S`,
        `BT ${rgb(MUTED)} /F1 8 Tf ${num(MARGIN)} ${MARGIN - 13} Td (${esc(this.footerText)}) Tj ET`,
        `BT ${rgb(MUTED)} /F1 8 Tf ${num(MARGIN + CONTENT_W - 60)} ${MARGIN - 13} Td (${esc(`Page ${i + 1} of ${total}`)}) Tj ET`,
      ];
      return ops.concat(footer).join("\n");
    });

    // Object numbering: 1 catalog, 2 pages, 3 F1, 4 F2, then (page, content) pairs.
    const objs = [];
    objs[1] = "<< /Type /Catalog /Pages 2 0 R >>";
    const kids = this.pages.map((_, i) => `${5 + i * 2} 0 R`).join(" ");
    objs[2] = `<< /Type /Pages /Kids [${kids}] /Count ${total} >>`;
    objs[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>";
    objs[4] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>";
    contents.forEach((c, i) => {
      objs[5 + i * 2] = `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(PAGE_W)} ${num(PAGE_H)}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${6 + i * 2} 0 R >>`;
      objs[6 + i * 2] = { stream: c };
    });
    const info = objs.length;
    objs[info] = `<< /Title (${esc(this.title)}) /Producer (MediTrack) >>`;

    // Every character is <= 0xFF, so a char code equals a byte.
    const chunks = [];
    const offsets = [];
    let length = 0;
    const push = (str) => { chunks.push(str); length += str.length; };
    push("%PDF-1.4\n");
    for (let n = 1; n < objs.length; n++) {
      offsets[n] = length;
      const o = objs[n];
      if (o && o.stream !== undefined) push(`${n} 0 obj\n<< /Length ${o.stream.length} >>\nstream\n${o.stream}\nendstream\nendobj\n`);
      else push(`${n} 0 obj\n${o}\nendobj\n`);
    }
    const xref = length;
    push(`xref\n0 ${objs.length}\n0000000000 65535 f \n`);
    for (let n = 1; n < objs.length; n++) push(`${String(offsets[n]).padStart(10, "0")} 00000 n \n`);
    push(`trailer\n<< /Size ${objs.length} /Root 1 0 R /Info ${info} 0 R >>\nstartxref\n${xref}\n%%EOF`);

    const str = chunks.join("");
    const bytes = new Uint8Array(str.length);
    for (let i = 0; i < str.length; i++) bytes[i] = str.charCodeAt(i) & 0xff;
    return bytes;
  }

  /** Triggers a browser download. */
  save(fileName) {
    const blob = new Blob([this.toBytes()], { type: "application/pdf" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }
}
