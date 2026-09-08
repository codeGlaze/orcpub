# Branch changelog — `fix/item-builder-save-label`

## Why this branch exists

Every builder's save button says "Save to Browser Storage", and for every builder
but one that is true. A magic item is saved to the database, like a character —
`::mi/save-item` posts to `/dnd/5e/items` with an auth header — so on that page the
label promised local storage while requiring an account, and a logged-out click
went to the login page having never said an account was needed.

## Fixed

- **The item builder's save button says "Save Item"** rather than claiming browser
  storage it does not use.
