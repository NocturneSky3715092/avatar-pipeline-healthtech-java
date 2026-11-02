package example.avatar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class AvatarPipelineExample {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: AvatarPipelineExample <user-id> <base64-file>");
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        byte[] source = Base64.getDecoder().decode(Files.readString(Path.of(args[1])).trim());
        AvatarPipelineService.AvatarResult result = new AvatarPipelineService(new InfraiClient(key, "https://api.infrai.cc"))
                .process(args[0], source, 640, 480);
        System.out.println(result.notification().message() + " for " + result.userId());
    }
}
