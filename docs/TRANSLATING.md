# Translating Adhkaar

There are two kinds of text, and each is translated differently.

## 1. The interface (buttons, labels, messages)

Android string resources live in `app/src/main/res/values/strings_*.xml` (English). Each
language has its own folder: `values-ar`, `values-ur`, `values-ha`, `values-yo`, `values-ig`.

- Keep placeholders exactly as they are (`%1$s`, `%2$d`). You may change their order.
- The `<!-- comments -->` explain what a string means or what a placeholder holds.
- The Hausa, Yoruba and Igbo interface files started as drafts. **A native speaker must review
  them before release**, and the file header says whether that review has been done.

## 2. The meanings of the adhkaar (religious content)

These live in `app/src/main/assets/i18n/<language>.json`. Start from
[`i18n-template.json`](i18n-template.json), which lists every dhikr's title, meaning and
virtue in English under its id.

- **Take meanings from a trusted, published translation, or have them checked by someone with
  knowledge. Never use machine translation for these.** Fill in `source` and `reviewedBy`.
- Translate only `title`, `translation` and `virtue`. The Arabic and the hadith references are
  never translated.
- You don't have to translate everything at once. Anything missing is shown in English.
- `ﷺ` stays as it is. The app draws it at a readable size.
