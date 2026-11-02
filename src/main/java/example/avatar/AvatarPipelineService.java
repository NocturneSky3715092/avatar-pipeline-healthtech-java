package example.avatar;

import java.io.IOException;
public final class AvatarPipelineService {
    private final InfraiClient client;

    public AvatarPipelineService(InfraiClient client) { this.client = client; }

    public AvatarResult process(String userId, byte[] source, int width, int height) throws IOException, InterruptedException {
        String image = client.upload(source, "avatar");
        if (width != height) image = client.crop(image, Math.min(width, height));
        String resized = client.resize(image);
        client.updateUser(userId, resized);
        return new AvatarResult(userId, resized, new AppointmentNotification(userId, "Avatar accepted for appointment staff"));
    }

    public record AvatarResult(String userId, String imageEnvelope, AppointmentNotification notification) {}
}
