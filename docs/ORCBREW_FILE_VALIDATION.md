# OrcBrew File Import/Export Validation

## Overview

This document explains the comprehensive validation system for `.orcbrew` files, which helps catch and fix bugs before they frustrate users.

## What Changed?

### Before
- ❌ Generic "Invalid .orcbrew file" errors
- ❌ No way to know what's wrong with a file
- ❌ One bad item breaks the entire import
- ❌ Bugs exported into files with no warning
- ❌ Silent failures and data loss

### After
- ✅ Detailed error messages explaining what's wrong
- ✅ Progressive import (recovers valid items, skips invalid ones)
- ✅ Pre-export validation (catches bugs before they're saved)
- ✅ Automatic fixing of common corruption patterns
- ✅ Console logging for debugging

## Features

### 1. **Pre-Export Validation**

Before creating an `.orcbrew` file, the system now validates your content:

```
Exporting "My Homebrew Pack"...
✓ Checking for nil values
✓ Checking for empty option-pack strings
✓ Validating data structure
✓ Running spec validation

Export successful!
```

**If issues are found:**
- ⚠️ **Warnings** - File exports but issues are logged
- ❌ **Errors** - File won't export, must fix issues first

**Check the browser console** (F12) for detailed information about any warnings or errors.

### 2. **Enhanced Import Validation**

When importing an `.orcbrew` file, you now get detailed feedback:

#### **Successful Import**
```
✅ Import successful

Imported 25 items

To be safe, export all content now to create a clean backup.
```

#### **Import with Warnings**
```
⚠️ Import completed with warnings

Imported: 23 valid items
Skipped: 2 invalid items

Invalid items were skipped. Check the browser console for details.

To be safe, export all content now to create a clean backup.
```

#### **Parse Error**
```
⚠️ Could not read file

Error: EOF while reading
Line: 45

The file may be corrupted or incomplete. Try exporting a
fresh copy if you have the original source.
```

#### **Validation Error**
```
⚠️ Invalid orcbrew file

Validation errors found:
  • at spells > fireball: Missing required field: :option-pack
  • at races > dwarf: Invalid value format
    Got: {:name "Dwarf", :option-pack nil}

To recover data from this file, you can:
1. Try progressive import (imports valid items, skips invalid ones)
2. Check the browser console for detailed validation errors
3. Export a fresh copy if you have the original source
```

### 3. **Progressive Import**

The default import strategy is **progressive**, which means:

- ✅ Valid items are imported successfully
- ⚠️ Invalid items are skipped and reported
- 📊 You get a count of imported vs skipped items
- 🔍 Detailed errors for skipped items appear in the console

**Example:**

If your file has 10 spells and 2 of them are missing the `option-pack` field:
- The 8 valid spells are imported
- The 2 invalid spells are skipped
- You see: "Imported: 8 valid items, Skipped: 2 invalid items"
- Console shows exactly which spells were skipped and why

### 4. **Automatic Cleaning**

The system automatically fixes these common corruption patterns:

| Pattern | Fix |
|---------|-----|
| `disabled? nil` | `disabled? false` |
| `nil nil,` | (removed) |
| `:field-name nil` | (removed) |
| `option-pack ""` | `option-pack "Default Option Source"` |
| Empty plugin name `""` | `"Default Option Source"` |

**This happens automatically** - you don't need to do anything!

### 5. **Required Field Validation**

The system validates that all content has required fields (like `:name`), with different behavior for import vs export:

**On Import (Permissive):**
- Missing required fields are auto-filled with placeholder data
- Placeholder examples: `[Missing Name]`, `[Missing Trait Name]`
- Import continues without interruption
- Changes are logged for user awareness

**On Export (Strict):**
- Missing fields trigger a warning modal
- Modal lists all items with issues
- User can:
  - **Cancel**: Go back and fix the issues manually
  - **Export Anyway**: Export with placeholder data filled in

**Required fields by content type:**

| Content Type | Required Fields |
|--------------|-----------------|
| Classes | `:name` |
| Subclasses | `:name` |
| Races | `:name` |
| Subraces | `:name` |
| Backgrounds | `:name` |
| Feats | `:name` |
| Spells | `:name`, `:level`, `:school` |
| Monsters | `:name` |
| Invocations | `:name` |
| Languages | `:name` |
| Traits (nested) | `:name` |

### 6. **Unicode Normalization**

Text content is automatically normalized to ASCII-safe characters. This prevents encoding issues with PDF generation and ensures compatibility across systems.

**Characters automatically converted:**

| Category | Examples | Converted To |
|----------|----------|--------------|
| Smart quotes | `'` `'` `"` `"` | `'` `"` |
| Dashes | `–` (en-dash) `—` (em-dash) | `-` `--` |
| Special spaces | non-breaking, thin, em | regular space |
| Symbols | `…` `•` `©` `®` `™` | `...` `*` `(c)` `(R)` `(TM)` |

**Why this matters:**
- Smart quotes often sneak in from copy/paste from Word, Google Docs, etc.
- PDF fonts may not have glyphs for these characters
- Ensures clean exports that work everywhere

**This happens during:**
- Import (after EDN parsing)
- Homebrew save (when you click Save)

### 6. **Detailed Console Logging**

Open the browser console (F12) to see:

**On Export:**
```javascript
Export warnings for "My Pack":
  Item spells/fireball has missing option-pack
  Found nil value for key: :some-field
```

**On Import:**
```javascript
Import validation result: {
  success: true,
  imported_count: 23,
  skipped_count: 2,
  skipped_items: [
    {key: :invalid-spell, errors: "..."},
    {key: :bad-race, errors: "..."}
  ]
}

Skipped invalid items:
  :invalid-spell
    Errors: Missing required field: :option-pack
  :bad-race
    Errors: Invalid value format
```

## How to Use

### Exporting Content

**Single Plugin:**
1. Go to "Manage Homebrew"
2. Click "Export" next to your plugin
3. If warnings appear, check the console (F12)
4. Fix any issues and export again

**All Plugins:**
1. Go to "Manage Homebrew"
2. Click "Export All"
3. If any plugin has errors, export is blocked
4. Check console for which plugin has issues
5. Fix issues and try again

### Importing Content

**Standard Import (Progressive):**
1. Click "Import Content"
2. Select your `.orcbrew` file
3. Read the import message
4. If warnings appear, check console for details
5. **IMPORTANT:** Export all content to create a clean backup

**Strict Import (All-or-Nothing):**

For users who want the old behavior (reject entire file if any item is invalid):

*This feature is available via browser console only:*
```javascript
// In browser console
dispatch(['orcpub.dnd.e5.events/import-plugin-strict', 'Plugin Name', 'file contents'])
```

## Common Error Messages & Fixes

### "Missing required field: :option-pack"

**Cause:** Item doesn't have an `option-pack` field

**Fix:** Each item MUST have `:option-pack "<source name>"`

```clojure
;; Bad
{:name "Fireball" :level 3}

;; Good
{:option-pack "My Homebrew"
 :name "Fireball"
 :level 3}
```

### "EOF while reading"

**Cause:** File is incomplete or has unmatched brackets

**Fix:**
1. Check for missing `}`, `]`, or `)`
2. Make sure every opening bracket has a closing bracket
3. If file is very corrupted, you may need to restore from backup

### "Invalid value format"

**Cause:** Value doesn't match expected format (e.g., number instead of string)

**Fix:** Check the item in console logs to see which field is wrong

### "File appears to be corrupted"

**Cause:** File is incomplete, truncated, or contains invalid characters

**Fix:**
1. Try re-exporting from the original source
2. If you only have this copy, try progressive import to recover what you can

## Best Practices

### 1. **Always Export After Importing**

After importing any file:
```
Import → Check Message → Export All → Save Backup
```

This creates a clean version with all auto-fixes applied.

### 2. **Check Console Regularly**

When working with homebrew content, keep the browser console open (F12) to catch issues early.

### 3. **Keep Backups**

Export your content regularly:
- After major changes
- After importing new content
- Before deleting or modifying existing content

### 4. **Use Progressive Import for Recovery**

If you have a corrupted file, progressive import can recover valid items:
- You'll lose invalid items, but keep everything else
- Console will show exactly what was lost
- You can manually recreate lost items

### 5. **Fix Warnings Before Sharing**

If you're sharing your homebrew:
- Export and check for warnings
- Fix any issues
- Export again to create a clean file
- Share the clean file

## Technical Details

### Validation Process

1. **Auto-Clean** - Fix common corruption patterns
2. **Parse EDN** - Convert text to data structure
3. **Validate Structure** - Check against spec
4. **Item-Level Validation** - Check each item individually
5. **Report Results** - Show user-friendly messages

### Import Strategies

**Progressive (Default):**
- Imports valid items
- Skips invalid items
- Reports what was skipped
- Best for recovery

**Strict (Optional):**
- All items must be valid
- Rejects entire file if any item is invalid
- Best for validation

### Spec Requirements

Every homebrew item must have:
- `:option-pack` (string) - The source/pack name
- Valid qualified keywords for content types
- Keywords must start with a letter
- Content types must be in `orcpub.dnd.e5` namespace

### Error Data Structure

All errors include:
- **error** - Error message
- **context** - Where the error occurred
- **hint** - Suggested fix (when available)
- **line** - Line number (for parse errors)

## Developer Tools

### Lein Prettify Tool

A command-line tool for analyzing and debugging orcbrew files without running the full app:

```bash
# Analyze a file for issues
lein with-profile +tools prettify-orcbrew path/to/file.orcbrew --analyze

# Pretty-print a file (for manual inspection)
lein with-profile +tools prettify-orcbrew path/to/file.orcbrew

# Write prettified output to file
lein with-profile +tools prettify-orcbrew path/to/file.orcbrew --output=pretty.edn
```

**The `+tools` profile skips Garden CSS compilation for faster startup.**

**Analysis output includes:**
- File size
- `nil nil` pattern count
- Problematic Unicode characters (with counts by type)
- Unknown non-ASCII characters
- Disabled entry count
- Plugin structure (single vs multi-plugin)
- Traits missing `:name` fields

**Example output:**
```
=== Content Analysis ===
File size: 1843232 bytes

[WARNING] Found 36 'nil nil,' patterns
  Spurious nil key-value pairs (e.g., {nil nil, :key :foo})

[WARNING] Found 1621 problematic Unicode characters (will be auto-fixed on import):
  - right single quote (U+2019): 1598 occurrences
  - left double quote (U+201C): 23 occurrences

[INFO] Found 48 disabled entries (previously errored content)
```

## Troubleshooting

### Import Fails with "Could not read file"

**Likely Cause:** File is corrupted or incomplete

**Solutions:**
1. Check file size - is it much smaller than expected?
2. Open in text editor - does it end abruptly?
3. Try progressive import to recover what you can
4. If you have the original source, re-export

### Import Shows "Skipped X items"

**Likely Cause:** Some items are invalid

**Solutions:**
1. Check console (F12) for detailed errors
2. Note which items were skipped
3. Re-create those items manually
4. Export all content to save clean version

### Export Blocked with Errors

**Likely Cause:** Your content has invalid data

**Solutions:**
1. Check console for which plugin has errors
2. Look for items with empty/nil option-pack
3. Fix the issues
4. Try export again

### Console Shows "nil value" Warnings

**Likely Cause:** Some fields have `nil` instead of proper values

**Impact:** Usually auto-fixed, but may indicate data quality issues

**Solutions:**
1. Review your content
2. Fill in missing fields
3. Export to create clean version

## Related Features

This validation system works alongside other import/export features:

### Conflict Resolution
When duplicate keys are detected during import, the **Conflict Resolution** system helps you:
- Detect duplicate keys (same key in different sources)
- Choose how to handle each conflict (rename, skip, or replace)
- Automatically update references when renaming

**See:** [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)

### Missing Content Detection
After import, the **Content Reconciliation** system:
- Detects when characters reference content that isn't loaded
- Suggests similar content using fuzzy matching
- Helps identify which plugins are needed

**See:** [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md)

### Error Handling Framework
All validation operations use the **Error Handling** framework:
- Structured error messages with `ex-info`
- Consistent logging and error reporting
- User-friendly error messages

**See:** [ERROR_HANDLING.md](ERROR_HANDLING.md)

### Required Fields
Understand what fields are needed for each content type:
- Spec requirements vs functional requirements
- Which fields can break features if missing
- Default values and optional fields

**See:** [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md)

## Support

If you encounter issues not covered here:

1. **Check the console** (F12) for detailed errors
2. **Create an issue** on GitHub with:
   - Error message from console
   - Steps to reproduce
   - Sample .orcbrew file (if you can share it)
3. **Try progressive import** to recover data

## Migration Guide

### For Existing Users

Your existing `.orcbrew` files will continue to work! The system:

1. Auto-cleans common issues
2. Uses progressive import by default
3. Helps you create cleaner files going forward

**Recommended:**
1. Import your existing files
2. Check for any warnings
3. Export all content to create clean versions
4. Use the clean versions going forward

### For Developers

If you're building tools that generate `.orcbrew` files:

1. **Use the validation API** before creating files
2. **Ensure all items have option-pack**
3. **Avoid nil values** in output
4. **Test with the validator** to catch issues early

```clojure
;; Validate before export
(require '[orcpub.dnd.e5.import-validation :as import-val])

(let [result (import-val/validate-before-export my-plugin)]
  (if (:valid result)
    (export-to-file my-plugin)
    (handle-errors (:errors result))))
```

## Version History

### v2.0 - Comprehensive Validation (Current)
- Added pre-export validation
- Added progressive import
- Added detailed error messages
- Added automatic cleaning
- Added console logging
- 30+ validation test cases

### v1.0 - Basic Validation (Legacy)
- Simple spec validation
- All-or-nothing import
- Generic error messages

---

**Questions?** Open an issue on GitHub or check the browser console for detailed error information.
