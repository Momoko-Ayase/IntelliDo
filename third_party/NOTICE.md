# Third-party icon and emoji assets

IntelliDo fetches complete local copies at build time instead of a hand-picked subset.
Pins live in `scripts/vendor-lock.json`. Do not commit the generated files under
`domain/build/generated/` or `domain/src/main/resources/vendor/`.

## Font Awesome Free 6.7.2

- Source: https://fontawesome.com / `@fortawesome/fontawesome-free@6.7.2`
- Archive: https://registry.npmjs.org/@fortawesome/fontawesome-free/-/fontawesome-free-6.7.2.tgz
- Icons: CC BY 4.0
- Fonts: SIL OFL 1.1
- Code: MIT
- License file: `fontawesome-LICENSE.txt`
- Generated table: `/vendor/fontawesome/glyphs.tsv` on the runtime classpath

## Twemoji (via discourse-emojis v1.0.46)

- Graphics: Copyright Twitter, Inc. and other contributors, CC BY 4.0
- Shortcode database: [discourse-emojis](https://github.com/discourse/discourse-emojis) tag `v1.0.46`
- Archive: https://github.com/discourse/discourse-emojis/archive/refs/tags/v1.0.46.tar.gz
- Generated table: `/vendor/emoji/shortcodes.tsv`
- PNGs: `/vendor/twemoji/`

`./gradlew check` (or `./gradlew :domain:vendorIconAssets`) downloads the pinned archives into
`tmp/vendor-dl/`, verifies SHA-256, and writes resources under `domain/build/generated/resources`.
Python 3 is required. Updating pins means editing `scripts/vendor-lock.json` and re-running the task.
