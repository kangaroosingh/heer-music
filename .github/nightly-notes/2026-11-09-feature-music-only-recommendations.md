## New
- Flow Music is a standalone music-only fork with Home, music search, playlists and offline audio downloads. Its separate app ID allows installation alongside Flow and other music apps.
- Punjabi-first taste setup adds favourite artists, language and era preferences, and a discovery slider with a default 60/30/10 familiar/related/wider target.
- Versioned on-device preferences, bounded cached catalogue queries, structured recommendation explanations and local original/improved comparison counters.
- Song dislikes hide only that recording until undone; blocking an artist remains a separate explicit action. Dislikes never delete downloaded files.

## Improved
- Recommendation sequencing avoids the last 20 played tracks in autoplay and adjacent identical artists when alternatives exist. Distinct original, live and remix IDs remain separate recordings.
- Likes, intentional replays and playlist saves teach the existing music engine. Audible early manual skips have a weak track-level effect; playback failures and interruptions do not count as negative feedback.
- Regular YouTube video, Shorts and public-playlist links open through the music player. Shared download prompts offer audio in the music fork.
- Recommendation reset, feedback undo and the original-ranking fallback are available in Your music taste. Private listening sessions are excluded from the new local statistics.

## Improved
- The original Music and Neuro benchmark floors remain unchanged; the new policy intentionally changes cold-start ordering only in improved mode.
- Automated recommendation tests cover balance targets, shortages, missing metadata, recording identity, repetition, song dislike undo and interruption handling.
- This is a test preview. Phone listening quality and compatibility with a physical Android Auto head unit still require user validation.
- Catalogue language and era preferences guide search queries. Missing catalogue metadata stays unknown rather than being guessed from a title.
- GPLv3 and original Flow credits are retained. This fork does not modify Navidrome or synchronise Google private playlists.
