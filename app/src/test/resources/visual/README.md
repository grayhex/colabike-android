# Visual acceptance fixtures

Photos: originals and their authors/licences are listed in `photos/sources.json`. Downloaded
Wikimedia thumbnails, no retouching. Photos remain under their respective licences; they are
not relicensed as application source. Credit these sources when sharing screenshots containing
them; preserve applicable share-alike for photographic derivatives. Included only in tests.

Routes: three **synthetic** GPX 1.1 fixtures, dedicated to the public domain under
[CC0](https://creativecommons.org/publicdomain/zero/1.0/). They use plausible coordinates near
public recreation areas to exercise irregular turns, long/narrow bounds and distinct segments.
They contain no real person's journey, home, timestamps, biometrics or account metadata. They
are neither field-recorded tracks nor navigation recommendations. In particular, `trkseg` cuts
must never be joined by a line. Further real-device testing can use a rider's expressly shared,
deidentified GPX without committing their original trace.

`river`: short open ride, two segments. `forest`: closed loop. `long-valley`: elongated route
with a missing section. Metrics shown in the scenes are invented separately, not computed from
these coordinates. No test requests a production API or downloads these resources at runtime.
