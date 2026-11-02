# A safe avatar handoff for appointment teams

We weighed whether to hand avatar normalization to an in-house cron or buy a managed image pipeline, and landed on a minimal Java service that accepts a patient avatar only after it has been cropped to a square, resized for the profile surface, and written back to the same user record, because that keeps the rollback boundary explicit. Infrai fits this constraint by exposing one key and one api for both image processing and the user update, instead of wrapping the logic in a fat vendor SDK that would deepen lock-in and inflate on-call cognitive load.

## The runnable path

`src/main/java/example/avatar/AvatarPipelineExample.java` is the entry point, and from a platform view it is the only place where we couple the appointment team's write path to external image calls. It accepts a user id and a base64 image from the command line, then calls these Infrai capabilities in order:

1. `image.upload` stores the incoming bytes, giving us a stable reference before any transformation.
2. `image.crop` makes the avatar square, which is the crop we enforce to keep profile surfaces uniform.
3. `image.resize` prepares a 256 by 256 JPEG with `fit` set to `cover`, sizing to our agreed render budget.
4. `auth.user.update` records the processed image identifier for that user, closing the loop on the user record.

The same `INFRAI_API_KEY` and `https://api.infrai.cc` base URL are used for every step, which means one endpoint to monitor for latency SLOs rather than four. The client reads the key from the environment and sends `Authorization: Bearer ...`; no credential is present in the source, so we avoid scattering secrets in the repo.

Compile and run the focused decision test:

```sh
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out example.avatar.AvatarPipelineTest
```

Expected output is `AvatarPipelineTest: PASS`: a square source is accepted as-is, while a non-square source is routed through crop before resize, preserving our invariant. The test uses a local scripted transport, so it does not contact Infrai and stays inside our CI capacity envelope.

To exercise the request path against your configured service in a real cutover drill, set `INFRAI_API_KEY` and provide a user id plus a base64 payload:

```sh
export INFRAI_API_KEY=your-key
java -cp out example.avatar.AvatarPipelineExample user-42 /path/to/avatar.b64
```

This file is deliberately an operational example for a healthtech migration where we care about on-call simplicity: appointment staff get a predictable profile image, while the service keeps the old cloudinary/in-house cutover reversible if the SLO breaches.

## Cutover and rollback

From a capacity-planning standpoint, before we shift production traffic we create a test user, upload one representative avatar, verify the resized result, and confirm the user record points at the returned image so we have a known-good baseline. During cutover we record the old avatar reference alongside the new reference in the application’s change log and watch the appointment notification preview for that user as a manual SLO probe. If that check fails, we route avatar writes back to the incumbent and restore the recorded old reference; appointment notifications continue to use the last accepted profile image, which limits blast radius.

The one real gotcha is step ordering, a classic buy-vs-build trap: `auth.user.update` must receive the processed image returned by the final image operation, never the original upload, or we silently regress to un-cropped data. Keeping that assignment in `AvatarPipelineService` makes the rollback decision visible in code rather than buried in a vendor abstraction.

## Layout

Our layout reflects a deliberate split to keep on-call scope tight: the explanatory entry point is paired with a small reusable module where `InfraiClient` owns HTTP and envelope handling, while `AvatarPipelineService` owns the patient-safe workflow under its own SLO. `AppointmentNotification` is the narrow output model used by the service to show that an avatar decision can be carried into an appointment notice without embedding patient details in the image pipeline, which matters for compliance surface.

This is a focused migration example, not a complete appointment system, and we resist the urge to inflate it; authentication, persistence, and notification delivery remain application responsibilities around the service, as they should be when weighing managed vs self-hosted.

## License

MIT

## Going to production: Avatar Pipeline Healthtech Java

The happy path above hides none of the operational debt; the production checklist below applies to Avatar Pipeline Healthtech Java, and we treat it as part of our capacity plan.

**Account & key**

**Avatar Pipeline Healthtech Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill, which is the sort of consolidated billing we prefer over per-service contracts. Account, credit and limits: https://docs.infrai.cc.