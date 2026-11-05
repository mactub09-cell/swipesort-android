SwipeSort 0.4.0 — offline hybrid classification

This patch must be applied ON TOP OF SwipeSort 0.3.0 (OCR models already installed).
Old screenshot decisions and manual categories are kept.

Changes:
- Optional test batch of 40, or explicit full scan of remaining uncategorized screenshots.
- Tesseract OCR rus+eng first, then ML Kit bundled image labeling as fallback.
- Existing 0.3.0 OCR-only analyzed but uncategorized images can be reclassified via visual labels.
- Added categories: Животные, Еда и напитки, Природа.
- Strict thresholds keep uncertain images uncategorized.
- Progress and cancellation; one by one stored decisions.
- API 35+ support maintained. MediaStore and swipe-to-trash left unchanged.
- No network calls and no INTERNET permission. GitHub Actions now fails build if the final APK contains INTERNET permission.

Not included:
- Full semantic VLM / Gemma; ML Kit image labels only recognize a limited number of visual concepts.
- Background service. Leave the app visible during long analysis.
- Automatic deletion. Classification never deletes a file.

Smoke test on phone:
1. Update existing APK; open Categories, take 40-file test batch.
2. Confirm folder counters increase. Check 5 actual screenshots manually.
3. Open 'Без категории' for unresolved screenshots.
4. Swipe left and right in 'Разбор'; ensure it still works.
5. Cancel a long scan and reopen application, ensure existing categorized results remain.
6. Do not run mass-delete until a folder has been reviewed for false positives.

If a build fails, open Actions > failed build step and inspect actual error before modifying Gradle.
