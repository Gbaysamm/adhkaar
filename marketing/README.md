# Marketing posts

Social posts for sharing Adhkaar, built from the app's real screens.

- `export/` holds the finished images (2160 × 2700, the 4:5 portrait size for Instagram, Facebook and WhatsApp).
- `build.py` holds each post's words and layout. `post.css` is the shared grid:
  - 72px margins
  - the brand top-left and the story's time top-right
  - the headline, then the paragraph, the note and the footer in the left column
  - the phone in the right column, bleeding off the bottom edge
- `assets/` holds the screens, taken from the app via the simulator's captures (`site/sim/app`) and the website (`site/img`).

To change a post or add one, edit `build.py`, then run `python marketing/build.py`. It writes `posts/*.html` and exports them with headless Edge.
