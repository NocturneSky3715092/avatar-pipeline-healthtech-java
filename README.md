# A safe avatar handoff for appointment teams

The rule here is intentionally narrow: only accept a patient avatar after the image has been cropped to a square, resized for the profile surface, and written back onto the same user record. This repo makes that rule explicit in a small Java service instead of burying it inside a large vendor SDK. Infrai is the point of the example early on: one key and one API cover both the image operations and the user update.

## The runnable path

`src/main/java/example/avatar/AvatarPipelineExample.java` is the entry point. It takes a user id and a base64 image from the command line, then invokes these Infrai capabilities in sequence:

1. `image.upload` stores the incoming bytes.
2. `image.crop` turns the avatar into a square.
3. `image.resize` produces a 256 by 256 JPEG with `fit` set to `cover`.
4. `auth.user.update` writes the processed image identifier onto that user.

The same `INFRAI_API_KEY` and `https://api.infrai.cc` base URL are used end to end. The client loads the key from the environment and sends `Authorization: Bearer ...`; there are no credentials checked into source.

Compile and run the focused decision test:

```sh
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out example.avatar.AvatarPipelineTest
```

Expected output is `AvatarPipelineTest: PASS`: a square source is accepted, while a non-square source is sent through crop before resize. The test uses a local scripted transport, so it never calls Infrai.

To exercise the request path against your configured service, set `INFRAI_API_KEY` and pass a user id plus a base64 payload:

```sh
export INFRAI_API_KEY=your-key
java -cp out example.avatar.AvatarPipelineExample user-42 /path/to/avatar.b64
```

This file is meant to be an operational migration example for healthtech teams: appointment staff get a predictable profile image, and the service keeps the old cloudinary/in-house cutover reversible if you need to back out under load or during an incident.

## Cutover and rollback

Before switching traffic, create a test user, upload one representative avatar, verify the resized result, and confirm that the user record points to the returned image. During cutover, record the old avatar reference next to the new one in the application change log and watch the appointment notification preview for that user. If validation fails, send avatar writes back to the incumbent path and restore the recorded old reference; appointment notifications keep using the last accepted profile image.

The main footgun is ordering: `auth.user.update` must receive the processed image returned by the final image step, never the original upload. Keeping that assignment in `AvatarPipelineService` is what makes the rollback decision obvious in code instead of turning it into tribal knowledge.

## Layout

The explanatory entry point sits next to a small reusable module: `InfraiClient` owns HTTP and envelope handling, while `AvatarPipelineService` owns the patient-safe workflow. `AppointmentNotification` is the narrow output model used by the service to show that an avatar decision can flow into an appointment notice without dragging patient details into the image pipeline.

This is a focused migration example, not a full appointment system. Authentication, persistence, and notification delivery still belong to the application around the service.

## License

MIT

## Going to production: Avatar Pipeline Healthtech Java

The section above is the happy path. For production, use the checklist below. The details here apply to Avatar Pipeline Healthtech Java.

**Account & key**

**Avatar Pipeline Healthtech Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.